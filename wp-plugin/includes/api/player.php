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
        'permission_callback' => 'tourney_api_can_edit_request_post'
    ]);

    // POST /tourney/v1/public-register - Öffentliche Anmeldung zu einem Wettbewerb (ohne Login)
    register_rest_route('tourney/v1', '/public-register', [
        'methods' => 'POST',
        'callback' => 'tourney_public_register',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Gibt alle Spieler für eine bestimmte Post-ID zurück.
 */
function tourney_get_players(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = 'players';

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
    $meta = 'players';

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
    update_post_meta($post_id, $meta, wp_slash(wp_json_encode(array_values($new_players), JSON_UNESCAPED_UNICODE)));
    update_post_meta($post_id, $meta_ver, $next_ver);

    return [
        "version" => $next_ver
    ];
}

/**
 * Öffentliche Anmeldung eines Spielers zu einem Wettbewerb (ohne Login).
 *
 * Alle Prüfungen erfolgen serverseitig: Anmeldung im ACI freigegeben, Wettbewerb noch offen
 * (siehe tourney_public_register_is_open), Turnstile-Captcha (falls konfiguriert) und Eingaben.
 * Verein und Spieler werden wiederverwendet oder neu angelegt, der Teilnehmer wird in die
 * Teilnehmerliste des Wettbewerbs eingetragen.
 *
 * Body (JSON): compId, firstName, lastName, clubName, clubId (optional, vom Client per
 * Ähnlichkeit gefundener Verein), normalizedName (optional), ttr, email, turnstileToken.
 *
 * @param WP_REST_Request $request Der REST-Request (postId bzw. slug des Turniers).
 * @return array|WP_REST_Response ["playerId", "clubId"] bei Erfolg, sonst Fehlerantwort.
 */
function tourney_public_register(WP_REST_Request $request)
{
    global $wpdb;

    $post_id = ApiHelper::getPostId($request);
    if (!$post_id || get_post_status($post_id) !== 'publish') {
        return ApiHelper::error("not_found", "Tourney not found", "", "tourney_public_register", HttpStatus::NOT_FOUND);
    }

    $body = json_decode($request->get_body(), true);
    if (!is_array($body)) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $first_name = trim(sanitize_text_field($body['firstName'] ?? ''));
    $last_name  = trim(sanitize_text_field($body['lastName'] ?? ''));
    $club_name  = trim(sanitize_text_field($body['clubName'] ?? ''));
    $norm_name  = trim(sanitize_text_field($body['normalizedName'] ?? ''));
    $email      = sanitize_email($body['email'] ?? '');
    $ttr        = max(0, min(4000, intval($body['ttr'] ?? 0)));
    $comp_id    = intval($body['compId'] ?? 0);

    foreach (array($first_name, $last_name, $club_name) as $value) {
        if ($value === '' || mb_strlen($value) > 100) {
            return ApiHelper::error("invalid_input", "Name or club missing or too long", "", "tourney_public_register", HttpStatus::BAD_REQUEST);
        }
    }
    if (!is_email($email) || $comp_id < 1 || $comp_id > 64) {
        return ApiHelper::error("invalid_input", "Invalid email or competition", "", "tourney_public_register", HttpStatus::BAD_REQUEST);
    }

    $aci = json_decode(get_post_meta($post_id, 'aci', true) ?: '{}', true);
    if (empty($aci['allowRegistration'])) {
        return ApiHelper::error("registration_closed", "Registration is not enabled", "", "tourney_public_register", HttpStatus::FORBIDDEN);
    }

    $error = tourney_public_register_verify_captcha($body['turnstileToken'] ?? '');
    if ($error) {
        return $error;
    }

    // Lesen-Ändern-Schreiben gegen gleichzeitige Anmeldungen zum selben Turnier absichern
    $lock = 'tourney_register_' . $post_id;
    if (!$wpdb->get_var($wpdb->prepare("SELECT GET_LOCK(%s, 10)", $lock))) {
        return ApiHelper::error("busy", "Registration busy, please retry", "", "tourney_public_register", HttpStatus::CONFLICT);
    }

    try {
        $comp_key = sprintf('competition%02d', $comp_id);
        $comp = json_decode(get_post_meta($post_id, $comp_key, true) ?: '', true);
        if (!is_array($comp) || !empty($comp['deleted'])) {
            return ApiHelper::error("not_found", "Competition not found", "", "tourney_public_register", HttpStatus::NOT_FOUND);
        }
        if (!tourney_public_register_is_open($post_id, $comp)) {
            return ApiHelper::error("registration_closed", "Registration for this competition is closed", "", "tourney_public_register", HttpStatus::FORBIDDEN);
        }

        // Verein: vom Client gefundenen bzw. namensgleichen Verein verwenden, sonst neu anlegen
        $clubs = json_decode(get_post_meta($post_id, 'clubs', true) ?: '[]', true) ?: [];
        $club_idx = -1;
        $req_club_id = intval($body['clubId'] ?? 0);
        foreach ($clubs as $i => $c) {
            if (($req_club_id > 0 && intval($c['id'] ?? 0) === $req_club_id) ||
                ($norm_name !== '' && ($c['normalizedName'] ?? '') === $norm_name)) {
                $club_idx = $i;
                break;
            }
        }
        $clubs_changed = false;
        if ($club_idx < 0) {
            $clubs[] = [
                "id"             => tourney_public_register_next_id($clubs),
                "name"           => $club_name,
                "normalizedName" => $norm_name !== '' ? $norm_name : mb_strtolower($club_name),
            ];
            $club_idx = count($clubs) - 1;
            $clubs_changed = true;
        } elseif (isset($clubs[$club_idx]['active']) && !$clubs[$club_idx]['active']) {
            $clubs[$club_idx]['active'] = true;
            $clubs_changed = true;
        }
        $club = $clubs[$club_idx];
        $club_id = intval($club['id']);

        // Spieler: vorhandenen Spieler (Name + Verein) verwenden, sonst neu anlegen
        $players = json_decode(get_post_meta($post_id, 'players', true) ?: '[]', true) ?: [];
        $player_id = 0;
        foreach ($players as $p) {
            if (($p['firstName'] ?? '') === $first_name && ($p['lastName'] ?? '') === $last_name && intval($p['clubId'] ?? 0) === $club_id) {
                $player_id = intval($p['id']);
                break;
            }
        }
        $players_changed = false;
        if ($player_id === 0) {
            $player_id = tourney_public_register_next_id($players);
            $players[] = [
                "id"        => $player_id,
                "firstName" => $first_name,
                "lastName"  => $last_name,
                "clubId"    => $club_id,
                "email"     => $email,
            ];
            $players_changed = true;
        }

        // Teilnehmer in den Wettbewerb eintragen (falls noch nicht vorhanden)
        $sno = "[" . $player_id . "]";
        $pants = $comp['pants1Stage'] ?? [];
        $registered = false;
        foreach ($pants as $pant) {
            if (($pant['id'] ?? '') === $sno) {
                $registered = true;
                break;
            }
        }

        if ($clubs_changed) {
            update_post_meta($post_id, 'clubs', wp_slash(wp_json_encode(array_values($clubs), JSON_UNESCAPED_UNICODE)));
            update_post_meta($post_id, 'clubs_ts', intval(get_post_meta($post_id, 'clubs_ts', true)) + 1);
        }
        if ($players_changed) {
            update_post_meta($post_id, 'players', wp_slash(wp_json_encode(array_values($players), JSON_UNESCAPED_UNICODE)));
            update_post_meta($post_id, 'players_ts', intval(get_post_meta($post_id, 'players_ts', true)) + 1);
        }
        if (!$registered) {
            $pants[] = [
                "id"     => $sno,
                "name"   => $last_name . ", " . $first_name,
                "club"   => $club['name'] ?? $club_name,
                "rating" => $ttr,
                "status" => "UNKN",
                "active" => true,
                "clubId" => $club_id,
            ];
            $comp['pants1Stage'] = $pants;
            // +2: Ein Client mit dem alten Stand sendet beim nächsten Speichern alte Version + 1;
            // das muss kleiner als die gespeicherte Version sein, damit er neu lädt statt die
            // Anmeldung zu überschreiben (siehe tourney_sync_competitions).
            $comp['version'] = intval(get_post_meta($post_id, "_{$comp_key}_ts", true)) + 2;
            update_post_meta($post_id, $comp_key, wp_slash(wp_json_encode($comp, JSON_UNESCAPED_UNICODE)));
            update_post_meta($post_id, "_{$comp_key}_ts", $comp['version']);
        }

        return [
            "playerId" => $player_id,
            "clubId"   => $club_id,
        ];
    } finally {
        $wpdb->query($wpdb->prepare("SELECT RELEASE_LOCK(%s)", $lock));
    }
}

/**
 * Prüft, ob die Anmeldung zu einem Wettbewerb noch offen ist.
 *
 * Entspricht der Logik des Clients (Tourney.isRegLocked, TourneyWelcome.isCompStartInFuture):
 * Wettbewerb nicht abgeschlossen, Start-Stage (falls vorhanden) noch in Konfiguration und
 * Startdatum (falls angegeben) nicht in der Vergangenheit.
 *
 * @param int   $post_id Die WordPress Post-ID des Turniers.
 * @param array $comp    Der Wettbewerb (dekodiertes JSON).
 * @return bool True, wenn Anmeldungen angenommen werden.
 */
function tourney_public_register_is_open($post_id, array $comp)
{
    if (($comp['status'] ?? '') === 'FIN') {
        return false;
    }

    $start_stage = intval($comp['startStage'] ?? 0);
    if ($start_stage > 0) {
        $stage = json_decode(get_post_meta($post_id, sprintf('stage%03d', $start_stage), true) ?: '', true);
        if (is_array($stage) && ($stage['status'] ?? '') !== 'CFG') {
            return false;
        }
    }

    $digits = preg_replace('/[^0-9]/', '', $comp['startDate'] ?? '');
    if (strlen($digits) >= 8) {
        $time = strlen($digits) >= 12 ? substr($digits, 8, 4) . '00' : '235959';
        $start = DateTime::createFromFormat('YmdHis', substr($digits, 0, 8) . $time, wp_timezone());
        if ($start && $start->getTimestamp() < time()) {
            return false;
        }
    }
    return true;
}

/**
 * Prüft das Cloudflare-Turnstile-Token, sofern Turnstile konfiguriert ist.
 *
 * @param string $token Das Turnstile-Token aus dem Formular.
 * @return WP_REST_Response|null Fehlerantwort oder null, wenn die Prüfung bestanden ist.
 */
function tourney_public_register_verify_captcha($token)
{
    $site_key = get_option('cfturnstile_key', getenv('TURNSTILE_SITEKEY') ?: '');
    if (empty($site_key)) {
        return null;
    }

    $secret_key = get_option('cfturnstile_secret', getenv('TURNSTILE_SECRET') ?: '');
    if (empty($token) || empty($secret_key)) {
        return ApiHelper::error("captcha_failed", "Captcha token missing", "", "tourney_public_register", HttpStatus::FORBIDDEN);
    }

    $response = wp_remote_post('https://challenges.cloudflare.com/turnstile/v0/siteverify', [
        'body' => [
            'secret'   => $secret_key,
            'response' => $token,
            'remoteip' => $_SERVER['REMOTE_ADDR'] ?? ''
        ]
    ]);
    $result = json_decode(wp_remote_retrieve_body($response), true);
    if (empty($result['success'])) {
        return ApiHelper::error("captcha_failed", "Captcha verification failed", "", "tourney_public_register", HttpStatus::FORBIDDEN);
    }
    return null;
}

/**
 * Ermittelt die nächste freie ID einer Liste (höchste vorhandene ID + 1).
 *
 * @param array $items Liste von Einträgen mit Feld "id".
 * @return int Die nächste freie ID.
 */
function tourney_public_register_next_id(array $items)
{
    $max = 0;
    foreach ($items as $item) {
        $max = max($max, intval($item['id'] ?? 0));
    }
    return $max + 1;
}
