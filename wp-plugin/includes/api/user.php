<?php

require_once plugin_dir_path(__FILE__) . '../../vendor/autoload.php';

use Firebase\JWT\JWT;

// Registrierung des API-Endpunkts
// Permalink Einstellungen auf Beiträge setzen, damit die REST API funktioniert
add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/user/', [
        'methods'  => 'GET',
        'callback' => 'tourney_api_callback_user',
        'permission_callback' => 'tourney_api_create_get_permissions_check' 
    ]);

    // Endpunkt: LOGIN
    register_rest_route('tourney/v1', '/auth/login', array(
        'methods' => 'POST',
        'callback' => 'pd_api_login_handler',
        'permission_callback' => '__return_true',
    ));

    // Endpunkt: LOGOUT
    register_rest_route('tourney/v1', '/auth/logout', array(
        'methods' => 'POST',
        'callback' => 'pd_api_logout_handler',
        'permission_callback' => '__return_true',
    ));

    // Endpunkt: REGISTER
    register_rest_route('tourney/v1', '/auth/register', array(
        'methods' => 'POST',
        'callback' => 'pd_api_register_user',
        'permission_callback' => '__return_true',
    ));
    
    // Endpunkt: VERIFY
    register_rest_route('tourney/v1', '/auth/verify', array(
        'methods' => 'POST',
        'callback' => 'pd_api_verify_handler',
        'permission_callback' => '__return_true',
    ));

    // Endpunkt: ADMIN USER SEARCH
    register_rest_route('tourney/v1', '/admin/users', array(
        'methods' => 'GET',
        'callback' => 'tourney_api_admin_search_users',
        'permission_callback' => 'tourney_api_admin_permissions_check',
    ));

    // Endpunkt: ADMIN UPDATE USER PROFILE
    register_rest_route('tourney/v1', '/admin/update_user_profile', array(
        'methods' => 'POST',
        'callback' => 'tourney_api_admin_update_user_profile',
        'permission_callback' => 'tourney_api_admin_permissions_check',
    ));

    // Endpunkt: ADMIN GET UNMATCHED PURCHASES
    register_rest_route('tourney/v1', '/admin/unmatched_purchases', array(
        'methods' => 'GET',
        'callback' => 'tourney_api_admin_get_unmatched_purchases',
        'permission_callback' => 'tourney_api_admin_permissions_check',
    ));

    // Endpunkt: ADMIN ASSIGN UNMATCHED PURCHASE
    register_rest_route('tourney/v1', '/admin/assign_unmatched_purchase', array(
        'methods' => 'POST',
        'callback' => 'tourney_api_admin_assign_unmatched_purchase',
        'permission_callback' => 'tourney_api_admin_permissions_check',
    ));
});

function pd_api_login_handler($request) {
    $params = $request->get_json_params();

    $email = trim($params['email'] ?? '');
    $password = $params['password'] ?? '';
    $ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';

    $ip_fails_key = 'pd_login_fails_ip_' . md5($ip);
    $ip_lockout_key = 'pd_login_lockout_ip_' . md5($ip);

    // 1. IP Throttling & Account Lockout (Nach 5 Fehllogins: 5 Minuten temporäre Sperre)
    if (get_transient($ip_lockout_key)) {
        return new WP_Error(
            'account_locked',
            'Zu viele fehlgeschlagene Anmeldeversuche. Dein Zugriff wurde für 5 Minuten temporär gesperrt.',
            array('status' => 429)
        );
    }

    $ip_fails = (int) get_transient($ip_fails_key);
    $user_fails = 0;
    $user_obj = null;
    if (!empty($email) && strpos($email, '@') !== false) {
        $user_obj = get_user_by('email', $email);
        if ($user_obj) {
            $user_fails = (int) get_user_meta($user_obj->ID, 'pd_login_fails_count', true);
        }
    }

    $max_fails_before_captcha = 3;
    $requires_captcha = ($ip_fails >= $max_fails_before_captcha || $user_fails >= $max_fails_before_captcha);

    // 2. Dynamisches Risk-Based Captcha (Erst nach >= 3 Fehllogins erforderlich)
    $turnstile_key = get_option('cfturnstile_key', getenv('TURNSTILE_SITEKEY') ?: '');
    if (!empty($turnstile_key) && $requires_captcha) {
        $token = $params['turnstileToken'] ?? '';
        $secret_key = get_option('cfturnstile_secret', getenv('TURNSTILE_SECRET') ?: '');
        
        if (empty($token) || empty($secret_key)) {
            return new WP_Error(
                'captcha_required',
                'Aufgrund mehrerer fehlgeschlagener Anmeldeversuche ist eine Sicherheitsüberprüfung (Captcha) erforderlich.',
                array('status' => 403, 'require_captcha' => true)
            );
        }

        $response = wp_remote_post('https://challenges.cloudflare.com/turnstile/v0/siteverify', [
            'body' => [
                'secret'   => $secret_key,
                'response' => $token,
                'remoteip' => $ip
            ]
        ]);
        
        $body = json_decode(wp_remote_retrieve_body($response), true);
        if (empty($body['success']) || $body['success'] !== true) {
            return new WP_Error(
                'bot_detected',
                'Sicherheitsüberprüfung fehlgeschlagen. Bitte bestätige, dass du ein Mensch bist.',
                array('status' => 403, 'require_captcha' => true)
            );
        }
    }

    if ( empty($email) || empty($password) ) {
        return new WP_Error('missing_params', 'E-Mail und Passwort sind erforderlich.', array('status' => 400));
    }

    // Beim Login nur E-Mail-Adresse zulassen
    if (strpos($email, '@') === false) {
        return new WP_Error('login_failed', 'Bitte melde dich mit deiner E-Mail-Adresse an.', array('status' => 403));
    }

    // 1. Benutzer suchen nur per E-Mail
    if (!$user_obj) {
        $user_obj = get_user_by('email', $email);
    }

    if (!$user_obj) {
        $new_ip_fails = $ip_fails + 1;
        set_transient($ip_fails_key, $new_ip_fails, 15 * MINUTE_IN_SECONDS);
        if ($new_ip_fails >= 5) {
            set_transient($ip_lockout_key, true, 5 * MINUTE_IN_SECONDS);
            return new WP_Error('account_locked', 'Zu viele fehlgeschlagene Anmeldeversuche. Zugriff für 5 Minuten gesperrt.', array('status' => 429));
        }
        $req_cap = ($new_ip_fails >= 3);
        return new WP_Error(
            $req_cap ? 'login_failed_captcha' : 'login_failed',
            'E-Mail-Adresse oder Passwort ungültig.',
            array('status' => 403, 'require_captcha' => $req_cap)
        );
    }

    // 2. Status prüfen, bevor wir den eigentlichen Login versuchen
    $status = get_user_meta($user_obj->ID, 'pd_status', true);
    if ($status === 'email_pending') {
        return new WP_Error('pending_email', 'Bitte bestätige zuerst deine E-Mail-Adresse (Link in der Willkommens-Mail).', array('status' => 403));
    }
    if ($status === 'organizer_pending') {
        return new WP_Error('pending_approval', 'Dein Account wurde noch nicht vom Administrator freigeschaltet.', array('status' => 403));
    }

    // 3. Login Versuch
    $creds = array(
        'user_login'    => $user_obj->user_login,
        'user_password' => $password,
        'remember'      => true
    );

    $user = wp_signon($creds, false);

    if (is_wp_error($user)) {
        $new_ip_fails = $ip_fails + 1;
        $new_user_fails = $user_fails + 1;
        set_transient($ip_fails_key, $new_ip_fails, 15 * MINUTE_IN_SECONDS);
        update_user_meta($user_obj->ID, 'pd_login_fails_count', $new_user_fails);

        if ($new_ip_fails >= 5 || $new_user_fails >= 5) {
            set_transient($ip_lockout_key, true, 5 * MINUTE_IN_SECONDS);
            return new WP_Error('account_locked', 'Zu viele fehlgeschlagene Anmeldeversuche. Zugriff für 5 Minuten gesperrt.', array('status' => 429));
        }

        $req_cap = ($new_ip_fails >= 3 || $new_user_fails >= 3);
        return new WP_Error(
            $req_cap ? 'login_failed_captcha' : 'login_failed',
            'E-Mail-Adresse oder Passwort ungültig.',
            array('status' => 403, 'require_captcha' => $req_cap)
        );
    }

    // Erfolgreicher Login: Fehllogin-Zähler zurücksetzen
    delete_transient($ip_fails_key);
    delete_transient($ip_lockout_key);
    delete_user_meta($user->ID, 'pd_login_fails_count');

    // EXPLIZIT: Cookies für die gesamte Domain setzen
    wp_set_current_user($user->ID);
    wp_set_auth_cookie($user->ID, true);

    return array(
        'status' => 'success', 
        'message' => 'Login erfolgreich',
        'nonce' => wp_create_nonce('wp_rest')
    );
}

function pd_api_logout_handler() {
    wp_logout();
    return array(
        'status' => 'success',
        'message' => 'Erfolgreich abgemeldet.',
        'nonce' => wp_create_nonce('wp_rest')
    );
}

function pd_normalize_username($email) {
    $parts = explode('@', $email);
    $local_part = $parts[0];
    $subparts = explode('.', $local_part);
    $capitalized = array_map('ucfirst', $subparts);
    return implode('', $capitalized);
}

function pd_api_register_user($request) {
    $params = $request->get_json_params();
    
    // Cloudflare Turnstile Verifizierung
    $turnstile_key = get_option('cfturnstile_key', getenv('TURNSTILE_SITEKEY') ?: '');
    if (!empty($turnstile_key)) {
        $token = $params['turnstileToken'] ?? '';
        $secret_key = get_option('cfturnstile_secret', getenv('TURNSTILE_SECRET') ?: '');
        
        if (empty($token) || empty($secret_key)) {
            return new WP_Error('turnstile_failed', 'Sicherheitsprüfung fehlgeschlagen (Fehlendes Token).', array('status' => 403));
        }

        $response = wp_remote_post('https://challenges.cloudflare.com/turnstile/v0/siteverify', [
            'body' => [
                'secret'   => $secret_key,
                'response' => $token,
                'remoteip' => $_SERVER['REMOTE_ADDR']
            ]
        ]);
        
        $body = json_decode(wp_remote_retrieve_body($response), true);
        if (empty($body['success']) || $body['success'] !== true) {
            return new WP_Error('bot_detected', 'Bitte bestätigen Sie, dass Sie ein Mensch sind.', array('status' => 403));
        }
    }

    $username = pd_normalize_username($params['email']);
    $base_username = $username;
    $suffix = 1;
    while (username_exists($username)) {
        $username = $base_username . $suffix;
        $suffix++;
    }

    // Passwort generieren falls passwortlose Registrierung gewählt wurde
    $password = !empty($params['password']) ? $params['password'] : wp_generate_password(24, true, true);

    $user_id = wp_insert_user(array(
        'user_login' => $username,
        'user_email' => $params['email'],
        'display_name' => $username,
        'nickname'   => $username,
        'user_pass'  => $password,
        'role'       => $params['role']
    ));

    if (is_wp_error($user_id)) {
        return new WP_Error('reg_failed', $user_id->get_error_message(), array('status' => 400));
    }

    update_user_meta($user_id, 'pd_status', 'email_pending');
    update_user_meta($user_id, 'organizer', sanitize_text_field($params['organizer']));
    
    $hash = wp_generate_password(32, false);
    update_user_meta($user_id, 'pd_verify_hash', $hash);

    $verify_page_url = home_url("/verification/?uid=$user_id&hash=$hash");
    
    $subject = "Willkommen bei Tourney - Bitte E-Mail bestätigen";
    $message = "Hallo " . $username . ",\n\nbitte klicke auf den Link, um deinen Account zu aktivieren:\n$verify_page_url";
    
    wp_mail($params['email'], $subject, $message);

    // WebAuthn Creation Options generieren, um direkt bei der Registrierung den Fingerabdruck abzufragen
    $webauthn = pd_get_webauthn();
    $args = $webauthn->getCreateArgs($user_id, $username, $username, 60, true, 'required', false);
    
    // Challenge in Transient speichern
    set_transient('pd_webauthn_challenge_reg_' . $user_id, bin2hex($webauthn->getChallenge()->getBinaryString()), 10 * MINUTE_IN_SECONDS);

    return array(
        'status' => 'success',
        'message' => 'Registrierung erfolgreich.',
        'user_id' => (string)$user_id,
        'webauthn_args' => json_encode($args)
    );
}

function pd_api_verify_handler($request) {
    $params = $request->get_json_params();
    $uid = $params['uid'];
    $hash = $params['hash'];
    $saved_hash = get_user_meta($uid, 'pd_verify_hash', true);

    if ($hash === $saved_hash && !empty($hash)) {
        delete_user_meta($uid, 'pd_verify_hash');
        $role = get_userdata($uid)->roles[0];

        if ($role === 'turnier_admin') {
            update_user_meta($uid, 'pd_status', 'organizer_pending');
            return array('status' => 'success', 'message' => 'E-Mail bestätigt. Dein Account wird nun vom Administrator geprüft.');
        } else {
            update_user_meta($uid, 'pd_status', 'active');
            // Benutzer nach der erfolgreichen Verifizierung direkt einloggen
            wp_set_current_user($uid);
            wp_set_auth_cookie($uid, true);
            return array(
                'status' => 'success',
                'logged_in' => 'true',
                'message' => 'E-Mail erfolgreich bestätigt. Du bist jetzt eingeloggt.'
            );
        }
    }
    return new WP_Error('verify_failed', 'Ungültiger oder abgelaufener Link.', array('status' => 403));
}


function tourney_api_create_get_permissions_check( WP_REST_Request $request ) {
    // Prüfen, ob der aktuelle Benutzer die Fähigkeit 'edit_posts' hat
    // Für Administratoren: current_user_can( 'manage_options' )
    // Für Redakteure: current_user_can( 'edit_others_posts' )
    // if ( ! is_user_logged_in() ) {
    //     return ApiHelper::error("rest_forbidden_access", "Sie sind nicht berechtigt, Posts zu erstellen.", "", "", HttpStatus::FORBIDDEN);
    // }
    return true;
}


// Die Callback-Funktion für den Endpunkt
function tourney_api_callback_user($request) {
    $user_id        = 0;
    $username       = '';
    $email          = '';
    $organizer    = '';
    $firstname      = '';
    $lastname       = '';
    $description    = '';
    $avatar_url     = '';
    $roles          = [];

    $has_passkey    = false;
    $allowed_tourneys = 0;
    $user_profile     = array('available' => 0, 'executed' => 0, 'history' => array());
    if ( is_user_logged_in() )  {
        $user_id          = get_current_user_id();
        $current_user     = wp_get_current_user();
        $username         = $current_user->user_login;
        $email            = $current_user->user_email;     
        $organizer        = get_user_meta($user_id, 'organizer' , true );
        $user_profile     = tourney_get_user_profile($user_id);
        $allowed_tourneys = $user_profile['available'];
        $firstname        = $current_user->first_name;
        $lastname         = $current_user->last_name;
        $description      = $current_user->description;
        $avatar_url       = get_avatar_url($user_id);
        $roles            = $current_user->roles;
        $keys             = get_user_meta($user_id, 'pd_webauthn_keys', true) ?: [];
        $has_passkey      = !empty($keys);
    } 

    return [
        'username'         => $username,
        'user_id'          => (int)$user_id,
        'email'            => $email,
        'club'             => $organizer,
        'user_profile'     => $user_profile,
        'allowed_tourneys' => (int)$allowed_tourneys,
        'firstname'        => $firstname,
        'lastname'         => $lastname,
        'description'      => $description,
        'avatar_url'       => $avatar_url,
        'roles'            => array_values($roles), // Ensure numeric array for JSON
        'has_passkey'      => $has_passkey,
        'time'             => current_time('mysql'),
    ];
}


add_action('rest_api_init', function () {

    register_rest_route('tourney/v1', '/issue-jwt', [
        'methods' => 'POST',
        'permission_callback' => function (WP_REST_Request $request) {

            $nonce = $request->get_header('X-WP-Nonce');

            if (!wp_verify_nonce($nonce, 'wp_rest')) {
                return ApiHelper::error("invalid_nonce", "Invalid nonce", "", "", HttpStatus::FORBIDDEN);
            }

            return is_user_logged_in();
        },
        'callback' => function () {

            $user = wp_get_current_user();

            $payload = [
                'iss' => get_bloginfo('url'),
                'iat' => time(),
                'exp' => time() + 3600,
                'user_id' => $user->ID
            ];

            $jwt = JWT::encode($payload, JWT_AUTH_SECRET_KEY, 'HS256');

            return ['token' => $jwt];
        }
    ]);
});

add_action('rest_api_init', function () {

    register_rest_route('tourney/v1', '/get-jwt-token', [
        'methods' => 'POST',

        'permission_callback' => function () {
            return current_user_can('read');
        },

        'callback' => function () {

            $user = wp_get_current_user();

            $payload = [
                'iss' => get_bloginfo('url'),
                'iat' => time(),
                'exp' => time() + 3600,
                'user_id' => $user->ID
            ];

            $jwt = JWT::encode($payload, JWT_AUTH_SECRET_KEY, 'HS256');

            return ['token' => $jwt];
        }
    ]);
});

/**
 * Permission check callback for admin endpoints.
 *
 * @param WP_REST_Request $request
 * @return bool|WP_Error
 */
function tourney_api_admin_permissions_check( WP_REST_Request $request ) {
    $current_user = wp_get_current_user();
    if ( ! $current_user || ! $current_user->exists() ) {
        return new WP_Error( 'rest_not_logged_in', 'Sie sind nicht angemeldet.', array( 'status' => 401 ) );
    }
    if ( ! tourney_is_admin_or_editor( $current_user ) ) {
        return new WP_Error( 'rest_forbidden', 'Sie haben keine Administrationsrechte.', array( 'status' => 403 ) );
    }
    return true;
}

/**
 * REST API Callback: Searches users for admin management.
 *
 * @param WP_REST_Request $request
 * @return WP_REST_Response|WP_Error
 */
function tourney_api_admin_search_users( WP_REST_Request $request ) {
    $query_param = sanitize_text_field( $request->get_param( 'query' ) ?? '' );
    $args = array(
        'number'  => 50,
        'orderby' => 'display_name',
        'order'   => 'ASC',
    );

    if ( ! empty( $query_param ) ) {
        $args['search'] = '*' . $query_param . '*';
        $args['search_columns'] = array( 'user_login', 'user_email', 'user_nicename', 'display_name' );
    }

    $user_query = new WP_User_Query( $args );
    $users = $user_query->get_results();
    $result = array();

    foreach ( (array) $users as $u ) {
        if ( ! $u || ! ( $u instanceof WP_User ) ) {
            continue;
        }
        $profile = tourney_get_user_profile( $u->ID );
        $organizer = get_user_meta( $u->ID, 'organizer', true );
        $roles_list = array_values( array_filter( array_map( 'strval', (array) $u->roles ) ) );

        $result[] = array(
            'user_id'          => (int) $u->ID,
            'username'         => (string) ( $u->user_login ? $u->user_login : '' ),
            'email'            => (string) ( $u->user_email ? $u->user_email : '' ),
            'club'             => (string) ( $organizer ? $organizer : '' ),
            'roles'            => $roles_list,
            'user_profile'     => $profile,
            'allowed_tourneys' => (int) $profile['available'],
        );
    }

    return rest_ensure_response( $result );
}

/**
 * REST API Callback: Updates UserProfile available/executed count for a given user ID.
 *
 * @param WP_REST_Request $request
 * @return WP_REST_Response|WP_Error
 */
function tourney_api_admin_update_user_profile( WP_REST_Request $request ) {
    $params  = $request->get_json_params();
    $target_user_id = intval( $params['user_id'] ?? 0 );
    if ( $target_user_id <= 0 ) {
        return new WP_Error( 'invalid_user', 'Ungültige Benutzer-ID.', array( 'status' => 400 ) );
    }

    $user = get_userdata( $target_user_id );
    if ( ! $user ) {
        return new WP_Error( 'user_not_found', 'Benutzer nicht gefunden.', array( 'status' => 404 ) );
    }

    $profile = tourney_get_user_profile( $target_user_id );

    if ( isset( $params['available'] ) ) {
        $profile['available'] = max( 0, intval( $params['available'] ) );
    }
    if ( isset( $params['executed'] ) ) {
        $profile['executed'] = max( 0, intval( $params['executed'] ) );
    }

    tourney_update_user_profile( $target_user_id, $profile );

    return rest_ensure_response( array(
        'success'          => true,
        'user_id'          => $target_user_id,
        'user_profile'     => $profile,
        'allowed_tourneys' => (int) $profile['available'],
        'message'          => 'UserProfile erfolgreich aktualisiert.',
    ) );
}

/**
 * REST API Callback: Returns all unmatched purchases from log file.
 *
 * @param WP_REST_Request $request
 * @return WP_REST_Response
 */
function tourney_api_admin_get_unmatched_purchases( WP_REST_Request $request ) {
    $entries = tourney_get_unmatched_purchases();
    return rest_ensure_response( $entries );
}

/**
 * REST API Callback: Manually assigns an unmatched purchase to a specified target_user_id.
 *
 * @param WP_REST_Request $request
 * @return WP_REST_Response|WP_Error
 */
function tourney_api_admin_assign_unmatched_purchase( WP_REST_Request $request ) {
    $params = $request->get_json_params();
    $target_user_id = intval( $params['target_user_id'] ?? 0 );
    $email = strtolower( trim( $params['email'] ?? '' ) );
    $index = isset( $params['index'] ) ? intval( $params['index'] ) : -1;

    if ( $target_user_id <= 0 ) {
        return new WP_Error( 'invalid_user', 'Ungültige Ziel-Benutzer-ID.', array( 'status' => 400 ) );
    }

    $user = get_userdata( $target_user_id );
    if ( ! $user ) {
        return new WP_Error( 'user_not_found', 'Ziel-Benutzer nicht gefunden.', array( 'status' => 404 ) );
    }

    $entries = tourney_get_unmatched_purchases();
    if ( empty( $entries ) ) {
        return new WP_Error( 'no_entries', 'Keine nicht zugewiesenen Käufe vorhanden.', array( 'status' => 404 ) );
    }

    $remaining_entries = array();
    $assigned_entries = array();

    foreach ( $entries as $i => $entry ) {
        if ( ( $index >= 0 && $i === $index ) || ( $index < 0 && ! empty( $email ) && strtolower( trim( $entry['email'] ) ) === $email ) ) {
            tourney_user_profile_add_purchase( $target_user_id, $entry['count'], $entry['price'], $entry['date'] );
            $assigned_entries[] = $entry;
        } else {
            $remaining_entries[] = $entry;
        }
    }

    if ( empty( $assigned_entries ) ) {
        return new WP_Error( 'entry_not_found', 'Passender nicht zugewiesener Kauf nicht gefunden.', array( 'status' => 404 ) );
    }

    tourney_save_unmatched_purchases( $remaining_entries );
    update_user_meta( $target_user_id, 'payhip_kauf_status', 'aktiv' );
    update_user_meta( $target_user_id, 'payhip_last_purchase_date', current_time( 'mysql' ) );

    $updated_profile = tourney_get_user_profile( $target_user_id );

    return rest_ensure_response( array(
        'success'          => true,
        'target_user_id'   => $target_user_id,
        'user_profile'     => $updated_profile,
        'remaining_count'  => count( $remaining_entries ),
        'message'          => 'Kauf erfolgreich dem Benutzer zugewiesen.',
    ) );
}