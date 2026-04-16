<?php

/**
 * Registriert den REST-API-Endpunkt zum Erstellen oder Aktualisieren eines Turnier-CPT.
 */
add_action('rest_api_init', function () {

    // POST /tourney/v1/create-tourney
    // Erwartet 'title' und 'slug' im Body oder als Parameter
    register_rest_route('tourney/v1', '/create-tourney', [
        'methods'  => 'POST',
        'callback' => 'tourney_api_create_tourney',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

});

/**
 * Erstellt ein neues Turnier (CPT 'tourney') mit einem vorgegebenen Slug
 * oder aktualisiert ein bestehendes, falls der Slug bereits existiert.
 *
 * @param WP_REST_Request $request
 * @return array|WP_REST_Response
 */
function tourney_api_create_tourney(WP_REST_Request $request) {
    $title = $request->get_param('title');
    $slug_param  = $request->get_param('slug');

    if (empty($title) || empty($slug_param)) {
        return ApiHelper::error("missing_params", "Titel und Slug sind erforderlich.", "", "tourney_api_create_tourney", HttpStatus::BAD_REQUEST);
    }

    $current_user = wp_get_current_user();
    $username = ($current_user && $current_user->exists()) ? $current_user->user_login : '';
    $organizer = ($current_user && $current_user->exists()) ? get_user_meta($current_user->ID, 'organizer', true) : '';
    
    // Parent slug selection logic: organizer > username > 'admin'
    $parent_base = !empty($organizer) ? $organizer : (!empty($username) ? $username : 'admin');
    $parent_slug = sanitize_title($parent_base);
    $parent_title = !empty($organizer) ? $organizer : (!empty($username) ? $username : 'admin');
    
    $parent_id = 0;

    $existing_parents = get_posts([
        'name'           => $parent_slug,
        'post_type'      => 'tourney',
        'post_parent'    => 0,
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    if (!empty($existing_parents)) {
        $parent_id = $existing_parents[0]->ID;
    } else {
        $parent_id = wp_insert_post([
            'post_title'  => $parent_title,
            'post_name'   => $parent_slug,
            'post_type'   => 'tourney',
            'post_status' => 'publish',
        ]);
    }

    // Bereinige den Turnier-Slug
    $turnier_slug = sanitize_title($slug_param);

    // Prüfen, ob bereits ein Turnier mit diesem Slug unter diesem Parent existiert
    $existing_posts = get_posts([
        'name'           => $turnier_slug,
        'post_type'      => 'tourney',
        'post_parent'    => $parent_id,
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    $post_data = [
        'post_title'   => $title,
        'post_name'    => $turnier_slug,
        'post_parent'  => $parent_id,
        'post_content' => '[playdemo mode="view"]',
        'post_type'    => 'tourney',
        'post_status'  => 'publish',
    ];

    if (!empty($existing_posts)) {
        // Aktualisieren
        $post_data['ID'] = $existing_posts[0]->ID;
        $result_id = wp_update_post($post_data);
        $action = 'updated';
    } else {
        // Neu erstellen
        $result_id = wp_insert_post($post_data);
        $action = 'created';
    }

    if (is_wp_error($result_id)) {
        return ApiHelper::error("db_error", $result_id->get_error_message(), "", "tourney_api_create_tourney", HttpStatus::INTERNAL_SERVER_ERROR);
    }

    // Full hierarchical slug for the response
    $full_slug = "tourney/" . $parent_slug . "/" . $turnier_slug;

    return [
        'success'  => true,
        'action'   => $action,
        'pageId'   => $result_id,
        'parentId' => $parent_id,
        'username' => $username,
        'organizer'=> $organizer,
        'slug'     => $full_slug
    ];
}
