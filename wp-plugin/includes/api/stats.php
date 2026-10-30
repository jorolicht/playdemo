<?php

/**
 * Anonyme Nutzungsstatistik: Tageszähler für Homepage-Aufrufe und Start-Buttons.
 *
 * Gespeichert werden nur Zähler pro Tag und Ereignis (Tabelle {prefix}tourney_stats),
 * keine IP-Adressen oder anderen personenbezogenen Daten. Auswertung per Shortcode [tourney_stats].
 */

/** Erlaubte Ereignisse (Schlüssel => Spaltenüberschrift im Shortcode). */
const TOURNEY_STATS_EVENTS = [
    'home'    => 'Homepage',
    'demo'    => 'Demo',
    'comp'    => 'Wettbewerb',
    'tourney' => 'Turnier',
];

/** Maximale Anzahl Meldungen pro IP-Adresse und Minute. */
const TOURNEY_STATS_RATE_LIMIT = 30;

/**
 * Liefert den Namen der Statistik-Tabelle.
 *
 * @return string Tabellenname mit WordPress-Präfix.
 */
function tourney_stats_table() {
    global $wpdb;
    return $wpdb->prefix . 'tourney_stats';
}

/**
 * Legt die Statistik-Tabelle an, falls sie noch nicht existiert (einmalig, gemerkt in Option tourney_stats_db).
 *
 * @return void
 */
function tourney_stats_install() {
    if (get_option('tourney_stats_db') === '1') {
        return;
    }
    global $wpdb;
    require_once ABSPATH . 'wp-admin/includes/upgrade.php';
    $table = tourney_stats_table();
    dbDelta("CREATE TABLE $table (
        day date NOT NULL,
        event varchar(16) NOT NULL,
        hits int(10) unsigned NOT NULL DEFAULT 0,
        PRIMARY KEY  (day,event)
    ) {$wpdb->get_charset_collate()};");
    update_option('tourney_stats_db', '1');
}
add_action('plugins_loaded', 'tourney_stats_install');

/**
 * Registriert den REST-Endpunkt für Statistik-Meldungen.
 */
add_action('rest_api_init', function () {

    // POST /tourney/v1/stats-hit?e=<event> - Zählt ein Ereignis (anonym, per navigator.sendBeacon)
    register_rest_route('tourney/v1', '/stats-hit', [
        'methods' => 'POST',
        'callback' => 'tourney_stats_hit',
        'permission_callback' => '__return_true'
    ]);

});

/**
 * Erhöht den Tageszähler eines Ereignisses.
 *
 * Unbekannte Ereignisse, Bots (User-Agent) und mehr als TOURNEY_STATS_RATE_LIMIT Meldungen
 * pro IP-Adresse und Minute werden ignoriert; die Antwort ist immer 204, damit nichts ausgespäht werden kann.
 *
 * @param WP_REST_Request $request Anfrage mit Parameter e (Ereignis).
 * @return WP_REST_Response Leere Antwort (204).
 */
function tourney_stats_hit(WP_REST_Request $request) {
    global $wpdb;
    $event = (string) $request->get_param('e');
    $agent = $_SERVER['HTTP_USER_AGENT'] ?? '';

    if (array_key_exists($event, TOURNEY_STATS_EVENTS) && !preg_match('/bot|crawl|spider|slurp/i', $agent)) {
        $ip_key = 'pd_stats_ip_' . md5($_SERVER['REMOTE_ADDR'] ?? '');
        $count = (int) get_transient($ip_key);
        if ($count < TOURNEY_STATS_RATE_LIMIT) {
            set_transient($ip_key, $count + 1, MINUTE_IN_SECONDS);
            $table = tourney_stats_table();
            $wpdb->query($wpdb->prepare(
                "INSERT INTO $table (day, event, hits) VALUES (%s, %s, 1) ON DUPLICATE KEY UPDATE hits = hits + 1",
                current_time('Y-m-d'), $event
            ));
        }
    }
    return new WP_REST_Response(null, 204);
}

/**
 * Shortcode [tourney_stats]: Tabelle der Zähler pro Tag (letzte 30 Tage) oder pro Woche (letzte 12 ISO-Wochen).
 *
 * Nur für Benutzer mit manage_options sichtbar; Umschaltung über den URL-Parameter stats=day|week.
 *
 * @return string HTML der Statistik oder leerer String ohne Berechtigung.
 */
function tourney_stats_render() {
    if (!current_user_can('manage_options')) {
        return '';
    }
    global $wpdb;
    $table = tourney_stats_table();
    $week  = ($_GET['stats'] ?? '') === 'week';
    $today = current_time('Y-m-d');

    // Zeilenschlüssel (Tag bzw. ISO-Woche JJJJWW) vom neuesten zum ältesten, auch ohne Daten
    $keys = [];
    if ($week) {
        for ($i = 0; $i < 12; $i++) {
            $keys[] = date('oW', strtotime("$today -$i week"));
        }
        $from = date('Y-m-d', strtotime('-11 week', strtotime('monday this week', strtotime($today))));
        $sql  = "SELECT YEARWEEK(day, 3) AS k, event, SUM(hits) AS hits FROM $table WHERE day >= %s GROUP BY k, event";
    } else {
        for ($i = 0; $i < 30; $i++) {
            $keys[] = date('Y-m-d', strtotime("$today -$i day"));
        }
        $from = $keys[29];
        $sql  = "SELECT day AS k, event, hits FROM $table WHERE day >= %s";
    }
    $data = [];
    foreach ($wpdb->get_results($wpdb->prepare($sql, $from)) as $row) {
        $data[$row->k][$row->event] = (int) $row->hits;
    }

    $url  = remove_query_arg('stats');
    $html = '<div class="tourney-stats">';
    $html .= '<p><a href="' . esc_url($url) . '"' . ($week ? '' : ' style="font-weight:bold"') . '>Pro Tag</a> | ';
    $html .= '<a href="' . esc_url(add_query_arg('stats', 'week', $url)) . '"' . ($week ? ' style="font-weight:bold"' : '') . '>Pro Woche</a></p>';
    $html .= '<table class="table table-sm table-striped"><thead><tr><th>' . ($week ? 'Woche' : 'Tag') . '</th>';
    foreach (TOURNEY_STATS_EVENTS as $label) {
        $html .= '<th style="text-align:right">' . esc_html($label) . '</th>';
    }
    $html .= '</tr></thead><tbody>';
    $sum = array_fill_keys(array_keys(TOURNEY_STATS_EVENTS), 0);
    foreach ($keys as $k) {
        $label = $week ? 'KW ' . substr($k, 4) . '/' . substr($k, 0, 4) : date_i18n('D, d.m.Y', strtotime($k));
        $html .= '<tr><td>' . esc_html($label) . '</td>';
        foreach (array_keys(TOURNEY_STATS_EVENTS) as $ev) {
            $hits = $data[$k][$ev] ?? 0;
            $sum[$ev] += $hits;
            $html .= '<td style="text-align:right">' . $hits . '</td>';
        }
        $html .= '</tr>';
    }
    $html .= '</tbody><tfoot><tr><th>Summe</th>';
    foreach ($sum as $hits) {
        $html .= '<th style="text-align:right">' . $hits . '</th>';
    }
    $html .= '</tr></tfoot></table></div>';
    return $html;
}
add_shortcode('tourney_stats', 'tourney_stats_render');
