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

// Load required files
#require_once PLAYPLUGIN_PATH . 'includes/admin.php';
require_once PLAYPLUGIN_PATH . 'includes/user.php';
require_once PLAYPLUGIN_PATH . 'includes/settings.php';
require_once PLAYPLUGIN_PATH . 'includes/api-user.php';
#require_once PLAYPLUGIN_PATH . 'includes/helpers.php';


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



