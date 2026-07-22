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
});

function pd_api_login_handler($request) {
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

    if ( empty($params['email']) || empty($params['password']) ) {
        return new WP_Error('missing_params', 'E-Mail und Passwort sind erforderlich.', array('status' => 400));
    }

    // Beim Login nur E-Mail-Adresse zulassen
    if (strpos($params['email'], '@') === false) {
        return new WP_Error('login_failed', 'Bitte melde dich mit deiner E-Mail-Adresse an.', array('status' => 403));
    }

    // 1. Benutzer suchen nur per E-Mail
    $user_obj = get_user_by('email', $params['email']);

    if (!$user_obj) {
        return new WP_Error('login_failed', 'E-Mail-Adresse unbekannt.', array('status' => 403));
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
        'user_login'    => $user_obj->user_login, // Wir nehmen den echten User-Login aus dem gefundenen Objekt
        'user_password' => $params['password'],
        'remember'      => true
    );

    $user = wp_signon($creds, false);

    if (is_wp_error($user)) {
        return new WP_Error('login_failed', 'Passwort ungültig.', array('status' => 403));
    }

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
    if ( is_user_logged_in() )  {
        $user_id        = get_current_user_id();
        $current_user   = wp_get_current_user();
        $username       = $current_user->user_login;
        $email          = $current_user->user_email;     
        $organizer    = get_user_meta($user_id, 'organizer' , true );
        $firstname      = $current_user->first_name;
        $lastname       = $current_user->last_name;
        $description    = $current_user->description;
        $avatar_url     = get_avatar_url($user_id);
        $roles          = $current_user->roles;
        $keys           = get_user_meta($user_id, 'pd_webauthn_keys', true) ?: [];
        $has_passkey    = !empty($keys);
    } 

    return [
        'username'      => $username,
        'user_id'       => (int)$user_id,
        'email'         => $email,
        'club'          => $organizer,
        'firstname'     => $firstname,
        'lastname'      => $lastname,
        'description'   => $description,
        'avatar_url'    => $avatar_url,
        'roles'         => array_values($roles), // Ensure numeric array for JSON
        'has_passkey'   => $has_passkey,
        'time'          => current_time('mysql'),
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