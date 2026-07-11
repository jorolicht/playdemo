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

    // WordPress-Username: Einfacher Text
    register_setting(
        'mac_settings_group',
        'wordpress_user',
        array(
            'type'         => 'string',
            'default'      => '',
            'sanitize_callback' => 'sanitize_text_field',
            'show_in_rest' => true,
            'description'  => 'Username der den API Zugriff erlaubt.'
        )
    );

    // API-Passwort: Sensibler Wert, wird spezieller bereinigt und NICHT in der REST API angezeigt.
    register_setting(
        'mac_settings_group',
        'api_password',
        array(
            'type'         => 'string',
            'default'      => '',
            'sanitize_callback' => 'mac_sanitize_api_password',
            'show_in_rest' => false,
            'description'  => 'Das geheime API-Passwort für die Authentifizierung.'
        )
    );

    // Turnierserver-URL: URL des Play-Backends
    register_setting(
        'mac_settings_group',
        'tourney_server_url',
        array(
            'type'         => 'string',
            'default'      => '',
            'sanitize_callback' => 'esc_url_raw',
            'show_in_rest' => true,
            'description'  => 'Die URL des Turnierservers (Play-Backend).'
        )
    );

    // Fügt einen Einstellungsabschnitt zur Einstellungsseite hinzu.
    add_settings_section(
        'playdemo_main_section',        // ID des Abschnitts
        'Allgemeine App-Einstellungen', // Titel des Abschnitts
        'mac_section_callback',         // Callback-Funktion für den Beschreibungstext des Abschnitts
        'playdemo_configurator'         // Slug der Seite, zu der der Abschnitt gehört
    );

    // Fügt das Eingabefeld für den WP-Username hinzu.
    add_settings_field(
        'mac_field_wordpress_user',
        'Username',
        'mac_field_wordpress_user_callback',
        'playdemo_configurator',
        'playdemo_main_section'
    );

    // Fügt das Eingabefeld für das API-Passwort hinzu.
    add_settings_field(
        'mac_field_api_password',
        'API-Password',
        'mac_field_api_password_callback',
        'playdemo_configurator',
        'playdemo_main_section'
    );

    // Fügt das Eingabefeld für die Turnierserver-URL hinzu.
    add_settings_field(
        'mac_field_tourney_server_url',
        'Turnierserver-URL',
        'mac_field_tourney_server_url_callback',
        'playdemo_configurator',
        'playdemo_main_section'
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
 * Rendert das Eingabefeld für den WP-Usernamen.
 */
function mac_field_wordpress_user_callback() {
    $value = get_option('wordpress_user', '');
    ?>
    <input type="text" id="wordpress_user" name="wordpress_user" value="<?php echo esc_attr($value); ?>" class="regular-text">
    <p class="description">Geben Sie hier den WordPress-Benutzernamen für API-Anfragen ein.</p>
    <?php
}

/**
 * Rendert das Eingabefeld für das API-Passwort.
 */
function mac_field_api_password_callback() {
    $has_password = ! empty( get_option( 'api_password', '' ) );
    ?>
    <input type="password" id="api_password" name="api_password" value="" class="regular-text"
           placeholder="<?php echo $has_password ? esc_attr__('Passwort gesetzt (neu eingeben zum Ändern)', 'playdemo_configurator') : ''; ?>">
    <p class="description">Geben Sie hier das API-Passwort ein. Es wird aus Sicherheitsgründen nicht angezeigt.</p>
    <?php
    if ( $has_password ) {
        echo '<p class="description">Ein Passwort ist bereits gespeichert. Geben Sie ein neues ein, um es zu überschreiben.</p>';
    }
}

/**
 * Rendert das Eingabefeld für die Turnierserver-URL.
 */
function mac_field_tourney_server_url_callback() {
    $value = get_option('tourney_server_url', '');
    ?>
    <input type="url" id="tourney_server_url" name="tourney_server_url" value="<?php echo esc_attr($value); ?>" class="regular-text" placeholder="https://example.com/srv">
    <p class="description">Geben Sie hier die Basis-URL des Play-Backends ein (z.B. mit dem Präfix /srv).</p>
    <?php
}

/**
 * Sanitisierungs-Callback für das API-Passwort.
 */
function mac_sanitize_api_password( $new_value ) {
    $old_value = get_option( 'api_password' );
    if ( empty( $new_value ) ) {
        return $old_value;
    }
    return sanitize_text_field( $new_value );
}

add_action('admin_menu', 'playdemo_register_settings');