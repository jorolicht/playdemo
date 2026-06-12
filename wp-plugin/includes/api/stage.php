<?php

/**
 * Registriert die REST-API-Endpunkte für die Stages-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/stages - Gibt eine Liste aller Stages zurück
    register_rest_route('tourney/v1', '/stages', [
        'methods' => 'GET',
        'callback' => 'tourney_get_stages',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/stages-sync - Synchronisiert die Stages
    register_rest_route('tourney/v1', '/stages-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_stages',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Stages für eine bestimmte Post-ID zurück.
 */
function tourney_get_stages(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $stages = [];
    
    // Stages 001–128 auslesen
    for ($i = 1; $i <= 128; $i++) {
        $key = sprintf('stage%03d', $i);
        $val = get_post_meta($post_id, $key, true);
        if ($val) {
            $stage = json_decode($val, true);
            if ($stage) {
                $stages[] = $stage;
            }
        }
    }

    return [
        "stages" => $stages
    ];
}

/**
 * Synchronisiert die Stages-Daten mit optimistic locking via Version-Counter.
 */
function tourney_sync_stages(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $events = $body["events"] ?? [];

    // 🔒 Optimistic locking Check vorab für alle Events
    foreach ($events as $stage) {
        if (!isset($stage["id"])) continue;
        
        $id = intval($stage["id"]);
        if ($id < 1 || $id > 128) continue;
        
        $key = sprintf('stage%03d', $id);
        $meta_ver = "_{$key}_ts"; // Wir nutzen das Feld für die Version
        
        $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));
        $client_ver = intval($stage["version"] ?? 0);

        // Optimistic Locking:
        // Da der Client die Version bereits lokal inkrementiert hat (z.B. von 1 auf 2),
        // muss sie um genau 1 höher sein als auf dem Server (bzw. 1 für neue Stages).
        
        if ($stored_ver === 0) {
            if ($client_ver !== 1) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Stage $id is new but version is not 1.", 
                    "Sent: $client_ver", 
                    "tourney_sync_stages", 
                    HttpStatus::CONFLICT
                );
            }
        } else {
            if ($client_ver !== ($stored_ver + 1)) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Stage $id has been modified by another user.", 
                    "Stored Version: $stored_ver, Sent Version: $client_ver", 
                    "tourney_sync_stages", 
                    HttpStatus::CONFLICT
                );
            }
        }
    }

    // Wenn alle Checks bestanden, dann speichern
    foreach ($events as $stage) {
        if (!isset($stage["id"])) continue;
        
        $id = intval($stage["id"]);
        if ($id < 1 || $id > 128) continue;
        
        $key = sprintf('stage%03d', $id);
        $meta_ver = "_{$key}_ts";
        
        $client_ver = intval($stage["version"]);
        
        // Speichern der Stage als JSON
        update_post_meta($post_id, $key, wp_json_encode($stage, JSON_UNESCAPED_UNICODE));
        update_post_meta($post_id, $meta_ver, $client_ver);
    }

    return [
        "success" => true
    ];
}
