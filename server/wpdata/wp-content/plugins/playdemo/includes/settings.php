<?php

/**
 * Registriert die Einstellungen, Abschnitte und Felder für das Plugin.
 */
function playdemo_register_settings() {
    // Fügt eine neue Seite unter "Einstellungen" im Admin-Menü hinzu.
    add_options_page(
        'Playdemo-Einstellungen',         // Titel der HTML-Seite
        'Playdemo-Konfigurator',          // Titel im Admin-Menü
        'manage_options',                 // Erforderliche Benutzerrolle ('Administrator')
        'playdemo_configurator',          // Slug (URL-Identifikator) der Einstellungsseite
        'playdemo_settings_page_callback' // Funktion, die das HTML der Seite rendert
    );

    // Registriert die Einstellungen in der WordPress-Datenbank.
    // Die erste Zeichenkette ist der 'option_group'-Name.
    // Die zweite ist der 'option_name'.
    // Der dritte ist der 'sanitize_callback' (optional, aber empfohlen).

    // App-Name: Einfacher Text, wird sanft bereinigt.
    register_setting(
        'mac_settings_group',             // Optionsgruppe
        'playdemo_url',                   // Optionsname
        array(                            // Array für zusätzliche Argumente (für Sanitisierung/REST API)
            'type'         => 'string',   // Datentyp der Option
            'default'      => '',         // Standardwert
            'sanitize_callback' => 'sanitize_text_field', // Grundlegende Textbereinigung
            'show_in_rest' => true,       // Optional: Macht die Option in der REST API sichtbar
            'description'  => 'Adresse des Playdemoserver'
        )
    );


    // App-Name: Einfacher Text, wird sanft bereinigt.
    register_setting(
        'mac_settings_group',             // Optionsgruppe
        'playdemo_user_api_pw',           // Optionsname
        array(                            // Array für zusätzliche Argumente (für Sanitisierung/REST API)
            'type'         => 'string',   // Datentyp der Option
            'default'      => '',         // Standardwert
            'sanitize_callback' => 'sanitize_text_field', // Grundlegende Textbereinigung
            'show_in_rest' => true,       // Optional: Macht die Option in der REST API sichtbar
            'description'  => 'Username der Anwendungspasswort einrichet.'
        )
    );

    // API-Passwort: Sensibler Wert, wird spezieller bereinigt und NICHT in der REST API angezeigt.
    register_setting(
        'mac_settings_group',
        'playdemo_api_password',
        array(
            'type'         => 'string',
            'default'      => '',
            'sanitize_callback' => 'mac_sanitize_api_password', // Spezieller Callback für Passwörter
            'show_in_rest' => false,      // WICHTIG: NICHT in der REST API anzeigen!
            'description'  => 'Das geheime API-Passwort für die Authentifizierung.'
        )
    );



    // Fügt einen Einstellungsabschnitt zur Einstellungsseite hinzu.
    add_settings_section(
        'playdemo_main_section',        // ID des Abschnitts
        'Allgemeine App-Einstellungen', // Titel des Abschnitts
        'mac_section_callback',         // Callback-Funktion für den Beschreibungstext des Abschnitts
        'playdemo_configurator'         // Slug der Seite, zu der der Abschnitt gehört
    );

    // Fügt das Eingabefeld für den Playdemo URL hinzu.
    add_settings_field(
        'playdemo_field_url',         // ID des Feldes
        'Playdemo URL',                   // Beschriftung des Feldes
        'playdemo_field_url_callback',// Funktion zum Rendern des HTML-Feldes
        'playdemo_configurator',        // Slug der Seite
        'playdemo_main_section'            // ID des Abschnitts, zu dem das Feld gehört
    );

    // Fügt das Eingabefeld für den App-Namen hinzu.
    add_settings_field(
        'mac_field_app_name',                       // ID des Feldes
        'Username',                                 // Beschriftung des Feldes
        'mac_field_app_name_callback',              // Funktion zum Rendern des HTML-Feldes
        'playdemo_configurator',                    // Slug der Seite
        'playdemo_main_section'                     // ID des Abschnitts, zu dem das Feld gehört
    );

    // Fügt das Eingabefeld für das API-Passwort hinzu.
    add_settings_field(
        'mac_field_api_password',          // ID des Feldes
        'API-Password',                    // Beschriftung des Feldes
        'mac_field_api_password_callback', // Funktion zum Rendern des HTML-Feldes
        'playdemo_configurator',           // Slug der Seite
        'playdemo_main_section'            // ID des Abschnitts
    );
}

/**
 * Rendert die Haupt-Einstellungsseite des Plugins.
 */
function playdemo_settings_page_callback() {
    ?>
    <div class="wrap">
        <h1>Playdemo-Konfigurator Einstellungen</h1>
        <form method="post" action="options.php">
            <?php
            // Fügt die notwendigen Sicherheitsfelder für die Optionsgruppe hinzu.
            settings_fields('mac_settings_group');
            // Rendert alle registrierten Abschnitte und Felder für diese Seite.
            do_settings_sections('playdemo_configurator');
            // Zeigt den Speichern-Button an.
            submit_button();
            ?>
        </form>
    </div>
    <?php
}

/**
 * Callback-Funktion für den Einstellungsabschnitt.
 */
function mac_section_callback() {
    echo '<p>Konfigurieren Sie die grundlegenden Informationen für Ihre Anwendung.</p>';
}

/**
 * Rendert das Eingabefeld für die Playdemo Server URL.
 */
function playdemo_field_url_callback() {
    $pdemo_url = get_option('playdemo_url', ''); // Wert auslesen, Standard ist leer.
    ?>
    <input type="text" id="playdemo_url" name="playdemo_url" value="<?php echo esc_attr($pdemo_url); ?>" class="regular-text">
    <p class="description">Geben Sie die URL für den Playdemo-Server ein.</p>
    <?php
}

/**
 * Rendert das Eingabefeld für die Playdemo Server URL.
 */
function playdemo_field_url_callback() {
    $pdemo_url = get_option('playdemo_url', ''); // Wert auslesen, Standard ist leer.
    ?>
    <input type="text" id="playdemo_url" name="playdemo_url" value="<?php echo esc_attr($pdemo_url); ?>" class="regular-text">
    <p class="description">Geben Sie die URL für den Playdemo-Server ein.</p>
    <?php
}


/**
 * Rendert das Eingabefeld für das API-Passwort.
 * Der gespeicherte Wert wird aus Sicherheitsgründen niemals angezeigt.
 */
function mac_field_api_password_callback() {
    // Prüfen, ob bereits ein Passwort gesetzt ist, um den Placeholder anzupassen.
    $has_password = ! empty( get_option( 'playdemo_api_password', '' ) );
    ?>
    <input type="password" id="playdemo_api_password" name="playdemo_api_password" value="" class="regular-text"
           placeholder="<?php echo $has_password ? esc_attr__('Passwort gesetzt (neu eingeben zum Ändern)', 'playdemo_configurator') : ''; ?>">
    <p class="description">Geben Sie hier das API-Passwort ein. Es wird aus Sicherheitsgründen nicht angezeigt.</p>
    <?php
    if ( $has_password ) {
        echo '<p class="description">Ein Passwort ist bereits gespeichert. Geben Sie ein neues ein, um es zu überschreiben.</p>';
    }
}


/**
 * Sanitisierungs-Callback für das API-Passwort.
 * Wenn das Feld leer ist, wird das bestehende Passwort beibehalten.
 * Andernfalls wird der neue Wert bereinigt und gespeichert.
 */
function mac_sanitize_api_password( $new_value ) {
    $old_value = get_option( 'playdemo_api_password' );

    // Wenn der neue Wert leer ist, den alten Wert beibehalten.
    if ( empty( $new_value ) ) {
        return $old_value;
    }

    // Andernfalls den neuen Wert bereinigen.
    // ACHTUNG: Wenn dies ein echtes Benutzerpasswort ist, MUSS es hier gehasht werden!
    // Für API-Schlüssel, die im Klartext benötigt werden, ist sanitize_text_field in Ordnung,
    // aber seien Sie sich der Implikationen der Klartextspeicherung bewusst.
    return sanitize_text_field( $new_value );
}

add_action('admin_menu', 'playdemo_register_settings');