<?php
/**
 * Durchsagen der Turnierleitung (z. B. „Siegerehrung Herren A um 16 Uhr“).
 *
 * Gespeichert als JSON-Liste im Post-Meta `announcements` des Turniers (neueste zuerst, höchstens
 * TOURNEY_ANNOUNCEMENT_MAX Einträge). Angezeigt in „Mein Turnier“ und – vom Client zusätzlich als Info-Eintrag an den
 * Play-Server geschickt – auf dem Matchboard; neue Durchsagen gehen außerdem als Push-Benachrichtigung an die angemeldeten Geräte
 * (includes/api/push.php).
 *
 * - GET    /tourney/v1/announcements?postId=<id>               öffentlich
 * - POST   /tourney/v1/announcements?postId=<id>  {"text": ..} Schreibrecht auf das Turnier
 * - DELETE /tourney/v1/announcements?postId=<id>&id=<id>       Schreibrecht auf das Turnier
 *
 * Antwort jeweils {"announcements": [{"id": "...", "text": "...", "ts": "JJJJ-MM-TT HH:MM"}, ...]}.
 */

/** Höchstzahl gespeicherter Durchsagen je Turnier (ältere fallen weg). */
const TOURNEY_ANNOUNCEMENT_MAX = 20;
/** Höchstlänge einer Durchsage in Zeichen. */
const TOURNEY_ANNOUNCEMENT_MAX_LEN = 200;

add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/announcements', [
        [
            'methods'             => 'GET',
            'callback'            => 'tourney_announcements_get',
            'permission_callback' => '__return_true'
        ],
        [
            'methods'             => 'POST',
            'callback'            => 'tourney_announcements_add',
            'permission_callback' => 'tourney_api_can_edit_request_post'
        ],
        [
            'methods'             => 'DELETE',
            'callback'            => 'tourney_announcements_delete',
            'permission_callback' => 'tourney_api_can_edit_request_post'
        ],
    ]);
});

/**
 * Liest die Durchsagen eines Turniers.
 *
 * @param int $post_id Post-ID des Turniers.
 * @return array Liste der Durchsagen (neueste zuerst).
 */
function tourney_announcements_read($post_id) {
    $list = json_decode(get_post_meta($post_id, 'announcements', true) ?: '[]', true);
    return is_array($list) ? array_values(array_filter($list, 'is_array')) : [];
}

/**
 * Führt eine Änderung der Durchsagen unter einer Sperre aus (gleichzeitige Bearbeiter).
 *
 * @param int      $post_id Post-ID des Turniers.
 * @param callable $change  Erhält die Liste und liefert die neue Liste.
 * @return array|WP_REST_Response {"announcements": [...]} oder Fehler.
 */
function tourney_announcements_change($post_id, callable $change) {
    global $wpdb;
    $lock = 'tourney_announcements_' . $post_id;
    if (!$wpdb->get_var($wpdb->prepare("SELECT GET_LOCK(%s, 10)", $lock))) {
        return ApiHelper::error("busy", "Announcements busy, please retry", "", "tourney_announcements", HttpStatus::CONFLICT);
    }
    try {
        $list = array_slice(array_values($change(tourney_announcements_read($post_id))), 0, TOURNEY_ANNOUNCEMENT_MAX);
        update_post_meta($post_id, 'announcements', wp_slash(wp_json_encode($list, JSON_UNESCAPED_UNICODE)));
        return ["announcements" => $list];
    } finally {
        $wpdb->query($wpdb->prepare("SELECT RELEASE_LOCK(%s)", $lock));
    }
}

/**
 * GET: Durchsagen eines Turniers.
 *
 * @param WP_REST_Request $request Parameter postId.
 * @return array|WP_REST_Response {"announcements": [...]} oder Fehler.
 */
function tourney_announcements_get(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID missing", "", "tourney_announcements_get", HttpStatus::BAD_REQUEST);
    }
    return ["announcements" => tourney_announcements_read($post_id)];
}

/**
 * POST: neue Durchsage (wird als erste eingefügt).
 *
 * @param WP_REST_Request $request Parameter postId; Body {"text": "..."}.
 * @return array|WP_REST_Response {"announcements": [...]} oder Fehler.
 */
function tourney_announcements_add(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    $body    = json_decode($request->get_body(), true);
    $text    = trim(sanitize_text_field(is_array($body) ? strval($body['text'] ?? '') : ''));
    if (!$post_id || $text === '') {
        return ApiHelper::error("missing_param", "Post ID or text missing", "", "tourney_announcements_add", HttpStatus::BAD_REQUEST);
    }
    if (mb_strlen($text) > TOURNEY_ANNOUNCEMENT_MAX_LEN) {
        return ApiHelper::error("too_long", "Announcement too long", strval(TOURNEY_ANNOUNCEMENT_MAX_LEN), "tourney_announcements_add", HttpStatus::BAD_REQUEST);
    }
    $entry  = ["id" => wp_generate_password(8, false, false), "text" => $text, "ts" => wp_date('Y-m-d H:i')];
    $result = tourney_announcements_change($post_id, fn($list) => array_merge([$entry], $list));
    // Push-Benachrichtigung an die angemeldeten Geräte (includes/api/push.php); Fehler brechen das Speichern nicht ab
    if (is_array($result) && function_exists('tourney_push_announcement')) {
        $result['push'] = tourney_push_announcement($post_id, $entry);
    }
    return $result;
}

/**
 * DELETE: Durchsage entfernen.
 *
 * @param WP_REST_Request $request Parameter postId, id.
 * @return array|WP_REST_Response {"announcements": [...]} oder Fehler.
 */
function tourney_announcements_delete(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    $id      = sanitize_text_field(strval($request->get_param('id') ?? ''));
    if (!$post_id || $id === '') {
        return ApiHelper::error("missing_param", "Post ID or id missing", "", "tourney_announcements_delete", HttpStatus::BAD_REQUEST);
    }
    return tourney_announcements_change($post_id, fn($list) => array_filter($list, fn($a) => ($a['id'] ?? '') !== $id));
}
