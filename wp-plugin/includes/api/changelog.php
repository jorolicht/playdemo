<?php
/**
 * Änderungsprotokoll eines Turniers (Multiuser-Betrieb).
 *
 * Die Clients schicken beim Speichern von Stages (stages-sync) Texte zu den geänderten Ergebnissen mit, z. B.
 * "Herren A – Gruppen: Spiel 12 Müller – Meier 3:1". Der Server speichert sie zusammen mit Zeit und angemeldetem
 * Benutzer in der Tabelle {prefix}tourney_changelog. Turnierleiter (Schreibrecht auf den Post) können das Protokoll
 * über GET tourney/v1/changelog abrufen (Anzeige in TourneyAdmin, Reiter "Änderungen").
 *
 * Nur auf der Website turnier-service.info (TOURNEY_FWS), nicht im Embed-Plugin.
 */

/** Maximale Länge eines Protokolltexts. */
const TOURNEY_CHANGELOG_TEXT_MAX = 255;

/** Maximale Anzahl Protokolleinträge je Speicheraufruf. */
const TOURNEY_CHANGELOG_MAX_PER_REQUEST = 50;

/**
 * Liefert den Namen der Protokoll-Tabelle.
 *
 * @return string Tabellenname mit WordPress-Präfix.
 */
function tourney_changelog_table() {
    global $wpdb;
    return $wpdb->prefix . 'tourney_changelog';
}

/**
 * Prüft, ob das Änderungsprotokoll auf dieser Installation aktiv ist.
 *
 * @return bool true auf der Website turnier-service.info (TOURNEY_FWS).
 */
function tourney_changelog_enabled() {
    return defined('TOURNEY_FWS') && TOURNEY_FWS;
}

/**
 * Legt die Protokoll-Tabelle an, falls sie noch nicht existiert (einmalig, gemerkt in Option tourney_changelog_db).
 *
 * @return void
 */
function tourney_changelog_install() {
    if (!tourney_changelog_enabled() || get_option('tourney_changelog_db') === '1') {
        return;
    }
    global $wpdb;
    require_once ABSPATH . 'wp-admin/includes/upgrade.php';
    $table = tourney_changelog_table();
    dbDelta("CREATE TABLE $table (
        id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
        post_id bigint(20) unsigned NOT NULL,
        ts datetime NOT NULL,
        user_id bigint(20) unsigned NOT NULL DEFAULT 0,
        user_name varchar(100) NOT NULL DEFAULT '',
        obj varchar(16) NOT NULL DEFAULT '',
        obj_id int(10) unsigned NOT NULL DEFAULT 0,
        text varchar(255) NOT NULL DEFAULT '',
        PRIMARY KEY  (id),
        KEY post_id (post_id,id)
    ) {$wpdb->get_charset_collate()};");
    update_option('tourney_changelog_db', '1');
}
add_action('plugins_loaded', 'tourney_changelog_install');

/**
 * Speichert Protokolleinträge des angemeldeten Benutzers zu einem Turnier.
 *
 * @param int    $post_id Post-ID des Turniers.
 * @param string $obj     Objektart, z. B. "stage".
 * @param array  $entries Liste von ["id" => Objekt-ID, "text" => Protokolltext]; ungültige Einträge werden übersprungen.
 * @return void
 */
function tourney_changelog_add($post_id, $obj, $entries) {
    if (!tourney_changelog_enabled() || !is_array($entries) || empty($entries)) {
        return;
    }
    global $wpdb;
    $user = wp_get_current_user();
    $name = $user && $user->exists() ? $user->display_name : '';
    $now  = current_time('mysql');
    foreach (array_slice($entries, 0, TOURNEY_CHANGELOG_MAX_PER_REQUEST) as $entry) {
        $text = is_array($entry) ? trim(sanitize_text_field($entry['text'] ?? '')) : '';
        if ($text === '') {
            continue;
        }
        $wpdb->insert(tourney_changelog_table(), [
            'post_id'   => intval($post_id),
            'ts'        => $now,
            'user_id'   => $user ? intval($user->ID) : 0,
            'user_name' => mb_substr($name, 0, 100),
            'obj'       => substr($obj, 0, 16),
            'obj_id'    => max(0, intval($entry['id'] ?? 0)),
            'text'      => mb_substr($text, 0, TOURNEY_CHANGELOG_TEXT_MAX),
        ], ['%d', '%s', '%d', '%s', '%s', '%d', '%s']);
    }
}

/**
 * Löscht das Protokoll eines Turniers, wenn der Post endgültig gelöscht wird.
 *
 * @param int $post_id Post-ID.
 * @return void
 */
function tourney_changelog_delete_post($post_id) {
    if (get_option('tourney_changelog_db') !== '1') {
        return;
    }
    global $wpdb;
    $wpdb->delete(tourney_changelog_table(), ['post_id' => intval($post_id)], ['%d']);
}
add_action('before_delete_post', 'tourney_changelog_delete_post');

/**
 * Registriert den REST-Endpunkt zum Abrufen des Protokolls.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/changelog?postId=<id>&limit=<n> - Protokoll eines Turniers, neueste Einträge zuerst
    register_rest_route('tourney/v1', '/changelog', [
        'methods' => 'GET',
        'callback' => 'tourney_get_changelog',
        'permission_callback' => 'tourney_api_can_edit_request_post'
    ]);

});

/**
 * Liefert die letzten Protokolleinträge eines Turniers.
 *
 * @param WP_REST_Request $request Parameter postId (oder slug) und optional limit (1–500, Default 200).
 * @return array|WP_REST_Response ["entries" => [["ts", "user", "obj", "objId", "text"], ...]] oder Fehler.
 */
function tourney_get_changelog(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }
    if (!tourney_changelog_enabled() || get_option('tourney_changelog_db') !== '1') {
        return ["entries" => []];
    }
    global $wpdb;
    $limit = min(500, max(1, intval($request->get_param('limit') ?: 200)));
    $table = tourney_changelog_table();
    $rows  = $wpdb->get_results($wpdb->prepare(
        "SELECT ts, user_name, obj, obj_id, text FROM $table WHERE post_id = %d ORDER BY id DESC LIMIT %d",
        $post_id, $limit
    ));
    $entries = array_map(function ($r) {
        return [
            "ts"    => $r->ts,
            "user"  => $r->user_name,
            "obj"   => $r->obj,
            "objId" => intval($r->obj_id),
            "text"  => $r->text,
        ];
    }, $rows ?: []);
    return ["entries" => $entries];
}
