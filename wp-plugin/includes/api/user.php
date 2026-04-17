<?php

require_once plugin_dir_path(__FILE__) . '../vendor/autoload.php';

use Firebase\JWT\JWT;

// Registrierung des API-Endpunkts
// Permalink Einstellungen auf Beiträge setzen, damit die REST API funktioniert
add_action('rest_api_init', function () {
    register_rest_route('playdemo/v1', '/user/', [
        'methods'  => 'GET',
        'callback' => 'playdemo_api_callback_user',
        'permission_callback' => 'playdemo_api_create_get_permissions_check' 
    ]);
});


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