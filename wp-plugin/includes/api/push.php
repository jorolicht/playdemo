<?php
/**
 * Push-Benachrichtigungen für Durchsagen (Web Push, Bibliothek minishlink/web-push).
 *
 * - Schlüsselpaar (VAPID) je Installation, beim ersten Gebrauch erzeugt und in der Option `tourney_vapid` gespeichert.
 * - Geräte melden sich je Turnier in „Mein Turnier“ an; gespeichert in der Tabelle `<prefix>tourney_push`
 *   (Push-Adresse des Browsers mit Schlüsseln, Turnier, Sprache, Zeitpunkt).
 * - Beim Senden einer Durchsage (includes/api/announcement.php) gehen Benachrichtigungen an alle Geräte des Turniers;
 *   abgelaufene Anmeldungen (404/410 des Push-Dienstes) werden gelöscht.
 * - Service Worker für Push: <home>/?tourney_push=sw (Inhalt aus includes/push-sw.js), Scope <home>/tourney-push/.
 *
 * REST (öffentlich, Turnier muss veröffentlicht sein):
 * - GET  /tourney/v1/push-key                                → {"publicKey": "..."}
 * - POST /tourney/v1/push-subscribe?postId=<id>    {subscription, lang}
 * - POST /tourney/v1/push-unsubscribe?postId=<id>  {endpoint}
 */

use Minishlink\WebPush\WebPush;
use Minishlink\WebPush\Subscription;
use Minishlink\WebPush\VAPID;

require_once TOURNEY_PLUGIN_PATH . 'vendor/autoload.php';

/** Höchstzahl angemeldeter Geräte je Turnier (Schutz vor Missbrauch). */
const TOURNEY_PUSH_MAX_PER_TOURNEY = 5000;

/**
 * Name der Tabelle der Geräte-Anmeldungen.
 *
 * @return string Tabellenname mit Präfix.
 */
function tourney_push_table() {
    global $wpdb;
    return $wpdb->prefix . 'tourney_push';
}

/**
 * Legt die Tabelle an bzw. passt sie an (einmal je Schema-Version, Option tourney_push_db).
 *
 * @return void
 */
function tourney_push_install() {
    if (get_option('tourney_push_db') === '1') {
        return;
    }
    global $wpdb;
    require_once ABSPATH . 'wp-admin/includes/upgrade.php';
    $table = tourney_push_table();
    dbDelta("CREATE TABLE $table (
        id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
        post_id bigint(20) unsigned NOT NULL,
        ep_hash char(40) NOT NULL,
        endpoint varchar(1000) NOT NULL,
        p256dh varchar(255) NOT NULL,
        auth varchar(255) NOT NULL,
        lang varchar(5) NOT NULL DEFAULT 'de',
        created datetime NOT NULL,
        PRIMARY KEY  (id),
        UNIQUE KEY post_ep (post_id,ep_hash)
    ) {$wpdb->get_charset_collate()};");
    update_option('tourney_push_db', '1');
}
add_action('plugins_loaded', 'tourney_push_install');

/**
 * Schlüsselpaar (VAPID) der Installation; wird beim ersten Aufruf erzeugt.
 *
 * @return array{publicKey: string, privateKey: string}
 */
function tourney_push_vapid() {
    $keys = get_option('tourney_vapid');
    if (!is_array($keys) || empty($keys['publicKey']) || empty($keys['privateKey'])) {
        $keys = VAPID::createVapidKeys();
        update_option('tourney_vapid', $keys, false);
    }
    return $keys;
}

/**
 * Liefert den Service Worker für Push aus (?tourney_push=sw) und beendet die Anfrage.
 *
 * @return void
 */
function tourney_push_serve_sw() {
    if (isset($_GET['tourney_push']) && sanitize_key($_GET['tourney_push']) === 'sw') {
        header('Content-Type: application/javascript; charset=utf-8');
        header('Cache-Control: no-cache');
        header('Service-Worker-Allowed: ' . wp_parse_url(home_url('/'), PHP_URL_PATH));
        readfile(TOURNEY_PLUGIN_PATH . 'includes/push-sw.js');
        exit;
    }
}
add_action('init', 'tourney_push_serve_sw', 1);

add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/push-key', [
        'methods'             => 'GET',
        'callback'            => fn() => ['publicKey' => tourney_push_vapid()['publicKey']],
        'permission_callback' => '__return_true'
    ]);
    register_rest_route('tourney/v1', '/push-subscribe', [
        'methods'             => 'POST',
        'callback'            => 'tourney_push_subscribe',
        'permission_callback' => '__return_true'
    ]);
    register_rest_route('tourney/v1', '/push-unsubscribe', [
        'methods'             => 'POST',
        'callback'            => 'tourney_push_unsubscribe',
        'permission_callback' => '__return_true'
    ]);
});

/**
 * Prüft, ob die Post-ID ein veröffentlichtes Turnier ist.
 *
 * @param int $post_id Post-ID.
 * @return bool
 */
function tourney_push_is_tourney($post_id) {
    $post = $post_id ? get_post($post_id) : null;
    return $post && $post->post_type === 'tourney' && $post->post_status === 'publish';
}

/**
 * POST push-subscribe: meldet ein Gerät für die Durchsagen eines Turniers an (erneute Anmeldung aktualisiert).
 *
 * @param WP_REST_Request $request Parameter postId; Body {"subscription": {"endpoint", "keys": {"p256dh", "auth"}}, "lang"}.
 * @return array|WP_REST_Response {"subscribed": true} oder Fehler.
 */
function tourney_push_subscribe(WP_REST_Request $request) {
    global $wpdb;
    $post_id  = ApiHelper::getPostId($request);
    $body     = json_decode($request->get_body(), true);
    $sub      = is_array($body) && is_array($body['subscription'] ?? null) ? $body['subscription'] : [];
    $endpoint = strval($sub['endpoint'] ?? '');
    $p256dh   = strval($sub['keys']['p256dh'] ?? '');
    $auth     = strval($sub['keys']['auth'] ?? '');
    $lang     = ($body['lang'] ?? '') === 'en' ? 'en' : 'de';
    if (!tourney_push_is_tourney($post_id)) {
        return ApiHelper::error("not_found", "Tourney not found", "", "tourney_push_subscribe", HttpStatus::NOT_FOUND);
    }
    if (!wp_http_validate_url($endpoint) || strpos($endpoint, 'https://') !== 0 || strlen($endpoint) > 1000
        || !preg_match('/^[A-Za-z0-9_-]{20,200}={0,2}$/', $p256dh) || !preg_match('/^[A-Za-z0-9_-]{8,100}={0,2}$/', $auth)) {
        return ApiHelper::error("invalid_param", "Invalid subscription", "", "tourney_push_subscribe", HttpStatus::BAD_REQUEST);
    }
    $table = tourney_push_table();
    $count = (int) $wpdb->get_var($wpdb->prepare("SELECT COUNT(*) FROM $table WHERE post_id = %d", $post_id));
    if ($count >= TOURNEY_PUSH_MAX_PER_TOURNEY) {
        return ApiHelper::error("limit", "Too many subscriptions", "", "tourney_push_subscribe", 429);
    }
    $wpdb->replace($table, [
        'post_id' => $post_id, 'ep_hash' => sha1($endpoint), 'endpoint' => $endpoint,
        'p256dh' => $p256dh, 'auth' => $auth, 'lang' => $lang, 'created' => current_time('mysql'),
    ], ['%d', '%s', '%s', '%s', '%s', '%s', '%s']);
    return ["subscribed" => true];
}

/**
 * POST push-unsubscribe: meldet ein Gerät von den Durchsagen eines Turniers ab.
 *
 * @param WP_REST_Request $request Parameter postId; Body {"endpoint": "..."}.
 * @return array {"subscribed": false}
 */
function tourney_push_unsubscribe(WP_REST_Request $request) {
    global $wpdb;
    $post_id  = ApiHelper::getPostId($request);
    $body     = json_decode($request->get_body(), true);
    $endpoint = strval(is_array($body) ? ($body['endpoint'] ?? '') : '');
    if ($post_id && $endpoint !== '') {
        $wpdb->delete(tourney_push_table(), ['post_id' => $post_id, 'ep_hash' => sha1($endpoint)], ['%d', '%s']);
    }
    return ["subscribed" => false];
}

/**
 * Löscht alle Geräte-Anmeldungen eines Turniers (z. B. beim Löschen des Turniers).
 *
 * @param int $post_id Post-ID.
 * @return void
 */
function tourney_push_delete_for_post($post_id) {
    global $wpdb;
    $wpdb->delete(tourney_push_table(), ['post_id' => intval($post_id)], ['%d']);
}
add_action('before_delete_post', 'tourney_push_delete_for_post');

/**
 * Schickt eine Durchsage als Push-Benachrichtigung an alle Geräte des Turniers.
 *
 * Fehler beim Versand brechen das Speichern der Durchsage nicht ab; abgelaufene Anmeldungen werden gelöscht.
 *
 * @param int   $post_id Post-ID des Turniers.
 * @param array $entry   Durchsage {"id", "text", "ts"}.
 * @return array{sent: int, removed: int} Anzahl erfolgreich zugestellter und gelöschter Anmeldungen.
 */
function tourney_push_announcement($post_id, array $entry) {
    global $wpdb;
    $table = tourney_push_table();
    $subs  = $wpdb->get_results($wpdb->prepare("SELECT id, endpoint, p256dh, auth, lang FROM $table WHERE post_id = %d", $post_id), ARRAY_A);
    if (empty($subs)) {
        return ['sent' => 0, 'removed' => 0];
    }
    $keys = tourney_push_vapid();
    $sent = 0;
    $removed = 0;
    try {
        $push = new WebPush(
            ['VAPID' => ['subject' => home_url('/'), 'publicKey' => $keys['publicKey'], 'privateKey' => $keys['privateKey']]],
            ['TTL' => 3600, 'urgency' => 'high', 'topic' => substr(preg_replace('/[^A-Za-z0-9_-]/', '', 'ann' . $entry['id']), 0, 32)],
            10,
            ['timeout' => 10]
        );
        $title = html_entity_decode(get_the_title($post_id), ENT_QUOTES, 'UTF-8');
        $url   = get_permalink($post_id) . '#mein-turnier';
        foreach ($subs as $s) {
            $payload = wp_json_encode([
                'title' => $title,
                'body'  => $entry['text'],
                'url'   => $url,
                'tag'   => 'ann-' . $post_id,
                'postId'=> intval($post_id),
                'icon'  => plugins_url('img/pwa-192.png', TOURNEY_PLUGIN_PATH . 'tourney.php'),
            ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            $push->queueNotification(Subscription::create([
                'endpoint' => $s['endpoint'],
                'keys'     => ['p256dh' => $s['p256dh'], 'auth' => $s['auth']],
            ]), $payload);
        }
        foreach ($push->flush() as $report) {
            if ($report->isSuccess()) {
                $sent++;
            } elseif ($report->isSubscriptionExpired()) {
                $wpdb->delete($table, ['post_id' => $post_id, 'ep_hash' => sha1($report->getEndpoint())], ['%d', '%s']);
                $removed++;
            }
        }
    } catch (\Throwable $e) {
        error_log('tourney_push_announcement: ' . $e->getMessage());
    }
    return ['sent' => $sent, 'removed' => $removed];
}
