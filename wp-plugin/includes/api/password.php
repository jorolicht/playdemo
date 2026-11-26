<?php
/**
 * Eigener Passwort-Reset in der App statt der WordPress-Standardseiten (wp-login.php?action=lostpassword / rp).
 *
 * Ablauf:
 * 1. POST /tourney/v1/auth/forgot {email, returnUrl, lang}: erzeugt einen Reset-Schlüssel (WordPress-Kern,
 *    get_password_reset_key, 24 h gültig, nur einmal verwendbar) und schickt eine eigene Mail mit einem Link auf die
 *    App-Seite, von der der Antrag kam (returnUrl, nur eigene Website): <returnUrl>?tourney_rp=<login>&key=<key>.
 *    Antwortet immer gleich, damit sich nicht erkennen lässt, ob eine Adresse registriert ist.
 *    Schutz: höchstens eine Mail je Konto alle TOURNEY_PW_USER_COOLDOWN Sekunden, TOURNEY_PW_IP_MAX Anträge je IP in
 *    15 Minuten.
 * 2. POST /tourney/v1/auth/check-reset {login, key}: prüft den Link, bevor die App das Formular zeigt.
 * 3. POST /tourney/v1/auth/reset {login, key, password}: setzt das neue Passwort (mind. TOURNEY_PW_MIN_LENGTH Zeichen).
 *
 * Website-Variante (TOURNEY_FWS): Die WordPress-Seiten „Passwort vergessen“, „E-Mail gesendet“ und „Passwort
 * zurücksetzen“ leiten in die App um, und Reset-Mails von WordPress (z. B. von einem Administrator in wp-admin ausgelöst) erhalten den App-Link.
 */

/** Mindestlänge eines Passworts (wie bei der Registrierung). */
const TOURNEY_PW_MIN_LENGTH = 8;
/** Mindestabstand zwischen zwei Reset-Mails an dasselbe Konto (Sekunden). */
const TOURNEY_PW_USER_COOLDOWN = 120;
/** Höchstzahl an Reset-Anträgen je IP-Adresse in 15 Minuten. */
const TOURNEY_PW_IP_MAX = 5;

add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/auth/forgot', [
        'methods'             => 'POST',
        'callback'            => 'tourney_pw_forgot',
        'permission_callback' => '__return_true'
    ]);
    register_rest_route('tourney/v1', '/auth/check-reset', [
        'methods'             => 'POST',
        'callback'            => 'tourney_pw_check',
        'permission_callback' => '__return_true'
    ]);
    register_rest_route('tourney/v1', '/auth/reset', [
        'methods'             => 'POST',
        'callback'            => 'tourney_pw_reset',
        'permission_callback' => '__return_true'
    ]);
});

/**
 * Link auf die App-Seite zum Setzen des neuen Passworts.
 *
 * @param string $base  Seite, von der der Antrag kam (nur eigene Website, sonst Startseite).
 * @param string $login Benutzername.
 * @param string $key   Reset-Schlüssel.
 * @return string URL.
 */
function tourney_pw_link($base, $login, $key) {
    $base = wp_validate_redirect(strtok((string) $base, '?#'), home_url('/'));
    if (!preg_match('#^https?://#i', $base)) {
        $base = home_url('/');   // relative Angaben lässt wp_validate_redirect durch; die Mail braucht eine volle Adresse
    }
    return add_query_arg(['tourney_rp' => rawurlencode($login), 'key' => rawurlencode($key)], $base);
}

/**
 * Text der Reset-Mail.
 *
 * @param WP_User $user Benutzer.
 * @param string  $link Link zum Setzen des neuen Passworts.
 * @param string  $lang "de" oder "en".
 * @return array{0: string, 1: string} Betreff und Text.
 */
function tourney_pw_mail($user, $link, $lang) {
    $site = wp_specialchars_decode(get_bloginfo('name'), ENT_QUOTES);
    if ($lang === 'en') {
        return ["$site – Reset your password",
            "Hello {$user->display_name},\n\n"
            . "someone has requested a new password for your account at $site.\n"
            . "To choose a new password, open this link (valid for 24 hours, usable once):\n\n$link\n\n"
            . "If you did not request this, you can ignore this email – your password stays unchanged.\n"];
    }
    return ["$site – Passwort zurücksetzen",
        "Hallo {$user->display_name},\n\n"
        . "für Ihr Konto bei $site wurde ein neues Passwort angefordert.\n"
        . "Über diesen Link vergeben Sie ein neues Passwort (24 Stunden gültig, nur einmal verwendbar):\n\n$link\n\n"
        . "Falls Sie das nicht angefordert haben, ignorieren Sie diese E-Mail – Ihr Passwort bleibt unverändert.\n"];
}

/**
 * POST auth/forgot: schickt eine Reset-Mail (Antwort immer gleich).
 *
 * @param WP_REST_Request $request Body {"email", "returnUrl", "lang"}.
 * @return array|WP_Error {"status": "success"} bzw. 429 bei zu vielen Anträgen.
 */
function tourney_pw_forgot(WP_REST_Request $request) {
    $params = $request->get_json_params() ?: [];
    $email  = sanitize_email($params['email'] ?? '');
    $lang   = ($params['lang'] ?? '') === 'en' ? 'en' : 'de';
    $ip_key = 'tourney_pw_ip_' . md5($_SERVER['REMOTE_ADDR'] ?? '');
    $count  = (int) get_transient($ip_key);
    if ($count >= TOURNEY_PW_IP_MAX) {
        return new WP_Error('too_many_requests', 'Too many requests', ['status' => 429]);
    }
    set_transient($ip_key, $count + 1, 15 * MINUTE_IN_SECONDS);

    $user = $email ? get_user_by('email', $email) : false;
    if ($user && !get_transient('tourney_pw_user_' . $user->ID)) {
        $key = get_password_reset_key($user);
        if (!is_wp_error($key)) {
            set_transient('tourney_pw_user_' . $user->ID, 1, TOURNEY_PW_USER_COOLDOWN);
            [$subject, $message] = tourney_pw_mail($user, tourney_pw_link($params['returnUrl'] ?? '', $user->user_login, $key), $lang);
            wp_mail($user->user_email, $subject, $message);
        }
    }
    return ['status' => 'success'];
}

/**
 * Prüft Benutzername und Reset-Schlüssel.
 *
 * @param array $params Body {"login", "key"}.
 * @return WP_User|WP_Error Benutzer oder Fehler (invalid_key / expired_key).
 */
function tourney_pw_user($params) {
    $login = sanitize_user(wp_unslash((string) ($params['login'] ?? '')));
    $key   = (string) ($params['key'] ?? '');
    if ($login === '' || $key === '') {
        return new WP_Error('invalid_key', 'Invalid link', ['status' => 403]);
    }
    $user = check_password_reset_key($key, $login);
    if (is_wp_error($user)) {
        return new WP_Error($user->get_error_code() === 'expired_key' ? 'expired_key' : 'invalid_key', 'Invalid link', ['status' => 403]);
    }
    return $user;
}

/**
 * POST auth/check-reset: prüft einen Reset-Link.
 *
 * @param WP_REST_Request $request Body {"login", "key"}.
 * @return array|WP_Error {"status": "valid"} oder Fehler.
 */
function tourney_pw_check(WP_REST_Request $request) {
    $user = tourney_pw_user($request->get_json_params() ?: []);
    return is_wp_error($user) ? $user : ['status' => 'valid'];
}

/**
 * POST auth/reset: setzt das neue Passwort (der Schlüssel wird dabei ungültig).
 *
 * @param WP_REST_Request $request Body {"login", "key", "password"}.
 * @return array|WP_Error {"status": "success"} oder Fehler.
 */
function tourney_pw_reset(WP_REST_Request $request) {
    $params   = $request->get_json_params() ?: [];
    $password = (string) ($params['password'] ?? '');
    $user     = tourney_pw_user($params);
    if (is_wp_error($user)) {
        return $user;
    }
    if (mb_strlen($password) < TOURNEY_PW_MIN_LENGTH) {
        return new WP_Error('weak_password', 'Password too short', ['status' => 400]);
    }
    reset_password($user, $password);
    delete_user_meta($user->ID, 'pd_login_fails_count');
    return ['status' => 'success'];
}

if (defined('TOURNEY_FWS') && TOURNEY_FWS) {
    /**
     * Website: Link „Passwort vergessen?“ von WordPress zeigt auf die App.
     *
     * @return string URL.
     */
    add_filter('lostpassword_url', fn() => add_query_arg('tourney_pw', 'forgot', home_url('/')));

    /**
     * Website: WordPress-Seiten „Passwort vergessen“ und „Passwort zurücksetzen“ leiten in die App um.
     *
     * @return void
     */
    add_action('login_init', function () {
        $action = $_REQUEST['action'] ?? '';
        if (in_array($action, ['lostpassword', 'retrievepassword'], true) && $_SERVER['REQUEST_METHOD'] === 'GET') {
            wp_safe_redirect(add_query_arg('tourney_pw', 'forgot', home_url('/')));
            exit;
        }
        if (($_GET['checkemail'] ?? '') === 'confirm') {
            wp_safe_redirect(add_query_arg('tourney_pw', 'sent', home_url('/')));
            exit;
        }
        if (in_array($action, ['rp', 'resetpass'], true) && isset($_GET['key'], $_GET['login'])) {
            wp_safe_redirect(tourney_pw_link(home_url('/'), wp_unslash($_GET['login']), wp_unslash($_GET['key'])));
            exit;
        }
    });

    /**
     * Website: Reset-Mails von WordPress (z. B. aus wp-admin) mit eigenem Text und App-Link.
     *
     * @param array   $mail       Mail (to, subject, message, headers).
     * @param string  $key        Reset-Schlüssel.
     * @param string  $user_login Benutzername.
     * @param WP_User $user       Benutzer.
     * @return array Mail.
     */
    add_filter('retrieve_password_notification_email', function ($mail, $key, $user_login, $user) {
        $lang = str_starts_with(get_user_locale($user), 'en') ? 'en' : 'de';
        [$mail['subject'], $mail['message']] = tourney_pw_mail($user, tourney_pw_link(home_url('/'), $user_login, $key), $lang);
        return $mail;
    }, 10, 4);
}
