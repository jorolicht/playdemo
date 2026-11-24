<?php
/**
 * Warteliste der Online-Anmeldung: Mails an Spieler.
 *
 * - Bestätigung nach der öffentlichen Anmeldung (tourney_public_register), fester Platz oder Platz auf der Warteliste.
 * - Mail an Nachrücker: Der Client lässt Spieler der Warteliste nachrücken (Competition.promoteWaiting, z. B. nach
 *   einer Abmeldung oder höherer Höchstzahl) und ruft danach POST /waitlist-notify auf.
 *
 * Mails gehen nur an Spieler, die bei der Anmeldung eine E-Mail-Adresse angegeben haben.
 */

add_action('rest_api_init', function () {

    // POST /tourney/v1/waitlist-notify?postId=<id>  Body {"compId": n, "playerIds": [..], "lang": "de"|"en"}
    register_rest_route('tourney/v1', '/waitlist-notify', [
        'methods'             => 'POST',
        'callback'            => 'tourney_waitlist_notify',
        'permission_callback' => 'tourney_api_can_edit_request_post'
    ]);

});

/**
 * Schickt eine Mail zur Anmeldung an einen Spieler.
 *
 * @param string $to        E-Mail-Adresse.
 * @param string $kind      "registered" (fester Platz), "waitlist" (auf der Warteliste) oder "promoted" (nachgerückt).
 * @param int    $post_id   Post-ID des Turniers.
 * @param string $comp_name Name des Wettbewerbs.
 * @param string $player    Vorname des Spielers.
 * @param int    $position  Platz auf der Warteliste (nur "waitlist").
 * @param string $lang      "de" oder "en".
 * @return bool true, wenn wp_mail() die Mail angenommen hat.
 */
function tourney_registration_mail($to, $kind, $post_id, $comp_name, $player, $position = 0, $lang = 'de') {
    if (!is_email($to)) {
        return false;
    }
    $en      = $lang === 'en';
    $tourney = html_entity_decode(get_the_title($post_id), ENT_QUOTES, 'UTF-8');
    $url     = get_permalink($post_id);
    $aci     = json_decode(get_post_meta($post_id, 'aci', true) ?: '{}', true);
    $hint    = trim(is_array($aci) && isset($aci['registrationHint']) ? strval($aci['registrationHint'])
        : ($en ? 'Please report to the tournament desk at least 30 minutes before the competition starts to secure your place.'
               : 'Bitte spätestens 30 Min vor Beginn des Wettbewerbs bei der Turnierleitung melden um Teilnahme zu gewährleisten.'));

    switch ($kind) {
        case 'waitlist':
            $subject = $en ? "Waiting list: $comp_name – $tourney" : "Warteliste: $comp_name – $tourney";
            $text = $en
                ? "the competition \"$comp_name\" is full. You are on the waiting list at position $position. "
                  . "If a place becomes available, you will move up automatically and receive another email."
                : "der Wettbewerb „{$comp_name}“ ist ausgebucht. Sie stehen auf Platz $position der Warteliste. "
                  . "Wird ein Platz frei, rücken Sie automatisch nach und erhalten eine weitere E-Mail.";
            $hint = '';
            break;
        case 'promoted':
            $subject = $en ? "You have moved up: $comp_name – $tourney" : "Nachgerückt: $comp_name – $tourney";
            $text = $en
                ? "a place has become available: you have moved up from the waiting list and are now registered for \"$comp_name\"."
                : "ein Platz ist frei geworden: Sie sind von der Warteliste nachgerückt und jetzt für „{$comp_name}“ angemeldet.";
            break;
        default:
            $subject = $en ? "Registration confirmed: $comp_name – $tourney" : "Anmeldung bestätigt: $comp_name – $tourney";
            $text = $en
                ? "thank you for registering. You are registered for \"$comp_name\"."
                : "vielen Dank für Ihre Anmeldung. Sie sind für „{$comp_name}“ angemeldet.";
    }

    $html  = '<p>' . esc_html(($en ? 'Hello ' : 'Guten Tag ') . $player . ',') . '</p>';
    $html .= '<p>' . esc_html($text) . '</p>';
    if ($hint !== '') {
        $html .= '<p><strong>' . esc_html($hint) . '</strong></p>';
    }
    $html .= '<p>' . esc_html($en ? 'Tournament: ' : 'Turnier: ') . '<a href="' . esc_url($url) . '">' . esc_html($tourney) . '</a></p>';
    $html .= '<p style="color:#888;font-size:12px">' . esc_html($en
        ? 'You receive this email because this address was entered when registering online. If that was not you, please contact the organiser.'
        : 'Sie erhalten diese E-Mail, weil bei der Online-Anmeldung diese Adresse angegeben wurde. Waren Sie das nicht, wenden Sie sich bitte an den Veranstalter.') . '</p>';

    return wp_mail($to, $subject, $html, ['Content-Type: text/html; charset=UTF-8']);
}

/**
 * Benachrichtigt nachgerückte Spieler per Mail.
 *
 * Geprüft wird, dass die Spieler im Wettbewerb gemeldet sind; Spieler ohne E-Mail-Adresse werden übersprungen.
 *
 * @param WP_REST_Request $request Parameter postId; Body compId, playerIds, lang.
 * @return array|WP_REST_Response ["sent" => Anzahl versendeter Mails] oder Fehler.
 */
function tourney_waitlist_notify(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    $body    = json_decode($request->get_body(), true);
    $comp_id = intval(is_array($body) ? ($body['compId'] ?? 0) : 0);
    $ids     = is_array($body['playerIds'] ?? null) ? array_map('intval', $body['playerIds']) : [];
    $lang    = ($body['lang'] ?? '') === 'en' ? 'en' : 'de';
    if (!$post_id || $comp_id < 1 || $comp_id > 64) {
        return ApiHelper::error("missing_param", "Post ID or competition missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $comp = json_decode(get_post_meta($post_id, sprintf('competition%02d', $comp_id), true) ?: '', true);
    if (!is_array($comp)) {
        return ApiHelper::error("not_found", "Competition not found", "", "tourney_waitlist_notify", HttpStatus::NOT_FOUND);
    }
    $snos = array_map(fn($p) => $p['id'] ?? '', $comp['pants1Stage'] ?? []);

    $players = json_decode(get_post_meta($post_id, 'players', true) ?: '[]', true) ?: [];
    $sent = 0;
    foreach ($players as $p) {
        $id = intval($p['id'] ?? 0);
        if (!in_array($id, $ids, true) || empty($p['email']) || !in_array("[$id]", $snos, true)) {
            continue;
        }
        if (tourney_registration_mail($p['email'], 'promoted', $post_id, $comp['name'] ?? '', $p['firstName'] ?? '', 0, $lang)) {
            $sent++;
        }
    }
    return ["sent" => $sent];
}
