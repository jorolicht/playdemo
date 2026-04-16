<?php

/**
 * Registriert die REST-API-Endpunkte für die Turnier-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/tourney - Gibt die Turnierdaten zurück
    register_rest_route('tourney/v1', '/tourney', [
        'methods' => 'GET',
        'callback' => 'tourney_get_tourney',
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
function tourney_get_tourney(WP_REST_Request $request)
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
