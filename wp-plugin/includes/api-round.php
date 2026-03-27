<?php

/**
 * Registriert die REST-API-Endpunkte für die Runden-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/rounds - Gibt eine Liste aller Runden zurück
    register_rest_route('tourney/v1', '/rounds', [
        'methods' => 'GET',
        'callback' => 'tourney_get_rounds',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/rounds-sync - Synchronisiert die Runden
    register_rest_route('tourney/v1', '/rounds-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_rounds',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Runden für eine bestimmte Post-ID zurück.
 */
function tourney_get_rounds(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $rounds = [];
    
    // Runden 001–128 auslesen
    for ($i = 1; $i <= 128; $i++) {
        $key = sprintf('round%03d', $i);
        $val = get_post_meta($post_id, $key, true);
        if ($val) {
            $round = json_decode($val, true);
            if ($round) {
                $rounds[] = $round;
            }
        }
    }

    return [
        "rounds" => $rounds
    ];
}

/**
 * Synchronisiert die Runden-Daten.
 */
function tourney_sync_rounds(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $events = $body["events"] ?? [];
    $new_ts = time();

    foreach ($events as $round) {
        if (!isset($round["id"])) continue;
        
        $id = intval($round["id"]);
        if ($id < 1 || $id > 128) continue;
        
        $key = sprintf('round%03d', $id);
        $meta_ts = "_{$key}_ts";
        
        // Speichern der Runde als JSON
        update_post_meta($post_id, $key, wp_json_encode($round));
        update_post_meta($post_id, $meta_ts, $new_ts);
    }

    return [
        "success" => true,
        "timestamp" => $new_ts
    ];
}
