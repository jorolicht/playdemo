<?php
/**
 * Tägliche Statistik-Mail an den Administrator der Website.
 *
 * Jeden Morgen um TOURNEY_STATS_MAIL_HOUR Uhr (Zeitzone von WordPress, Einstellungen > Allgemein) werden die Zähler
 * der Nutzungsstatistik (Tabelle {prefix}tourney_stats, siehe includes/api/stats.php) per wp_mail() an die Adresse
 * aus der Option admin_email geschickt.
 *
 * Zeitsteuerung über WP-Cron mit einem Einzelereignis, das nach jedem Lauf für den nächsten Morgen neu angelegt wird.
 * So bleibt die Uhrzeit auch nach einem Wechsel zwischen Sommer- und Winterzeit gleich. Da WP-Cron nur bei
 * Seitenaufrufen läuft, stößt auf dem Server ein System-Cron alle 5 Minuten `wp cron event run --due-now` an
 * (siehe doc/release.md).
 *
 * Aktiv nur auf der Website turnier-service.info (TOURNEY_FWS), nicht im Embed-Plugin für fremde Websites.
 *
 * Manuell auslösen: docker exec wp-cli-instance wp cron event run tourney_stats_mail --allow-root --path=/var/www/html
 */

/** Name des WP-Cron-Ereignisses. */
const TOURNEY_STATS_MAIL_HOOK = 'tourney_stats_mail';

/** Uhrzeit (Stunde) des Versands in der Zeitzone von WordPress. */
const TOURNEY_STATS_MAIL_HOUR = 8;

/**
 * Prüft, ob die Statistik-Mail auf dieser Installation verschickt wird.
 *
 * @return bool true auf der Website turnier-service.info (TOURNEY_FWS).
 */
function tourney_stats_mail_enabled() {
    return defined('TOURNEY_FWS') && TOURNEY_FWS;
}

/**
 * Zeitpunkt des nächsten Versands: heute um TOURNEY_STATS_MAIL_HOUR Uhr, falls noch nicht vorbei, sonst morgen.
 *
 * @return int Unix-Zeitstempel.
 */
function tourney_stats_mail_next_run() {
    $now  = new DateTimeImmutable('now', wp_timezone());
    $next = $now->setTime(TOURNEY_STATS_MAIL_HOUR, 0);
    if ($next <= $now) {
        $next = $next->modify('+1 day');
    }
    return $next->getTimestamp();
}

/**
 * Legt das WP-Cron-Ereignis an, falls keines geplant ist; auf Installationen ohne Statistik-Mail wird es entfernt.
 *
 * @return void
 */
function tourney_stats_mail_schedule() {
    if (!tourney_stats_mail_enabled()) {
        wp_clear_scheduled_hook(TOURNEY_STATS_MAIL_HOOK);
        return;
    }
    if (!wp_next_scheduled(TOURNEY_STATS_MAIL_HOOK)) {
        wp_schedule_single_event(tourney_stats_mail_next_run(), TOURNEY_STATS_MAIL_HOOK);
    }
}
add_action('init', 'tourney_stats_mail_schedule');

/**
 * Entfernt das WP-Cron-Ereignis beim Deaktivieren des Plugins.
 *
 * @return void
 */
function tourney_stats_mail_unschedule() {
    wp_clear_scheduled_hook(TOURNEY_STATS_MAIL_HOOK);
}

/**
 * WP-Cron-Ereignis: verschickt die Statistik-Mail und plant den nächsten Versand.
 *
 * @return void
 */
function tourney_stats_mail_run() {
    if (!tourney_stats_mail_enabled()) {
        return;
    }
    tourney_stats_mail_send();
    if (!wp_next_scheduled(TOURNEY_STATS_MAIL_HOOK)) {
        wp_schedule_single_event(tourney_stats_mail_next_run(), TOURNEY_STATS_MAIL_HOOK);
    }
}
add_action(TOURNEY_STATS_MAIL_HOOK, 'tourney_stats_mail_run');

/**
 * Summiert die Zähler je Ereignis für einen Zeitraum.
 *
 * @param string $from Erster Tag (Y-m-d).
 * @param string $to   Letzter Tag (Y-m-d), einschließlich.
 * @return array<string,int> Ereignis => Summe, für alle Ereignisse aus TOURNEY_STATS_EVENTS.
 */
function tourney_stats_mail_sum($from, $to) {
    global $wpdb;
    $table = tourney_stats_table();
    $sum   = array_fill_keys(array_keys(TOURNEY_STATS_EVENTS), 0);
    $rows  = $wpdb->get_results($wpdb->prepare(
        "SELECT event, SUM(hits) AS hits FROM $table WHERE day BETWEEN %s AND %s GROUP BY event", $from, $to
    ));
    foreach ($rows as $row) {
        if (array_key_exists($row->event, $sum)) {
            $sum[$row->event] = (int) $row->hits;
        }
    }
    return $sum;
}

/**
 * Erzeugt den HTML-Text der Statistik-Mail: je Zeitraum eine Zeile mit den Zählern der Ereignisse und der Summe.
 * Zeiträume (bis einschließlich gestern): gestern, letzte 7 Tage, letzte 30 Tage, Vormonat.
 *
 * @return array{0:string,1:string} Betreff und HTML-Text.
 */
function tourney_stats_mail_content() {
    $tz        = wp_timezone();
    $yesterday = (new DateTimeImmutable('yesterday', $tz));
    $y         = $yesterday->format('Y-m-d');
    $prevMonth = (new DateTimeImmutable('first day of last month', $tz));
    $periods = [
        'Gestern (' . wp_date('D, d.m.Y', $yesterday->getTimestamp()) . ')' => [$y, $y],
        'Letzte 7 Tage'  => [$yesterday->modify('-6 day')->format('Y-m-d'), $y],
        'Letzte 30 Tage' => [$yesterday->modify('-29 day')->format('Y-m-d'), $y],
        'Vormonat (' . wp_date('F Y', $prevMonth->getTimestamp()) . ')' =>
            [$prevMonth->format('Y-m-01'), $prevMonth->format('Y-m-t')],
    ];

    $cell = 'style="padding:4px 10px;border-bottom:1px solid #ddd;text-align:right"';
    $html  = '<p>Nutzungsstatistik von <a href="' . esc_url(home_url('/')) . '">' . esc_html(wp_parse_url(home_url(), PHP_URL_HOST)) . '</a>';
    $html .= ' (Stand ' . esc_html(wp_date('d.m.Y H:i')) . ' Uhr)</p>';
    $html .= '<table style="border-collapse:collapse;font-family:sans-serif;font-size:14px"><thead><tr>';
    $html .= '<th style="padding:4px 10px;border-bottom:2px solid #333;text-align:left">Zeitraum</th>';
    foreach (TOURNEY_STATS_EVENTS as $label) {
        $html .= '<th style="padding:4px 10px;border-bottom:2px solid #333;text-align:right">' . esc_html($label) . '</th>';
    }
    $html .= '<th style="padding:4px 10px;border-bottom:2px solid #333;text-align:right">Summe</th></tr></thead><tbody>';
    $yesterdayTotal = 0;
    foreach ($periods as $label => [$from, $to]) {
        $sum = tourney_stats_mail_sum($from, $to);
        if ($from === $y && $to === $y) {
            $yesterdayTotal = array_sum($sum);
        }
        $html .= '<tr><td style="padding:4px 10px;border-bottom:1px solid #ddd">' . esc_html($label) . '</td>';
        foreach ($sum as $hits) {
            $html .= '<td ' . $cell . '>' . $hits . '</td>';
        }
        $html .= '<td ' . $cell . '><b>' . array_sum($sum) . '</b></td></tr>';
    }
    $html .= '</tbody></table>';
    $html .= '<p style="color:#666;font-size:12px">Homepage = Aufrufe der Startseite, Demo/Wettbewerb/Turnier = Klicks auf die '
           . 'Start-Buttons. Gezählt werden nur Tageszähler ohne personenbezogene Daten.</p>';

    $subject = sprintf('Statistik %s: %d Aufrufe am %s', wp_parse_url(home_url(), PHP_URL_HOST), $yesterdayTotal,
                       wp_date('d.m.Y', $yesterday->getTimestamp()));
    return [$subject, $html];
}

/**
 * Verschickt die Statistik-Mail an die Adresse aus der Option admin_email.
 *
 * @return bool true, wenn wp_mail() die Mail angenommen hat.
 */
function tourney_stats_mail_send() {
    $to = get_option('admin_email');
    if (!is_email($to)) {
        error_log('tourney_stats_mail: keine gültige admin_email');
        return false;
    }
    [$subject, $html] = tourney_stats_mail_content();
    $ok = wp_mail($to, $subject, $html, ['Content-Type: text/html; charset=UTF-8']);
    if (!$ok) {
        error_log('tourney_stats_mail: Versand fehlgeschlagen');
    }
    return $ok;
}
