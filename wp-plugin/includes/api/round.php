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
 * Synchronisiert die Runden-Daten mit optimistic locking via Version-Counter.
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

    // 🔒 Optimistic locking Check vorab für alle Events
    foreach ($events as $round) {
        if (!isset($round["id"])) continue;
        
        $id = intval($round["id"]);
        if ($id < 1 || $id > 128) continue;
        
        $key = sprintf('round%03d', $id);
        $meta_ver = "_{$key}_ts"; // Wir nutzen das Feld für die Version
        
        $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));
        $client_ver = intval($round["version"] ?? 0);

        // Optimistic Locking:
        // Da der Client die Version bereits lokal inkrementiert hat (z.B. von 1 auf 2),
        // muss sie um genau 1 höher sein als auf dem Server (bzw. 1 für neue Runden).
        
        if ($stored_ver === 0) {
            if ($client_ver !== 1) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Round $id is new but version is not 1.", 
                    "Sent: $client_ver", 
                    "tourney_sync_rounds", 
                    HttpStatus::CONFLICT
                );
            }
        } else {
            if ($client_ver !== ($stored_ver + 1)) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Round $id has been modified by another user.", 
                    "Stored Version: $stored_ver, Sent Version: $client_ver", 
                    "tourney_sync_rounds", 
                    HttpStatus::CONFLICT
                );
            }
        }
    }

    // Wenn alle Checks bestanden, dann speichern
    foreach ($events as $round) {
        if (!isset($round["id"])) continue;
        
        $id = intval($round["id"]);
        if ($id < 1 || $id > 128) continue;
        
        $key = sprintf('round%03d', $id);
        $meta_ver = "_{$key}_ts";
        
        $client_ver = intval($round["version"]);
        
        // Speichern der Runde als JSON
        update_post_meta($post_id, $key, wp_json_encode($round, JSON_UNESCAPED_UNICODE));
        update_post_meta($post_id, $meta_ver, $client_ver);
    }

    return [
        "success" => true
    ];
}
