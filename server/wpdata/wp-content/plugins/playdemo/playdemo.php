<?php
/*
Plugin Name: Playdemo Example
Plugin URI: http://beispiel.de/plugins/mein-plugin
Description: Playdemo Example WordPress Plugin
Version: 1.0
Author: Max Mustermann
Author URI: http://beispiel.de
License: GPLv2
*/

if (!defined('ABSPATH')) {
    exit; // Sicherheitsprüfung
}

function pdemo_custom_post_type() {
	register_post_type('playdemo',
		array(
			'labels'      => array(
                'name'          => 'Playdemos',
				'singular_name' => 'Playdemo',
			),
				'public'          => false,
				'has_archive'     => false,
                'show_ui'         => true,
                'show_in_menu'    => true,
                'menu_icon'       => 'dashicons-media-spreadsheet', // A WordPress icon for the menu
                'rewrite'         => array( 'slug' => 'playdemo' ), // Sets the URL slug (e.g., /playdemos/date/)
                'show_in_rest'    => true, // <--- Crucial for REST API access!
                'supports'        => array( 'title', 'custom-fields' ), // What standard features it uses
                'capability_type' => 'post',
		)
	);
}
add_action('init', 'pdemo_custom_post_type');


function register_pdemo_custom_meta_fields() {
    register_post_meta( 'playdemo', 'data_1', array(
        'show_in_rest' => true, // <-- Dies ist der entscheidende Punkt
        'single'       => true,
        'type'         => 'string',
        'auth_callback' => function() {
            return current_user_can( 'edit_posts' ); // Oder eine spezifischere Capability
        },
        'sanitize_callback' => 'sanitize_text_field', // Wichtig: Desinfektion
        'description'  => 'Data 1'
    ) );
    register_post_meta( 'playdemo', 'data_2', array(
        'show_in_rest' => true, // <-- Dies ist der entscheidende Punkt
        'single'       => true,
        'type'         => 'string',
        'auth_callback' => function() {
            return current_user_can( 'edit_posts' ); // Oder eine spezifischere Capability
        },
        'sanitize_callback' => 'sanitize_text_field', // Wichtig: Desinfektion
        'description'  => 'Data 2'
    ) );
}
add_action( 'rest_api_init', 'register_pdemo_custom_meta_fields' );




/**
 * Renders the shortcode for the "playdemo" application/plugin.
 *
 * This function retrieves the necessary URLs and a nonce for secure communication
 * and then generates the HTML output for the application's front-end. It outputs
 * a container for the application and a script tag that initializes the main
 * JavaScript application with the required data.
 *
 * The shortcode `[playdemo]` can be used to embed the application anywhere
 * on the WordPress site.
 *
 * @return string The complete HTML output for the shortcode.
 */
function playdemo_render() {
    $playURL = get_option('playdemo_url', '');
    $wpURL   = home_url();
    $nonce   = wp_create_nonce('wp_rest');

    $output = '<div id="appcontent"></div>';
    $output .= '<script type="text/javascript">';
    $output .= 'Main.wpStart("'. $playURL .'", "'. $wpURL .'", "'. $nonce .'");';
    $output .= '</script>';

    return $output;
}
add_shortcode('playdemo', 'playdemo_render');


/**
 * Enqueues scripts and styles for the plugin's front-end.
 *
 * This function is hooked to the 'wp_enqueue_scripts' action. It registers
 * and enqueues several external and local assets to ensure the plugin's
 * functionality and styling are available on the front-end.
 *
 * It includes:
 * - Bootstrap 5 CSS and JavaScript from a CDN.
 * - Bootstrap Icons CSS from a CDN.
 * - A custom CSS file located in the plugin's 'css' directory.
 * - A custom JavaScript file located in the plugin's 'js' directory.
 *
 * Note: The scripts are configured to be loaded in the document head (the 'false'
 * parameter), not the footer.
 *
 * @return void
 */
function js_enqueue_scripts_styles() {
    // add script in head (false)
    wp_enqueue_style( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css', array(), '5.3.3' );
    wp_enqueue_style( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css', array(), '1.11.3' );
    wp_enqueue_script( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.min.js', array(), '5.3.3', false );

    wp_enqueue_script('tourney_js', plugin_dir_url(__FILE__) . 'js/main.js', [], '1.0', false);
    wp_enqueue_style( 'tourney_style', plugin_dir_url(__FILE__) . 'css/main.css', [], '1.0');
}
add_action('wp_enqueue_scripts', 'js_enqueue_scripts_styles');


/**
 * Allows the uploading of JSON files in WordPress.
 *
 * This function adds the 'application/json' MIME type to the list of allowed
 * file types for uploads. By default, WordPress does not permit the upload
 * of .json files for security reasons. This filter is necessary for plugins or themes
 * that need to handle JSON data via the media uploader.
 *
 * @param array $mimes An associative array of allowed MIME types and their extensions.
 * @return array The updated associative array of allowed MIME types.
 */
function allow_json_mime_type( $mimes ) {
    $mimes['json'] = 'application/json';
    return $mimes;
}
add_filter( 'upload_mimes', 'allow_json_mime_type' );


// Registrierung des API-Endpunkts
add_action('rest_api_init', function () {
    register_rest_route('playdemo/v1', '/user/', [
        'methods'  => 'GET',
        'callback' => 'playdemo_api_callback_user',
        'permission_callback' => 'playdemo_api_create_get_permissions_check' 
    ]);
});


function playdemo_api_create_get_permissions_check( WP_REST_Request $request ) {
    // Prüfen, ob der aktuelle Benutzer die Fähigkeit 'edit_posts' hat
    // Für Administratoren: current_user_can( 'manage_options' )
    // Für Redakteure: current_user_can( 'edit_others_posts' )
    // if ( ! is_user_logged_in() ) {
    //     return new WP_Error( 'rest_forbidden_access', __( 'Sie sind nicht berechtigt, Posts zu erstellen.', 'my-custom-api' ), array( 'status' => 403 ) );
    // }
    return true;
}


// PlaydemoPwName
// PnK6aeahzx9dt4wlLHNTJYcH

// Die Callback-Funktion für den Endpunkt
function playdemo_api_callback_user($request) {
    $user_id        = 'null';
    $username       = '';
    $email          = '';
    $club           = '';
    $user_api_pw    = '';  
    $api_password   = ''; 

    if ( is_user_logged_in() )  {
        $user_id        = get_current_user_id();
        $current_user   = wp_get_current_user();
        $username       = $current_user->user_login;
        $email          = $current_user->user_email;     
        $club           = get_user_meta($user_id, 'club_name' , true );
        $user_api_pw    = get_option('playdemo_user_api_pw');  
        $api_password   = get_option('playdemo_api_password');        
    } 

    return [
        'username'      => $username,
        'user_id'       => $user_id,
        'email'         => $email,
        'club'          => $club,
        'user_api_pw'   => $user_api_pw,
        'api_passsword' => $api_password,
        'time'          => current_time('mysql'),
    ];
}




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
add_action('admin_menu', 'playdemo_register_settings');

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
 * Rendert das Eingabefeld für den Namen des API Passworts.
 */
function mac_field_app_name_callback() {
    $app_name = get_option('playdemo_user_api_pw', ''); // Wert auslesen, Standard ist leer.
    ?>
    <input type="text" id="playdemo_user_api_pw" name="playdemo_user_api_pw" value="<?php echo esc_attr($app_name); ?>" class="regular-text">
    <p class="description">Geben Sie den Usernamen ein (Ersteller des Anwendungspasswortes).</p>
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

/**
 * Fügt den App-Namen zum Website-Footer hinzu.
 */
// function mac_add_app_name_to_footer() {
//     $app_name = get_option('playdemo_user_api_pw', ''); // Den gespeicherten App-Namen auslesen

//     if ( ! empty( $app_name ) ) {
//         echo '<p style="text-align: center; font-size: 0.8em; color: #777;">Powered by ' . esc_html($app_name) . '</p>';
//     }
// }
// add_action('wp_footer', 'mac_add_app_name_to_footer');




/**
 * Adds custom user profile fields to the 'Edit User' and 'Your Profile' screens.
 *
 * @param WP_User $user The WP_User object.
 */
function playdemo_add_custom_user_profile_fields( $user ) {
    ?>
    <h3><?php esc_html_e( 'Additional Profile Information', 'user-custom-fields-club' ); ?></h3>

    <table class="form-table">
        <tr>
            <th><label for="club_name"><?php esc_html_e( 'Club Name', 'user-custom-fields-club' ); ?></label></th>
            <td>
                <input type="text" name="club_name" id="club_name" value="<?php echo esc_attr( get_user_meta( $user->ID, 'club_name', true ) ); ?>" class="regular-text" /><br />
                <span class="description"><?php esc_html_e( 'Please enter your club name.', 'user-custom-fields-club' ); ?></span>
            </td>
        </tr>
    </table>
    <?php
}
// For displaying fields on the 'Edit User' screen for administrators
add_action( 'show_user_profile', 'playdemo_add_custom_user_profile_fields' );
// For displaying fields on the 'Your Profile' screen (user's own profile)
add_action( 'edit_user_profile', 'playdemo_add_custom_user_profile_fields' );

/**
 * Saves the custom user profile fields.
 *
 * @param int $user_id The ID of the user being saved.
 */
function ucfe_save_custom_user_profile_fields( $user_id ) {
    // Check if the current user has permission to edit this user's profile.
    // Also check nonce for security (though WordPress handles this for core user profile updates).
    if ( ! current_user_can( 'edit_user', $user_id ) ) {
        return false;
    }

    // Sanitize and save the 'club_name' field.
    if ( isset( $_POST['club_name'] ) ) {
        // Sanitize the input before saving.
        $club_name = sanitize_text_field( $_POST['club_name'] );
        update_user_meta( $user_id, 'club_name', $club_name );
    } else {
        // If the field wasn't set (e.g., if it was hidden or not submitted for some reason),
        // you might want to delete the meta or set it to an empty string.
        delete_user_meta( $user_id, 'club_name' );
    }
}
// For saving fields on the 'Edit User' screen for administrators
add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
// For saving fields on the 'Your Profile' screen (user's own profile)
add_action( 'edit_user_profile_update', 'ucfe_save_custom_user_profile_fields' );


/**
 * Function to retrieve the custom field for display outside the admin.
 *
 * @param int $user_id The ID of the user.
 * @return string The user's favorite color, or an empty string if not set.
 */
function ucfe_get_user_favorite_color( $user_id ) {
    return get_user_meta( $user_id, 'club_name', true );
}



?>