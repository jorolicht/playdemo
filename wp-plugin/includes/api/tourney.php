<?php

/**
 * Registriert die REST-API-Endpunkte für die Turnier-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/read - Gibt die Turnierdaten zurück
    register_rest_route('tourney/v1', '/read', [
        'methods' => 'GET',
        'callback' => 'tourney_get_read',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/tourney-sync - Synchronisiert die Turnierdaten
    register_rest_route('tourney/v1', '/tourney-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_tourney',
        'permission_callback' => '__return_true'
    ]);

    // GET /tourney/v1/organizers - Gibt alle Parent-Posts (Organisatoren) zurück
    register_rest_route('tourney/v1', '/organizers', [
        'methods' => 'GET',
        'callback' => 'tourney_get_organizers',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/create
    register_rest_route('tourney/v1', '/create', [
        'methods'  => 'POST',
        'callback' => 'tourney_api_create',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

});

/**
 * Gibt eine Liste aller Organisatoren (Parent-Posts) zurück.
 */
function tourney_get_organizers(WP_REST_Request $request) {
    $parents = get_posts([
        'post_type'      => 'tourney',
        'post_parent'    => 0,
        'post_status'    => 'publish',
        'posts_per_page' => -1,
        'orderby'        => 'title',
        'order'          => 'ASC'
    ]);

    $result = [];
    foreach ($parents as $parent) {
        // Zähle Kinder
        $children = get_posts([
            'post_type'   => 'tourney',
            'post_parent' => $parent->ID,
            'post_status' => 'publish',
            'posts_per_page' => -1,
            'fields'      => 'ids'
        ]);

        $result[] = [
            'id'    => $parent->ID,
            'title' => $parent->post_title,
            'slug'  => $parent->post_name,
            'count' => count($children)
        ];
    }

    return $result;
}

/**
 * Gibt die Turnierdaten für eine bestimmte Post-ID zurück.
 */
function tourney_get_read(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ver = $meta . "_ts";
    $version = intval(get_post_meta($post_id, $meta_ver, true));

    $tourney_json = get_post_meta($post_id, $meta, true);
    $tourney = $tourney_json ? json_decode($tourney_json, true) : null;

    return [
        "version" => $version,
        "tourney" => $tourney
    ];
}

/**
 * Synchronisiert die Turnierdaten mit globalem optimistic locking.
 */
function tourney_sync_tourney(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $client_ver = intval($body["version"] ?? 0);
    $new_tourney = $body["tourney"] ?? null;
    
    $meta_ver = $meta . "_ts";
    $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));

    // 🔒 Optimistic locking Check
    if ($stored_ver !== 0 && $client_ver !== $stored_ver) {
        return ApiHelper::error(
            "version_mismatch", 
            "Tournament data has been modified by another user.", 
            "Stored Version: $stored_ver, Sent Version: $client_ver", 
            "tourney_sync_tourney", 
            HttpStatus::CONFLICT
        );
    }

    // Neue Version
    $next_ver = $stored_ver + 1;

    // 💾 Speichern
    if ($new_tourney) {
        update_post_meta($post_id, $meta, wp_json_encode($new_tourney, JSON_UNESCAPED_UNICODE));
    } else {
        delete_post_meta($post_id, $meta);
    }
    update_post_meta($post_id, $meta_ver, $next_ver);

    return [
        "version" => $next_ver
    ];
}

/**
 * Erstellt ein neues Turnier (CPT 'tourney') mit einem vorgegebenen Slug
 * oder aktualisiert ein bestehendes, falls der Slug bereits existiert.
 *
 * @param WP_REST_Request $request
 * @return array|WP_REST_Response
 */
function tourney_api_create(WP_REST_Request $request) {
    $body = json_decode($request->get_body(), true);

    if (empty($body)) {
        return ApiHelper::error("missing_payload", "JSON-Payload ist erforderlich.", "", "tourney_api_create", HttpStatus::BAD_REQUEST);
    }

    $tourney_name = $body['name'] ?? '';
    $start_date   = $body['startDate'] ?? '';
    $ident        = $body['ident'] ?? '';

    if (empty($tourney_name) || empty($start_date)) {
        return ApiHelper::error("missing_params", "Name und Startdatum sind erforderlich.", "", "tourney_api_create", HttpStatus::BAD_REQUEST);
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

    // Generiere den Turnier-Slug aus Datum und Name: <jjjjMMdd>-<name>
    $slug_base = $start_date . '-' . $tourney_name;
    $turnier_slug = sanitize_title($slug_base);

    // Prüfen, ob bereits ein Turnier mit diesem Slug unter diesem Parent existiert
    $existing_posts = get_posts([
        'name'           => $turnier_slug,
        'post_type'      => 'tourney',
        'post_parent'    => $parent_id,
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    $post_data = [
        'post_title'   => $tourney_name,
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

    // Setze ident Metafield
    if (!empty($ident)) {
        update_post_meta($result_id, 'ident', $ident);
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

