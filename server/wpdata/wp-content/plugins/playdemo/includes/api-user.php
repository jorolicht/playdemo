<?php

// Registrierung des API-Endpunkts
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
    //     return new WP_Error( 'rest_forbidden_access', __( 'Sie sind nicht berechtigt, Posts zu erstellen.', 'my-custom-api' ), array( 'status' => 403 ) );
    // }
    return true;
}


// Die Callback-Funktion für den Endpunkt
function playdemo_api_callback_user($request) {
    $user_id        = 'null';
    $username       = '';
    $email          = '';
    $club           = '';
    $user_api_pw    = '';  
    $api_password   = ''; 

    if ( is_user_logged_in() )  {
        $user_id        = get_current_user_id();
        $current_user   = wp_get_current_user();
        $username       = $current_user->user_login;
        $email          = $current_user->user_email;     
        $club           = get_user_meta($user_id, 'club_name' , true );
        $user_api_pw    = get_option('playdemo_user_api_pw');  
        $api_password   = get_option('playdemo_api_password');        
    } 

    return [
        'username'      => $username,
        'user_id'       => $user_id,
        'email'         => $email,
        'club'          => $club,
        'user_api_pw'   => $user_api_pw,
        'api_passsword' => $api_password,
        'time'          => current_time('mysql'),
    ];
}