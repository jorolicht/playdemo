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
        'permission_callback' => 'tourney_api_can_edit_request_post'
    ]);

    // GET /tourney/v1/rounds (Alias for /stages)
    register_rest_route('tourney/v1', '/rounds', [
        'methods' => 'GET',
        'callback' => 'tourney_get_stages',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/rounds-sync (Alias for /stages-sync)
    register_rest_route('tourney/v1', '/rounds-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_stages',
        'permission_callback' => 'tourney_api_can_edit_request_post'
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
 *
 * Body: {"events": [Stage, ...], "bases": {"<id>": <Version>, ...}, "log": [{"id": <StageId>, "text": "..."}, ...]}
 * - bases (optional): Version, auf der die Änderung beruht; weicht die gespeicherte Version ab, hat ein anderer
 *   Bearbeiter die Stage inzwischen gespeichert → 409 version_mismatch, der Client führt die Änderungen zusammen.
 * - log (optional): Texte für das Änderungsprotokoll.
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
    // Multiuser: Version, auf der die Änderung des Clients beruht (Stage-ID => Version beim Laden/letzten Speichern)
    $bases = is_array($body["bases"] ?? null) ? $body["bases"] : [];

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
        // Mit Basisversion: die Stage darf seit dem Laden durch den Client nicht verändert worden sein.
        // Ohne Basisversion (ältere Clients, Import): Client-Version >= gespeicherte Version.
        $conflict = array_key_exists((string)$id, $bases)
            ? $stored_ver !== intval($bases[(string)$id])
            : $client_ver < $stored_ver;
        if ($conflict) {
            return ApiHelper::error(
                "version_mismatch", 
                "Stage $id has been modified by another user.", 
                "Stored Version: $stored_ver, Sent Version: $client_ver", 
                "tourney_sync_stages", 
                HttpStatus::CONFLICT
            );
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
        update_post_meta($post_id, $key, wp_slash(wp_json_encode($stage, JSON_UNESCAPED_UNICODE)));
        update_post_meta($post_id, $meta_ver, $client_ver);
    }

    // Änderungsprotokoll (Ergebnisse), siehe includes/api/changelog.php
    tourney_changelog_add($post_id, 'stage', $body["log"] ?? []);

    return [
        "success" => true
    ];
}
