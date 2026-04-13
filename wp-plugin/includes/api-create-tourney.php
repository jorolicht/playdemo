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
    $slug  = $request->get_param('slug');

    if (empty($title) || empty($slug)) {
        return ApiHelper::error("missing_params", "Titel und Slug sind erforderlich.", "", "tourney_api_create_tourney", HttpStatus::BAD_REQUEST);
    }

    // Bereinige den Slug
    $slug = sanitize_title($slug);

    // Prüfen, ob bereits ein Turnier mit diesem Slug existiert
    $existing_posts = get_posts([
        'name'           => $slug,
        'post_type'      => 'tourney',
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    $post_data = [
        'post_title'   => $title,
        'post_name'    => $slug,
        'post_type'    => 'tourney',
        'post_status'  => 'publish',
    ];

    if (!empty($existing_posts)) {
        // Aktualisieren
        $post_id = $existing_posts[0]->ID;
        $post_data['ID'] = $post_id;
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

    return [
        'success' => true,
        'action'  => $action,
        'pageId'  => $result_id,
        'slug'    => $slug
    ];
}
