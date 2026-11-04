<?php

/**
 * Turnier-App als Progressive Web App (nur Website-Variante, TOURNEY_FWS = true).
 *
 * Die Seite /app/ (Menü Tools → Offline-App) startet den Client mit data-page="app" (pages.AppView):
 * App installieren und lokale Turniere im Browser verwalten, auch ohne Netz.
 * - Manifest:        <home>/?tourney_pwa=manifest
 * - Service Worker:  <home>/?tourney_pwa=sw (Inhalt aus includes/pwa-sw.js), Scope /app/
 * Voraussetzung ist HTTPS (bzw. localhost).
 */

if (!defined('ABSPATH')) {
    exit;
}

/**
 * Pfad der App-Seite relativ zur Website (mit abschließendem '/').
 *
 * @return string Z. B. '/app/' bzw. '/wp/app/' bei WordPress im Unterverzeichnis.
 */
function tourney_app_path() {
    return wp_parse_url(home_url('/app/'), PHP_URL_PATH);
}

/**
 * Prüft, ob die aktuelle Anfrage die App-Seite /app/ ist.
 *
 * @return bool True auf /app/.
 */
function tourney_is_app() {
    return (bool) get_query_var('tourney_app');
}

/**
 * Registriert die Rewrite-Regel für /app/ und schreibt die Regeln einmalig neu (Option tourney_app_rules).
 *
 * @return void
 */
function tourney_app_rewrite() {
    add_rewrite_rule('^app/?$', 'index.php?tourney_app=1', 'top');
    if (get_option('tourney_app_rules') !== '1') {
        flush_rewrite_rules(false);
        update_option('tourney_app_rules', '1');
    }
}

/**
 * Liefert Service Worker oder Manifest aus, wenn ?tourney_pwa=sw|manifest gesetzt ist, und beendet die Anfrage.
 * Läuft früh in 'init', damit weder Template noch Umleitungen (z. B. Sprach-Plugins) greifen.
 *
 * @return void
 */
function tourney_pwa_serve() {
    $what = isset($_GET['tourney_pwa']) ? sanitize_key($_GET['tourney_pwa']) : '';
    if ($what === 'sw') {
        header('Content-Type: application/javascript; charset=utf-8');
        header('Cache-Control: no-cache');
        $config = array('version' => TOURNEY_VERSION, 'precache' => tourney_pwa_precache_urls());
        echo 'self.TOURNEY_PWA = ' . wp_json_encode($config, JSON_UNESCAPED_SLASHES) . ";\n";
        readfile(TOURNEY_PLUGIN_PATH . 'includes/pwa-sw.js');
        exit;
    }
    if ($what === 'manifest') {
        header('Content-Type: application/manifest+json; charset=utf-8');
        header('Cache-Control: no-cache');
        echo wp_json_encode(tourney_pwa_manifest(), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit;
    }
}

/**
 * URLs, die der Service Worker bei der Installation lädt: App-Seite, Daten (Nachrichten, Demo-Turnier) und Icons.
 * Alles weitere (main.js, CSS, Bootstrap) übergibt die App-Seite nach dem ersten Laden.
 *
 * @return string[] Absolute URLs.
 */
function tourney_pwa_precache_urls() {
    $main = TOURNEY_PLUGIN_PATH . 'tourney.php';
    $urls = array(home_url('/app/'));
    foreach (glob(TOURNEY_PLUGIN_PATH . 'data/*.json') ?: array() as $file) {
        $urls[] = plugins_url('data/' . basename($file), $main);
    }
    foreach (array('logo.png', 'favicon.ico', 'pwa-192.png', 'pwa-512.png') as $img) {
        $urls[] = plugins_url('img/' . $img, $main);
    }
    return $urls;
}

/**
 * Baut das Web-App-Manifest der Turnier-App.
 *
 * @return array Manifest als assoziatives Array.
 */
function tourney_pwa_manifest() {
    $main  = TOURNEY_PLUGIN_PATH . 'tourney.php';
    $path  = tourney_app_path();
    $site  = html_entity_decode(get_bloginfo('name'), ENT_QUOTES, 'UTF-8');
    $name  = $site !== '' ? $site : 'TurnierService';

    return array(
        'id'               => $path,
        'name'             => $name,
        'short_name'       => mb_strimwidth($name, 0, 15, '…', 'UTF-8'),
        'description'      => 'Turniere offline im Browser organisieren',
        'start_url'        => $path,
        'scope'            => $path,
        'display'          => 'standalone',
        'background_color' => '#ffffff',
        'theme_color'      => '#336699',
        'lang'             => 'de',
        'icons'            => array(
            array('src' => plugins_url('img/pwa-192.png', $main), 'sizes' => '192x192', 'type' => 'image/png'),
            array('src' => plugins_url('img/pwa-512.png', $main), 'sizes' => '512x512', 'type' => 'image/png'),
        ),
    );
}

/**
 * Seitentitel der App-Seite ("Offline-App – Website").
 *
 * @param array $parts Titelbestandteile von WordPress.
 * @return array Angepasste Titelbestandteile.
 */
function tourney_app_title_parts($parts) {
    return tourney_is_app() ? array('title' => 'Offline-App', 'site' => get_bloginfo('name')) : $parts;
}

/**
 * Gibt auf der App-Seite Manifest-Link, Theme-Farbe und Apple-Touch-Icon im <head> aus.
 *
 * @return void
 */
function tourney_pwa_head() {
    if (!tourney_is_app()) {
        return;
    }
    echo '<link rel="manifest" href="' . esc_url(add_query_arg('tourney_pwa', 'manifest', home_url('/'))) . '">' . "\n";
    echo '<meta name="theme-color" content="#336699">' . "\n";
    echo '<link rel="apple-touch-icon" href="' . esc_url(plugins_url('img/pwa-192.png', TOURNEY_PLUGIN_PATH . 'tourney.php')) . '">' . "\n";
}

/**
 * Registriert auf der App-Seite den Service Worker (Scope /app/) und übergibt ihm alle bereits geladenen Dateien
 * zum Cachen (beim ersten Aufruf lief noch kein Service Worker). Merkt sich außerdem das Ereignis
 * beforeinstallprompt in window.tourneyInstallPrompt, das pages.AppView für den Install-Button nutzt
 * (Ereignis 'tourney-installable' bei Änderung).
 *
 * @return void
 */
function tourney_pwa_footer() {
    if (!tourney_is_app()) {
        return;
    }
    $flags = JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_UNESCAPED_SLASHES;
    $sw    = wp_json_encode(add_query_arg('tourney_pwa', 'sw', home_url('/')), $flags);
    $scope = wp_json_encode(tourney_app_path(), $flags);
    ?>
    <script>
    (function () {
        window.addEventListener('beforeinstallprompt', function (e) {
            e.preventDefault();
            window.tourneyInstallPrompt = e;
            window.dispatchEvent(new Event('tourney-installable'));
        });
        window.addEventListener('appinstalled', function () {
            window.tourneyInstallPrompt = null;
            window.dispatchEvent(new Event('tourney-installable'));
        });
        if (!('serviceWorker' in navigator)) return;
        window.addEventListener('load', function () {
            navigator.serviceWorker.register(<?php echo $sw; ?>, { scope: <?php echo $scope; ?> }).then(function () {
                return navigator.serviceWorker.ready;
            }).then(function (reg) {
                var urls = [location.href].concat(performance.getEntriesByType('resource').map(function (e) { return e.name; }));
                if (reg.active) reg.active.postMessage({ type: 'tourney-cache', urls: urls });
            }).catch(function (err) {
                console.warn('Tourney PWA: Service Worker nicht registriert', err);
            });
        });
    })();
    </script>
    <?php
}

add_action('init', 'tourney_pwa_serve', 0);
add_action('init', 'tourney_app_rewrite');
add_filter('query_vars', fn($vars) => array_merge($vars, ['tourney_app']));
add_filter('pre_handle_404', fn($preempt) => tourney_is_app() ? true : $preempt);
add_filter('redirect_canonical', fn($redirect) => tourney_is_app() ? false : $redirect);
add_filter('document_title_parts', 'tourney_app_title_parts', 20);
add_action('wp_head', 'tourney_pwa_head');
add_action('wp_footer', 'tourney_pwa_footer');
