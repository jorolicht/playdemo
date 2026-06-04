<?php

require_once plugin_dir_path(__FILE__) . '../../vendor/autoload.php';

use Firebase\JWT\JWT;

// Registrierung des API-Endpunkts
// Permalink Einstellungen auf Beiträge setzen, damit die REST API funktioniert
add_action('rest_api_init', function () {
    register_rest_route('playdemo/v1', '/user/', [
        'methods'  => 'GET',
        'callback' => 'playdemo_api_callback_user',
        'permission_callback' => 'playdemo_api_create_get_permissions_check' 
    ]);

    // Endpunkt: LOGIN
    register_rest_route('playdemo/v1', '/auth/login', array(
        'methods' => 'POST',
        'callback' => 'pd_api_login_handler',
        'permission_callback' => '__return_true',
    ));

    // Endpunkt: LOGOUT
    register_rest_route('playdemo/v1', '/auth/logout', array(
        'methods' => 'POST',
        'callback' => 'pd_api_logout_handler',
        'permission_callback' => '__return_true',
    ));

    // Endpunkt: REGISTER
    register_rest_route('playdemo/v1', '/auth/register', array(
        'methods' => 'POST',
        'callback' => 'pd_api_register_user',
        'permission_callback' => '__return_true',
    ));
    
    // Endpunkt: VERIFY
    register_rest_route('playdemo/v1', '/auth/verify', array(
        'methods' => 'POST',
        'callback' => 'pd_api_verify_handler',
        'permission_callback' => '__return_true',
    ));
});

function pd_api_login_handler($request) {
    $params = $request->get_json_params();

    if ( empty($params['email']) || empty($params['password']) ) {
        return new WP_Error('missing_params', 'Benutzername/Email und Passwort sind erforderlich.', array('status' => 400));
    }

    // 1. Benutzer suchen (entweder per E-Mail oder per Login)
    $user_obj = get_user_by('email', $params['email']);
    if (!$user_obj) {
        $user_obj = get_user_by('login', $params['email']);
    }

    if (!$user_obj) {
        return new WP_Error('login_failed', 'Benutzer oder E-Mail-Adresse unbekannt.', array('status' => 403));
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

    return array('status' => 'success', 'message' => 'Login erfolgreich');
}

function pd_api_logout_handler() {
    wp_logout();
    return array('status' => 'success', 'message' => 'Erfolgreich abgemeldet.');
}

function pd_api_register_user($request) {
    $params = $request->get_json_params();
    
    $user_id = wp_insert_user(array(
        'user_login' => $params['email'],
        'user_email' => $params['email'],
        'display_name' => $params['name'],
        'user_pass'  => $params['password'],
        'role'       => $params['role']
    ));

    if (is_wp_error($user_id)) {
        return new WP_Error('reg_failed', $user_id->get_error_message(), array('status' => 400));
    }

    update_user_meta($user_id, 'pd_status', 'email_pending');
    update_user_meta($user_id, 'organizer', sanitize_text_field($params['organizer']));
    
    $hash = wp_generate_password(32, false);
    update_user_meta($user_id, 'pd_verify_hash', $hash);

    $verify_page_url = home_url("/verifikation/?uid=$user_id&hash=$hash");
    
    $subject = "Willkommen bei Playdemo - Bitte E-Mail bestätigen";
    $message = "Hallo " . $params['name'] . ",\n\nbitte klicke auf den Link, um deinen Account zu aktivieren:\n$verify_page_url";
    
    wp_mail($params['email'], $subject, $message);

    return array('status' => 'success', 'message' => 'Registrierung erfolgreich.');
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
            return array('status' => 'success', 'message' => 'E-Mail bestätigt. Du kannst dich jetzt einloggen.');
        }
    }
    return new WP_Error('verify_failed', 'Ungültiger oder abgelaufener Link.', array('status' => 403));
}


function playdemo_api_create_get_permissions_check( WP_REST_Request $request ) {
    // Prüfen, ob der aktuelle Benutzer die Fähigkeit 'edit_posts' hat
    // Für Administratoren: current_user_can( 'manage_options' )
    // Für Redakteure: current_user_can( 'edit_others_posts' )
    // if ( ! is_user_logged_in() ) {
    //     return ApiHelper::error("rest_forbidden_access", "Sie sind nicht berechtigt, Posts zu erstellen.", "", "", HttpStatus::FORBIDDEN);
    // }
    return true;
}


// Die Callback-Funktion für den Endpunkt
function playdemo_api_callback_user($request) {
    $user_id        = 0;
    $username       = '';
    $email          = '';
    $organizer    = '';
    $firstname      = '';
    $lastname       = '';
    $description    = '';
    $avatar_url     = '';
    $roles          = [];

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