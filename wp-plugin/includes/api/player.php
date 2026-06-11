<?php

/**
 * Registriert die REST-API-Endpunkte für die Spieler-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/players - Gibt eine Liste aller Spieler zurück
    register_rest_route('tourney/v1', '/players', [
        'methods' => 'GET',
        'callback' => 'tourney_get_players',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/players-sync - Synchronisiert die Spieler
    register_rest_route('tourney/v1', '/players-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_players',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Spieler für eine bestimmte Post-ID zurück.
 */
function tourney_get_players(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'players';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ver = $meta . "_ts";
    $version = intval(get_post_meta($post_id, $meta_ver, true));

    $players_json = get_post_meta($post_id, $meta, true);
    $players = $players_json ? json_decode($players_json, true) : [];
    
    return [
        "version" => $version,
        "players" => array_values($players)
    ];
}

/**
 * Synchronisiert die Spieler-Daten mit globalem optimistic locking.
 */
function tourney_sync_players(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'players';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $client_ver = intval($body["version"] ?? 0);
    $new_players = $body["players"] ?? [];
    
    $meta_ver = $meta . "_ts";
    $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));

    // 🔒 Optimistic locking Check (global für das gesamte Meta-Feld)
    if ($stored_ver !== 0 && $client_ver !== $stored_ver) {
        return ApiHelper::error(
            "version_mismatch", 
            "Players have been modified by another user.", 
            "Stored Version: $stored_ver, Sent Version: $client_ver", 
            "tourney_sync_players", 
            HttpStatus::CONFLICT
        );
    }

    // Neue Version
    $next_ver = $stored_ver + 1;

    // 💾 Gesamtes Array speichern
    update_post_meta($post_id, $meta, wp_json_encode(array_values($new_players), JSON_UNESCAPED_UNICODE));
    update_post_meta($post_id, $meta_ver, $next_ver);

    return [
        "version" => $next_ver
    ];
}
