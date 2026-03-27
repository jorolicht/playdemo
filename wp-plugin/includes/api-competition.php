<?php

/**
 * Registriert die REST-API-Endpunkte für die Wettbewerbs-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/competitions - Gibt eine Liste aller Wettbewerbe zurück
    register_rest_route('tourney/v1', '/competitions', [
        'methods' => 'GET',
        'callback' => 'tourney_get_competitions',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/competitions-sync - Synchronisiert die Wettbewerbe
    register_rest_route('tourney/v1', '/competitions-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_competitions',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Wettbewerbe für eine bestimmte Post-ID zurück.
 */
function tourney_get_competitions(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $competitions = [];
    
    // Wettbewerbe 01–64 auslesen
    for ($i = 1; $i <= 64; $i++) {
        $key = sprintf('competition%02d', $i);
        $val = get_post_meta($post_id, $key, true);
        if ($val) {
            $comp = json_decode($val, true);
            if ($comp) {
                $competitions[] = $comp;
            }
        }
    }

    return [
        "competitions" => $competitions
    ];
}

/**
 * Synchronisiert die Wettbewerbs-Daten.
 */
function tourney_sync_competitions(WP_REST_Request $request)
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

    foreach ($events as $comp) {
        if (!isset($comp["id"])) continue;
        
        $id = intval($comp["id"]);
        if ($id < 1 || $id > 64) continue;
        
        $key = sprintf('competition%02d', $id);
        $meta_ts = "_{$key}_ts";
        
        // Speichern des Wettbewerbs als JSON
        update_post_meta($post_id, $key, wp_json_encode($comp));
        update_post_meta($post_id, $meta_ts, $new_ts);
    }

    return [
        "success" => true,
        "timestamp" => $new_ts
    ];
}
