<?php

/**
 * Sprachfassungen der Website-Variante (TOURNEY_FWS = true): Deutsch unter /…, Englisch unter /en/….
 *
 * Die englische Startseite /en/ und die englischen statischen Seiten (/en/features/ …, includes/static-pages.php)
 * werden per Rewrite-Regel abgebildet, ohne WordPress-Seiten. Die Sprache kommt aus der URL (Query-Variable
 * tourney_lang) und wird dem Client über data-lang übergeben; dazu <html lang>, hreflang-Verweise und og:locale.
 */

/**
 * Sprache der aktuellen Anfrage.
 *
 * @return string 'en' für /en/…, sonst 'de'.
 */
function tourney_lang() {
    return get_query_var('tourney_lang') === 'en' ? 'en' : 'de';
}

/**
 * Prüft, ob die englische Startseite (/en/) angefragt wurde.
 *
 * @return bool True für /en/.
 */
function tourney_is_en_home() {
    return tourney_lang() === 'en' && !get_query_var('tourney_static');
}

/**
 * Registriert die Rewrite-Regel für die englische Startseite.
 *
 * @return void
 */
function tourney_lang_rewrite() {
    add_rewrite_rule('^en/?$', 'index.php?tourney_lang=en', 'top');
    if (get_option('tourney_lang_rules') !== '1') {
        flush_rewrite_rules(false);
        update_option('tourney_lang_rules', '1');
    }
}

/**
 * Liefert die URLs beider Sprachfassungen der aktuellen Seite (Startseite oder statische Seite).
 *
 * @return array{de:string,en:string}|null URLs je Sprache oder null, wenn es keine englische Fassung gibt.
 */
function tourney_lang_alternates() {
    $static = tourney_static_current();
    if ($static) {
        return ['de' => tourney_static_url($static['de'], 'de'), 'en' => tourney_static_url($static['de'], 'en')];
    }
    if (is_front_page() || tourney_is_en_home()) {
        return ['de' => home_url('/'), 'en' => home_url('/en/')];
    }
    return null;
}

/**
 * Gibt die hreflang-Verweise auf beide Sprachfassungen aus (x-default ist Deutsch).
 *
 * @return void
 */
function tourney_lang_hreflang() {
    $alt = tourney_lang_alternates();
    if (!$alt) {
        return;
    }
    foreach (['de' => $alt['de'], 'en' => $alt['en'], 'x-default' => $alt['de']] as $lang => $url) {
        echo '<link rel="alternate" hreflang="' . $lang . '" href="' . esc_url($url) . '" />' . "\n";
    }
}

/**
 * Setzt <html lang> auf Englisch für die englischen URLs.
 *
 * @param string $output Attribute von language_attributes().
 * @return string Angepasste Attribute.
 */
function tourney_lang_attributes($output) {
    return tourney_lang() === 'en' ? preg_replace('/lang="[^"]*"/', 'lang="en-GB"', $output) : $output;
}

if (TOURNEY_FWS) {
    add_action('init', 'tourney_lang_rewrite');
    add_filter('query_vars', fn($vars) => array_merge($vars, ['tourney_lang']));
    add_filter('pre_handle_404', fn($preempt) => tourney_is_en_home() ? true : $preempt);
    add_filter('redirect_canonical', fn($redirect) => get_query_var('tourney_lang') ? false : $redirect);
    add_filter('language_attributes', 'tourney_lang_attributes');
    add_action('wp_head', 'tourney_lang_hreflang', 3);
}
