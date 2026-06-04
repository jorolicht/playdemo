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
require_once PLAYPLUGIN_PATH . 'includes/api/user.php';
require_once PLAYPLUGIN_PATH . 'includes/api/player.php';
require_once PLAYPLUGIN_PATH . 'includes/api/club.php';
require_once PLAYPLUGIN_PATH . 'includes/api/tourney.php';
require_once PLAYPLUGIN_PATH . 'includes/api/competition.php';
require_once PLAYPLUGIN_PATH . 'includes/api/round.php';
require_once PLAYPLUGIN_PATH . 'includes/api/json.php';
require_once PLAYPLUGIN_PATH . 'includes/helpers.php';


/**
 * Renders the shortcode for the "playdemo" application/plugin.
 *
 * This function retrieves the necessary URLs and a nonce for secure communication
 * and then generates the HTML output for the application's front-end. It outputs
 * a container for the application and a script tag that initializes the main
 * JavaScript application with the required data.
 *
 * The shortcode `[playdemo mode="wp"]` can be used to embed the application anywhere
 * on the WordPress site.
 *
 * @param array $atts Shortcode attributes.
 * @return string The complete HTML output for the shortcode.
 */
function playdemo_render($atts) {
    $atts = shortcode_atts( array(
        'mode' => 'multi', // default mode
        'page' => '',      // optional page parameter for more specific views    
    ), $atts );

    $jsPath  = plugin_dir_path(__FILE__) . 'js/main.js';
    $jsUrl   = plugins_url('js/main.js', __FILE__) . '?v=' . filemtime($jsPath);
    $dataUrl = plugins_url('data/', __FILE__);
    $imgUrl  = plugins_url('img/', __FILE__);
    $playUrl = get_option('playdemo_url', '');
    $pageId  = get_the_ID();
    $homeUrl = home_url();
    $nonce   = wp_create_nonce('wp_rest');

    $logLevel  = isset($_GET['logLevel']) ? $_GET['logLevel'] : 'debug';
    $tourney = isset($_GET['tourney']) ? $_GET['tourney'] : '';

    $output = '<span id="Main_ParamId" data-page="' . esc_attr($atts['page']) . '" data-dataurl="' . esc_url($dataUrl) . '" data-imgurl="' . esc_url($imgUrl) . '" data-homeurl="' . esc_url($homeUrl) . '" data-playurl="' . esc_url($playUrl) . '" data-nonce="' . esc_attr($nonce) . '" data-pageid="' . esc_attr($pageId) . '" ></span>';
    $output .= '<span id="Main_DynContentId"></span>';
    $output .= '<span id="Main_NavbarId"></span>';
    $output .= '<div class="container-fluid mt-3">';
    $output .= '   <div id="Main_ContentId" class="d-flex mt-2 mr-2 justify-content-center">';
    $output .= '      Main Content';
    $output .= '   </div>';
    $output .= '</div>';
    $output .= '<span id="Footer_ConsoleClickId" data-command=""></span>';
    $output .= '<script type="module">';
    $output .= 'import { startApp } from "' . esc_url($jsUrl) . '";';
    $output .= 'startApp("001DE1970-01", "' . esc_attr($atts['mode']) . '", "' . esc_attr($logLevel) . '", "' . esc_attr($tourney) . '");';
    $output .= '</script>';

    return $output;
}
add_shortcode('playdemo', 'playdemo_render');


/**
 * Filters the template for "tourney" single posts to ensure the shortcode is rendered
 * even if the theme doesn't support the custom post type content out of the box.
 *
 * @param string $template The path to the template.
 * @return string The path to the template.
 */
add_filter('template_include', function ($template) {
    if (is_singular('tourney')) {
        // If the theme has a specific template, use it.
        // Otherwise, we can force a basic output that includes our shortcode.
        // For simplicity and to ensure it works, we can hook into the_content
        // or ensure the loop is handled.
    }
    return $template;
});


/**
 * Ensures that the shortcode is added to the content of 'tourney' posts
 * if it's missing, or just makes sure it's processed correctly.
 */
add_filter('the_content', function ($content) {
    if (is_singular('tourney') && !has_shortcode($content, 'playdemo')) {
        $content .= '[playdemo mode="single"]';
    }
    return $content;
});


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
    // Den Pfad zur CSS-Datei dynamisch generieren
    $url = plugin_dir_url( __FILE__ ) . 'css/style.css';

    // Das Stylesheet registrieren und einreihen
    wp_enqueue_style( 
        'mein-plugin-basis-style', // Einzigartiger Name (Handle)
        $url,                      // URL zur Datei
        array(),                   // Abhängigkeiten (falls nötig)
        '1.0.0'                    // Version (hilft beim Browser-Caching)
    );    

    // add script in head (false)
    wp_enqueue_style( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css', array(), '5.3.3' );
    wp_enqueue_style( 'bootstrap-icons', 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css', array(), '1.11.3' );
    wp_enqueue_script( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js', array(), '5.3.3', false );

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



