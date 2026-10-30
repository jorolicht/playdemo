<?php

/**
 * Eigene URLs für die statischen Seiten der Website-Variante (TOURNEY_FWS = true), z. B. /impressum/ oder /preise/.
 *
 * Eine Rewrite-Regel bildet die Slugs auf die Query-Variable tourney_static ab, ohne WordPress-Seiten anzulegen.
 * Der Inhalt kommt aus pages/<datei>.html (kopiert aus site/content) und steht schon im Server-HTML (SEO);
 * der Client öffnet die Seite beim Start über data-page="static:<seite>" (MainView) und ersetzt den Inhalt.
 * Die Zuordnung Seitenname -> Slug im Client steht in pages.MainView.StaticSlugs.
 */

/** Slug => Datei in pages/ (ohne .html), Seitenname im Client (MainView-Parameter), Titel und Beschreibung. */
const TOURNEY_STATIC_PAGES = [
    'funktionen'  => ['file' => 'funktionen', 'page' => 'funktionen', 'title' => 'Funktionen & Features',
                      'desc' => 'Alle Funktionen von TurnierService: K.o.-System, Gruppen, Jeder gegen Jeden und Schweizer System, Ergebniseingabe per QR-Code, Live-Anzeige und Urkunden.'],
    'preise'      => ['file' => 'pricing', 'page' => 'pricing', 'title' => 'Support & Kosten',
                      'desc' => 'Kosten und Support von TurnierService: lokal im Browser dauerhaft kostenlos, Cloud-Turniere mit Spieler-Self-Service und Live-Anzeige.'],
    'excel'       => ['file' => 'excel', 'page' => 'excel', 'title' => 'Excel-Downloads & Software',
                      'desc' => 'Excel-Vorlagen und Software-Downloads für die Turnierorganisation im Tischtennis.'],
    'plugin'      => ['file' => 'plugin', 'page' => 'plugin', 'title' => 'WordPress Plugin',
                      'desc' => 'Das WordPress-Plugin von TurnierService: Turniere direkt in die eigene Vereinswebsite einbetten.'],
    'historie'    => ['file' => 'history', 'page' => 'history', 'title' => 'Versionshistorie',
                      'desc' => 'Versionshistorie von TurnierService: neue Funktionen und Verbesserungen aller Versionen.'],
    'impressum'   => ['file' => 'impressum', 'page' => 'impressum', 'title' => 'Impressum',
                      'desc' => 'Impressum und Anbieterkennzeichnung von TurnierService.'],
    'datenschutz' => ['file' => 'privacy', 'page' => 'privacy', 'title' => 'Datenschutzerklärung',
                      'desc' => 'Datenschutzerklärung von TurnierService: welche Daten verarbeitet werden und welche Rechte Sie haben.'],
    'kontakt'     => ['file' => 'contact', 'page' => 'contact', 'title' => 'Kontakt',
                      'desc' => 'Kontakt zu TurnierService: Fragen, Support und Feedback zur Turniersoftware.'],
    'agb'         => ['file' => 'gtc', 'page' => 'gtc', 'title' => 'Allgemeine Geschäftsbedingungen',
                      'desc' => 'Allgemeine Geschäftsbedingungen (AGB) von TurnierService.'],
];

/**
 * Liefert die statische Seite der aktuellen Anfrage.
 *
 * @return array|null Eintrag aus TOURNEY_STATIC_PAGES ergänzt um 'slug', oder null.
 */
function tourney_static_current() {
    $slug = get_query_var('tourney_static');
    return ($slug && isset(TOURNEY_STATIC_PAGES[$slug])) ? TOURNEY_STATIC_PAGES[$slug] + ['slug' => $slug] : null;
}

/**
 * Registriert die Rewrite-Regel und schreibt die Regeln neu, wenn sich die Slug-Liste geändert hat
 * (gemerkt in Option tourney_static_rules; flush_rewrite_rules ist teuer und läuft daher nur einmal).
 *
 * @return void
 */
function tourney_static_rewrite() {
    $slugs = implode('|', array_keys(TOURNEY_STATIC_PAGES));
    add_rewrite_rule("^($slugs)/?$", 'index.php?tourney_static=$matches[1]', 'top');
    if (get_option('tourney_static_rules') !== $slugs) {
        flush_rewrite_rules(false);
        update_option('tourney_static_rules', $slugs);
    }
}

/**
 * Verhindert die 404-Behandlung für statische Seiten (kein WordPress-Beitrag passt zur Anfrage).
 *
 * @param bool $preempt Bisheriger Wert.
 * @return bool True für statische Seiten.
 */
function tourney_static_pre_404($preempt) {
    return tourney_static_current() ? true : $preempt;
}

/**
 * Erzeugt das HTML einer statischen Seite für #Main_ContentId, im Aufbau wie MainView/PageContent.scala.html.
 *
 * @param array $page Eintrag aus tourney_static_current().
 * @return string HTML oder leerer String, wenn die Datei fehlt.
 */
function tourney_static_html($page) {
    $file = TOURNEY_PLUGIN_PATH . 'pages/' . $page['file'] . '.html';
    if (!file_exists($file)) {
        return '';
    }
    return '<div class="container mt-4 mb-5"><div class="card shadow-lg border-0 overflow-hidden">'
         . '<div class="card-header bg-white border-0 pt-4 px-md-5"><h1 class="display-5 fw-bold text-primary mb-0">' . esc_html($page['title']) . '</h1></div>'
         . '<div class="card-body p-md-5 pt-3"><div class="wp-content-body lh-lg">' . file_get_contents($file) . '</div></div>'
         . '</div></div>';
}

/**
 * Seitentitel der statischen Seiten ("Impressum – Website").
 *
 * @param array $parts Titelbestandteile von WordPress.
 * @return array Angepasste Titelbestandteile.
 */
function tourney_static_title_parts($parts) {
    $page = tourney_static_current();
    return $page ? ['title' => $page['title'], 'site' => get_bloginfo('name')] : $parts;
}

/**
 * Registriert den Sitemap-Provider für die statischen Seiten (wp-sitemap-tourneystatic-1.xml).
 *
 * @return void
 */
function tourney_static_sitemap() {
    if (!class_exists('WP_Sitemaps_Provider')) {
        return;
    }
    /** Sitemap der statischen Seiten; lastmod ist das Änderungsdatum der Datei. */
    $provider = new class extends WP_Sitemaps_Provider {
        public function __construct() {
            $this->name        = 'tourneystatic';
            $this->object_type = 'tourneystatic';
        }
        public function get_url_list($page_num, $object_subtype = '') {
            $list = [];
            foreach (TOURNEY_STATIC_PAGES as $slug => $page) {
                $file  = TOURNEY_PLUGIN_PATH . 'pages/' . $page['file'] . '.html';
                $entry = ['loc' => home_url("/$slug/")];
                if (file_exists($file)) {
                    $entry['lastmod'] = gmdate('c', filemtime($file));
                }
                $list[] = $entry;
            }
            return $list;
        }
        public function get_max_num_pages($object_subtype = '') {
            return 1;
        }
    };
    wp_register_sitemap_provider('tourneystatic', $provider);
}

if (TOURNEY_FWS) {
    add_action('init', 'tourney_static_rewrite');
    add_filter('query_vars', fn($vars) => array_merge($vars, ['tourney_static']));
    add_filter('pre_handle_404', 'tourney_static_pre_404');
    add_filter('redirect_canonical', fn($redirect) => tourney_static_current() ? false : $redirect);
    add_filter('document_title_parts', 'tourney_static_title_parts', 20);
    add_action('init', 'tourney_static_sitemap');
}
