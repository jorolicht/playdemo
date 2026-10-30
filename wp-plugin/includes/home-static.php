<?php

/**
 * Serverseitige Fassung der Startseite (Progressive Enhancement, SEO).
 *
 * Statt "Wird geladen..." liefert das Plugin auf der Startseite der Website-Variante schon im HTML die Inhalte von
 * MainView (Überschrift, Beschreibung, die vier Karten). Der Scala.js-Client ersetzt den Inhalt von #Main_ContentId
 * beim Start wie bisher durch die vollständige Startseite (client/src/main/twirl/cviews/pages/MainView/Base.scala.html).
 *
 * Die Texte stammen aus derselben Quelle wie im Client (data/msgs_de.json, erzeugt aus server/conf/messages/de),
 * damit Server- und Client-Inhalt übereinstimmen (kein Cloaking).
 */

/**
 * Liest die deutschen Client-Texte (einmal pro Anfrage).
 *
 * @return array<string,string> Message-Key => Text; leer, wenn die Datei fehlt.
 */
function tourney_home_static_msgs() {
    static $msgs = null;
    if ($msgs === null) {
        $file = TOURNEY_PLUGIN_PATH . 'data/msgs_de.json';
        $msgs = file_exists($file) ? (json_decode(file_get_contents($file), true) ?: []) : [];
    }
    return $msgs;
}

/**
 * Erzeugt das HTML der Startseite für #Main_ContentId.
 *
 * @return string HTML der Startseite oder leerer String, wenn die Texte fehlen.
 */
function tourney_home_static_html() {
    $m = tourney_home_static_msgs();
    if (empty($m['mainView.heroTitle'])) {
        return '';
    }
    $t = fn($key) => esc_html($m[$key] ?? '');

    // Karten wie in Base.scala.html: [Titel, Beschreibung, Button, Icon, Farbklassen Icon, Button-Klasse]
    $cards = [
        ['mainView.demoTitle',       'mainView.demoDesc',       'mainView.demoBtn',       'bi-play-circle',      'bg-warning text-dark', 'btn-warning text-dark'],
        ['mainView.newTourneyTitle', 'mainView.newTourneyDesc', 'mainView.newTourneyBtn', 'bi-trophy',           'bg-primary text-white', 'btn-primary'],
        ['mainView.quickStartTitle', 'mainView.quickStartDesc', 'mainView.quickStartBtn', 'bi-lightning-charge', 'bg-success text-white', 'btn-success'],
        ['mainView.searchTitle',     'mainView.searchDesc',     'mainView.searchBtn',     'bi-search',           'bg-info text-white',    'btn-info text-white'],
    ];

    $html  = '<div class="main-multi-landing">';
    $html .= '<div class="hero-section text-white d-flex align-items-center justify-content-center text-center shadow-sm" style="min-height:18vh;background-color:#336699;">';
    $html .= '<div class="hero-content px-3 py-3">';
    $html .= '<h1 class="h2 fw-bold mb-1">' . $t('mainView.heroTitle') . '</h1>';
    $html .= '<p class="lead mb-1 fs-6">' . $t('mainView.heroSubtitle') . '</p>';
    $html .= '<p class="small opacity-75 mx-auto mb-0" style="max-width:600px;">' . $t('mainView.heroDesc') . '</p>';
    $html .= '</div></div>';
    $html .= '<div class="container mt-4 pb-5"><div class="row g-4 justify-content-center">';
    foreach ($cards as [$title, $desc, $btn, $icon, $iconClass, $btnClass]) {
        $html .= '<div class="col-lg-3 col-md-6"><div class="card h-100 border-0 shadow"><div class="card-body text-center p-4 d-flex flex-column">';
        $html .= '<div class="' . $iconClass . ' bg-gradient mb-3 mx-auto rounded-circle d-flex align-items-center justify-content-center" style="width:60px;height:60px;"><i class="bi ' . $icon . ' fs-3"></i></div>';
        $html .= '<h3 class="h4 mb-3">' . $t($title) . '</h3>';
        $html .= '<p class="text-muted small">' . $t($desc) . '</p>';
        // Ohne JavaScript ohne Funktion: nur als Platzhalter, damit sich das Layout beim Start nicht verschiebt
        $html .= '<span class="btn ' . $btnClass . ' mt-auto fw-bold disabled" aria-hidden="true">' . $t($btn) . '</span>';
        $html .= '</div></div></div>';
    }
    $html .= '</div></div></div>';
    return $html;
}
