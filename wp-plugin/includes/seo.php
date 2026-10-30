<?php

/**
 * Suchmaschinen-Optimierung der Website-Variante (TOURNEY_FWS = true).
 *
 * - Seitentitel, Meta-Description, Open Graph, Twitter Card und JSON-LD im <head>
 *   (die Seiten werden vom Scala.js-Client erzeugt, Crawler und Link-Vorschauen sehen nur das Server-HTML)
 * - noindex und Ausschluss aus der Sitemap für Seiten ohne Nutzen für Suchende
 * - keine Benutzer-Sitemap, keine Autorenarchive und keine öffentliche Benutzerliste (verraten Anmeldenamen)
 *
 * Im eingebetteten Betrieb (TOURNEY_FWS = false) bleibt die einbettende Website unberührt (kein Eingriff in
 * <head>, Sitemap, Autorenarchive oder REST-API).
 */

/** Seiten-Slugs, die nicht in Suchmaschinen erscheinen sollen (inkl. Unterseiten). */
const TOURNEY_SEO_NOINDEX_SLUGS = ['login', 'registration', 'verification', 'template', 'statistik'];

/** Beschreibung der Startseite (Meta-Description, Open Graph, JSON-LD). */
const TOURNEY_SEO_HOME_DESCRIPTION = 'Tischtennis-Turniere einfach im Browser organisieren: K.o.-System, Gruppen und Schweizer System frei kombinierbar, lokal dauerhaft kostenlos, in der Cloud mit Spieler-Self-Service und Live-Anzeige.';

/**
 * Liefert die IDs aller Seiten, die nicht indexiert werden sollen (TOURNEY_SEO_NOINDEX_SLUGS samt Unterseiten).
 *
 * @return int[] Seiten-IDs.
 */
function tourney_seo_noindex_ids() {
    static $ids = null;
    if ($ids !== null) {
        return $ids;
    }
    $ids = [];
    foreach (TOURNEY_SEO_NOINDEX_SLUGS as $slug) {
        $page = get_page_by_path($slug, OBJECT, 'page');
        if ($page) {
            $ids[] = $page->ID;
            foreach (get_pages(['child_of' => $page->ID, 'post_status' => 'publish,private']) as $child) {
                $ids[] = $child->ID;
            }
        }
    }
    return $ids = array_values(array_unique($ids));
}

/**
 * Liest die Grunddaten eines Turniers (Post-Typ tourney) für Titel und Beschreibung.
 *
 * @param int $post_id Turnier-Post-ID.
 * @return array{name:string,organizer:string,start:string,end:string,competitions:string[]} Grunddaten.
 */
function tourney_seo_tourney_data($post_id) {
    $toDate = function ($ymd) {
        $ymd = (string) $ymd;
        return strlen($ymd) === 8 ? substr($ymd, 6, 2) . '.' . substr($ymd, 4, 2) . '.' . substr($ymd, 0, 4) : '';
    };
    $competitions = [];
    for ($i = 1; $i <= 64; $i++) {
        $json = get_post_meta($post_id, sprintf('competition%02d', $i), true);
        if (!$json) {
            continue;
        }
        $comp = json_decode($json, true);
        if (is_array($comp) && !empty($comp['name']) && empty($comp['deleted'])) {
            $competitions[] = $comp['name'];
        }
    }
    return [
        'name'         => get_the_title($post_id),
        'organizer'    => (string) get_post_meta($post_id, 'organizer', true),
        'start'        => $toDate(get_post_meta($post_id, 'startDate', true)),
        'end'          => $toDate(get_post_meta($post_id, 'endDate', true)),
        'competitions' => $competitions,
    ];
}

/**
 * Seitentitel: Startseite mit Kurzbeschreibung, sonst WordPress-Standard ("Seite – Website").
 *
 * @param array $parts Titelbestandteile von WordPress.
 * @return array Angepasste Titelbestandteile.
 */
function tourney_seo_title_parts($parts) {
    if (is_front_page()) {
        $parts = ['title' => 'Tischtennis-Turniere einfach im Browser organisieren', 'site' => get_bloginfo('name')];
    }
    return $parts;
}

/**
 * Prüft, ob die aktuelle Seite nicht indexiert werden soll (Slug-Liste oder Statistik-Shortcode).
 *
 * @return bool True für noindex-Seiten.
 */
function tourney_seo_is_noindex() {
    if (!is_singular()) {
        return false;
    }
    $post = get_queried_object();
    return in_array($post->ID, tourney_seo_noindex_ids(), true) || has_shortcode($post->post_content, 'tourney_stats');
}

/**
 * Gibt Meta-Description, Open Graph, Twitter Card und JSON-LD für die aktuelle Seite aus.
 *
 * @return void
 */
function tourney_seo_head() {
    if (is_404() || tourney_seo_is_noindex()) {
        return;
    }
    $site  = get_bloginfo('name');
    $url   = is_singular() ? get_permalink() : home_url('/');
    $title = wp_get_document_title();
    $type  = 'website';
    $desc  = TOURNEY_SEO_HOME_DESCRIPTION;
    $jsonld = [];

    if (is_singular('tourney')) {
        $t     = tourney_seo_tourney_data(get_queried_object_id());
        $type  = 'article';
        $date  = $t['start'] && $t['end'] && $t['start'] !== $t['end'] ? $t['start'] . ' – ' . $t['end'] : $t['start'];
        $desc  = $t['name'] . ($date ? " am $date" : '') . ($t['organizer'] ? ', Veranstalter ' . $t['organizer'] : '') . '.';
        if ($t['competitions']) {
            $desc .= ' Wettbewerbe: ' . implode(', ', $t['competitions']) . '.';
        }
        $desc .= ' Auslosung und Ergebnisse live bei TurnierService.';
    } elseif (is_front_page()) {
        $sameAs = array_values(array_filter([getenv('SOCIAL_FACEBOOK_URL'), getenv('SOCIAL_X_URL'), getenv('SOCIAL_INSTAGRAM_URL')]));
        $jsonld[] = [
            '@context'            => 'https://schema.org',
            '@type'               => 'WebApplication',
            'name'                => 'TurnierService',
            'url'                 => home_url('/'),
            'description'         => TOURNEY_SEO_HOME_DESCRIPTION,
            'applicationCategory' => 'SportsApplication',
            'operatingSystem'     => 'Web',
            'inLanguage'          => ['de', 'en'],
            'offers'              => ['@type' => 'Offer', 'price' => '0', 'priceCurrency' => 'EUR'],
        ];
        $jsonld[] = array_filter([
            '@context' => 'https://schema.org',
            '@type'    => 'Organization',
            'name'     => $site,
            'url'      => home_url('/'),
            'logo'     => plugins_url('img/logo.png', TOURNEY_PLUGIN_PATH . 'tourney.php'),
            'sameAs'   => $sameAs ?: null,
        ]);
    }
    $desc  = wp_strip_all_tags($desc);
    $desc  = mb_strlen($desc) > 160 ? mb_substr($desc, 0, 157) . '…' : $desc;
    $image = plugins_url('img/og-image.jpg', TOURNEY_PLUGIN_PATH . 'tourney.php');

    $meta = [
        ['name', 'description', $desc],
        ['property', 'og:type', $type],
        ['property', 'og:site_name', $site],
        ['property', 'og:locale', 'de_DE'],
        ['property', 'og:title', $title],
        ['property', 'og:description', $desc],
        ['property', 'og:url', $url],
        ['property', 'og:image', $image],
        ['property', 'og:image:width', '1200'],
        ['property', 'og:image:height', '630'],
        ['name', 'twitter:card', 'summary_large_image'],
        ['name', 'twitter:title', $title],
        ['name', 'twitter:description', $desc],
        ['name', 'twitter:image', $image],
    ];
    foreach ($meta as [$attr, $key, $value]) {
        echo '<meta ' . $attr . '="' . esc_attr($key) . '" content="' . esc_attr($value) . '" />' . "\n";
    }
    foreach ($jsonld as $data) {
        echo '<script type="application/ld+json">' . wp_json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . '</script>' . "\n";
    }
}

/**
 * Setzt noindex für Seiten ohne Nutzen für Suchende (Anmeldung, Registrierung, Vorlagen, Statistik).
 *
 * @param array $robots Robots-Direktiven von WordPress.
 * @return array Angepasste Direktiven.
 */
function tourney_seo_robots($robots) {
    if (tourney_seo_is_noindex()) {
        $robots['noindex'] = true;
        $robots['follow']  = true;
    }
    return $robots;
}

/**
 * Nimmt die noindex-Seiten aus der Sitemap.
 *
 * @param array  $args      WP_Query-Argumente der Sitemap.
 * @param string $post_type Post-Typ der Sitemap.
 * @return array Angepasste Argumente.
 */
function tourney_seo_sitemap_query($args, $post_type) {
    if ($post_type === 'page') {
        $args['post__not_in'] = array_merge($args['post__not_in'] ?? [], tourney_seo_noindex_ids());
    }
    return $args;
}

/**
 * Leitet Autorenarchive (auch ?author=N) auf die Startseite um, damit keine Anmeldenamen sichtbar werden.
 *
 * @return void
 */
function tourney_seo_redirect_author() {
    if (is_author()) {
        wp_safe_redirect(home_url('/'), 301);
        exit;
    }
}

/**
 * Entfernt die öffentliche Benutzerliste der REST-API (/wp/v2/users) für nicht angemeldete Besucher.
 *
 * @param array $endpoints Registrierte REST-Endpunkte.
 * @return array Endpunkte ohne Benutzer-Routen.
 */
function tourney_seo_rest_endpoints($endpoints) {
    if (!is_user_logged_in()) {
        unset($endpoints['/wp/v2/users'], $endpoints['/wp/v2/users/(?P<id>[\d]+)']);
    }
    return $endpoints;
}

if (TOURNEY_FWS) {
    add_filter('document_title_parts', 'tourney_seo_title_parts');
    add_action('wp_head', 'tourney_seo_head', 2);
    add_filter('wp_robots', 'tourney_seo_robots');
    add_filter('wp_sitemaps_posts_query_args', 'tourney_seo_sitemap_query', 10, 2);
    add_filter('wp_sitemaps_add_provider', fn($provider, $name) => $name === 'users' ? false : $provider, 10, 2);
    add_action('template_redirect', 'tourney_seo_redirect_author', 1); // vor redirect_canonical, das ?author=N sonst auf /author/<name>/ umleitet
    add_filter('rest_endpoints', 'tourney_seo_rest_endpoints');
}
