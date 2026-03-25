<?php

/**
 * Registriert die REST-API-Endpunkte für die Spieler-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/players - Gibt eine Liste aller aktiven Spieler zurück
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
 * Gibt alle aktiven Spieler für eine bestimmte Post-ID zurück.
 */
function tourney_get_players(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'players';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ts = $meta . "_ts";

    $players_json = get_post_meta($post_id, $meta, true);
    $timestamp = intval(get_post_meta($post_id, $meta_ts, true));

    $players = $players_json ? json_decode($players_json, true) : [];
    
    // Nur aktive Spieler zurückgeben (wie im Prompt gefordert)
    $active_players = array_values(array_filter($players, function($p) {
        return isset($p['active']) && $p['active'] === true;
    }));

    return [
        "timestamp" => $timestamp,
        "players"   => $active_players
    ];
}

/**
 * Synchronisiert die Spieler-Daten mit Optimistic Locking.
 */
function tourney_sync_players(WP_REST_Request $request)
{
    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'players';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $timestamp = intval($body["timestamp"] ?? 0);
    $meta_ts = $meta . "_ts";
    $stored_ts = intval(get_post_meta($post_id, $meta_ts, true));

    // 🔒 Optimistic locking
    if ($stored_ts !== 0 && $stored_ts !== $timestamp) {
        $errorMessage = "Timestamp mismatch. Received: $timestamp, Stored: $stored_ts";
        return ApiHelper::error("timestamp_mismatch", $errorMessage, "", "", HttpStatus::CONFLICT);
    }

    // 📦 Bestehende Spieler laden
    $players_json = get_post_meta($post_id, $meta, true);
    $players = $players_json ? json_decode($players_json, true) : [];

    // 🗺️ Map nach ID
    $map = [];
    foreach ($players as $p) {
        if (isset($p["id"])) {
            $map[intval($p["id"])] = $p;
        }
    }

    // 🔄 Events anwenden
    if (isset($body["events"]) && is_array($body["events"])) {
        foreach ($body["events"] as $player) {
            if (!isset($player["id"])) continue;
            $id = intval($player["id"]);
            $map[$id] = $player;
        }
    }

    // 🔁 Zurück in Array konvertieren
    $players = array_values($map);

    // 💾 Speichern
    update_post_meta($post_id, $meta, wp_json_encode($players));

    // ⏱️ Neuer Timestamp
    $new_ts = time();
    update_post_meta($post_id, $meta_ts, $new_ts);

    return [
        "timestamp" => $new_ts
    ];
}
