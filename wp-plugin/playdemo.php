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

// Define Plugin-Path
define('PLAYPLUGIN_PATH', plugin_dir_path(__FILE__));


// Update-sichere mehrsprachige Dialoge für WP-Members.
add_filter( 'wpmem_default_msgs', 'my_multilingual_registration_msgs' );

function my_multilingual_registration_msgs( $msgs ) {
    // Sprache ermitteln (Polylang / WPML / WordPress Default)
    if ( function_exists('pll_current_language') ) {
        $lang = pll_current_language();
    } elseif ( defined('ICL_LANGUAGE_CODE') ) {
        $lang = ICL_LANGUAGE_CODE;
    } else {
        $lang = substr(get_locale(), 0, 2); // Nimmt z.B. "de" aus "de_DE"
    }

    // Sprachweichen
    switch ( $lang ) {
        case 'en':
            $msgs['register_success'] = 'Congratulations! Your registration was successful.<br /><br />You can now log in using your Passkey or the credentials sent to you.';
            $msgs['msg_username_exists'] = 'Sorry, that username is already taken.';
            break;

        case 'de':
        default:
            $msgs['register_success'] = 'Herzlichen Glückwunsch! Deine Registrierung war erfolgreich.<br /><br />Du kannst dich jetzt mit deinem Passkey oder deinen Zugangsdaten anmelden.';
            $msgs['msg_username_exists'] = 'Dieser Benutzername ist leider schon vergeben.';
            break;
    }

    return $msgs;
}


// Load required files
#require_once PLAYPLUGIN_PATH . 'includes/admin.php';
require_once PLAYPLUGIN_PATH . 'includes/user.php';
require_once PLAYPLUGIN_PATH . 'includes/cpt.php';
require_once PLAYPLUGIN_PATH . 'includes/settings.php';
require_once PLAYPLUGIN_PATH . 'includes/api-user.php';
require_once PLAYPLUGIN_PATH . 'includes/api-json.php';
require_once PLAYPLUGIN_PATH . 'includes/api-set-meta.php';
#require_once PLAYPLUGIN_PATH . 'includes/helpers.php';


function pdemo_custom_post_type() {
    register_post_type('playdemo',
        array(
            'labels'      => array(
                'name'          => 'Playdemos',
                'singular_name' => 'Playdemo',
            ),
            'public'              => true,  // Macht ihn grundsätzlich öffentlich
            'publicly_queryable'  => true,  // Erlaubt den Aufruf der URL
            'exclude_from_search' => false, // Erscheint dann auch in der Suche     
            'show_ui'             => true,  // Zeigt das Menü im Backend
            'show_in_menu'        => true,
            'show_in_rest'        => true,  // Wichtig für die API
            'has_archive'         => false,
            'hierarchical'        => true,
            'menu_icon'           => 'dashicons-media-spreadsheet',
            'supports'            => array('title', 'editor', 'custom-fields', 'page-attributes'),
            'capability_type'     => 'post',
            'query_var'           => true,
            'can_export'          => true,
        )
    );
}
add_action('init', 'pdemo_custom_post_type');


function register_pdemo_custom_meta_fields() {
    register_post_meta( 'playdemo', 'object_typ', array(
        'show_in_rest' => true, // <-- Dies ist der entscheidende Punkt
        'single'       => true,
        'type'         => 'string',
        'auth_callback' => function() {
            return current_user_can( 'edit_posts' ); // Oder eine spezifischere Capability
        },
        'sanitize_callback' => 'sanitize_text_field', // Wichtig: Desinfektion
        'description'  => 'Data 1'
    ) );
}
add_action( 'rest_api_init', 'register_pdemo_custom_meta_fields' );


// Spalte registrieren
add_filter('manage_playdemo_posts_columns', function($columns) {
    $columns['pdemo_type'] = 'Objekt-Typ';
    return $columns;
});

// Spalte befüllen
add_action('manage_playdemo_posts_custom_column', function($column, $post_id) {
    if ($column === 'pdemo_type') {
        $type = get_post_meta($post_id, 'object_typ', true);
        echo '<mark style="background:#e5e5e5; padding:3px 8px; border-radius:3px;">' . esc_html($type) . '</mark>';
    }
}, 10, 2);


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
    $jsPath  = plugin_dir_path(__FILE__) . 'js/main.js';
    $jsUrl   = plugins_url('js/main.js', __FILE__) . '?v=' . filemtime($jsPath);
    $dataUrl = plugins_url('data/', __FILE__);
    $playUrl = get_option('playdemo_url', '');
    $pageId  = get_the_ID();
    $homeUrl = home_url();
    $nonce   = wp_create_nonce('wp_rest');

    $output = '<span id="Main_ParamId" data-dataurl="' . esc_url($dataUrl) . '" data-homeurl="' . esc_url($homeUrl) . '" data-playurl="' . esc_url($playUrl) . '" data-nonce="' . esc_attr($nonce) . '" data-pageid="' . esc_attr($pageId) . '" ></span>';
    $output .= '<span id="Footer_ConsoleClickId" data-command=""></span>';
    $output .= '<span id="DlgPrompt_LoadId" data-loaded="false"></span>';
    $output .= '<div id="Main_WordpressId"></div>';
    $output .= '<script type="module">';
    $output .= 'import { startApp } from "' . esc_url($jsUrl) . '";';
    $output .= 'startApp("001DE1970-01", "wp", "debug");';
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

    //wp_enqueue_script('tourney_js', plugin_dir_url(__FILE__) . 'js/main.js', [], '1.0', false);
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



