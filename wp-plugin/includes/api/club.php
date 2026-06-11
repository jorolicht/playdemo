<?php

/**
 * Registriert die REST-API-Endpunkte für die Vereins-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/clubs - Gibt eine Liste aller Vereine zurück
    register_rest_route('tourney/v1', '/clubs', [
        'methods' => 'GET',
        'callback' => 'tourney_get_clubs',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/clubs-sync - Synchronisiert die Vereine
    register_rest_route('tourney/v1', '/clubs-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_clubs',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Vereine für eine bestimmte Post-ID zurück.
 */
function tourney_get_clubs(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'clubs';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ver = $meta . "_ts";
    $version = intval(get_post_meta($post_id, $meta_ver, true));

    $clubs_json = get_post_meta($post_id, $meta, true);
    $clubs = $clubs_json ? json_decode($clubs_json, true) : [];

    return [
        "version" => $version,
        "clubs" => array_values($clubs)
    ];
}

/**
 * Synchronisiert die Vereins-Daten mit globalem optimistic locking.
 */
function tourney_sync_clubs(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'clubs';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $client_ver = intval($body["version"] ?? 0);
    $new_clubs = $body["clubs"] ?? [];
    
    $meta_ver = $meta . "_ts";
    $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));

    // 🔒 Optimistic locking Check (global für das gesamte Meta-Feld)
    if ($stored_ver !== 0 && $client_ver !== $stored_ver) {
        return ApiHelper::error(
            "version_mismatch", 
            "Clubs have been modified by another user.", 
            "Stored Version: $stored_ver, Sent Version: $client_ver", 
            "tourney_sync_clubs", 
            HttpStatus::CONFLICT
        );
    }

    // Neue Version
    $next_ver = $stored_ver + 1;

    // 💾 Gesamtes Array speichern
    update_post_meta($post_id, $meta, wp_json_encode(array_values($new_clubs), JSON_UNESCAPED_UNICODE));
    update_post_meta($post_id, $meta_ver, $next_ver);

    return [
        "version" => $next_ver
    ];
}
