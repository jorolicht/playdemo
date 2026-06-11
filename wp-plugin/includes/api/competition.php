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
    $post_id = ApiHelper::getPostId($request);

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
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
 * Synchronisiert die Wettbewerbs-Daten mit optimistic locking via Version-Counter.
 */
function tourney_sync_competitions(WP_REST_Request $request)
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
    foreach ($events as $comp) {
        if (!isset($comp["id"])) continue;
        
        $id = intval($comp["id"]);
        if ($id < 1 || $id > 64) continue;
        
        $key = sprintf('competition%02d', $id);
        $meta_ver = "_{$key}_ts"; // Wir nutzen das Feld für die Version
        
        $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));
        $client_ver = intval($comp["version"] ?? 0);

        // Optimistic Locking:
        // Client inkrementiert die Version, bevor er sendet.
        if ($stored_ver === 0) {
            if ($client_ver !== 1) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Competition $id is new but version is not 1.", 
                    "Sent: $client_ver", 
                    "tourney_sync_competitions", 
                    HttpStatus::CONFLICT
                );
            }
        } else {
            if ($client_ver !== ($stored_ver + 1)) {
                return ApiHelper::error(
                    "version_mismatch", 
                    "Competition $id has been modified by another user.", 
                    "Stored Version: $stored_ver, Sent Version: $client_ver", 
                    "tourney_sync_competitions", 
                    HttpStatus::CONFLICT
                );
            }
        }
    }

    // Wenn alle Checks bestanden, dann speichern
    foreach ($events as $comp) {
        if (!isset($comp["id"])) continue;
        
        $id = intval($comp["id"]);
        if ($id < 1 || $id > 64) continue;
        
        $key = sprintf('competition%02d', $id);
        $meta_ver = "_{$key}_ts";
        
        $client_ver = intval($comp["version"]);
        
        // Speichern des Wettbewerbs als JSON
        update_post_meta($post_id, $key, wp_json_encode($comp, JSON_UNESCAPED_UNICODE));
        update_post_meta($post_id, $meta_ver, $client_ver);
    }

    return [
        "success" => true
    ];
}
