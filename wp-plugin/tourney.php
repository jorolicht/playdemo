<?php
/**
 * Plugin Name: Tourney
 * Plugin URI: https://turnier-service.info
 * Description: Tourney WordPress Plugin - Professionelle Turnierverwaltung für Tischtennis und Turniersport
 * Version: 1.0.4
 * Requires at least: 6.0
 * Requires PHP: 8.0
 * Author: Robert Lichtenegger
 * Author URI: https://turnier-service.info
 * License: GPLv2 or later
 * License URI: https://www.gnu.org/licenses/gpl-2.0.html
 * Text Domain: tourney
 */

if (!defined('ABSPATH')) {
    exit; // Sicherheitsprüfung
}

// SemVer MAJOR.MINOR.PATCH Versionskonstanten
define('TOURNEY_VERSION', '1.0.4');
define('TOURNEY_DB_VERSION', '1.0.4');

// Define Plugin-Path
define('TOURNEY_PLUGIN_PATH', plugin_dir_path(__FILE__));

// Plugin-Variante (includes/config.php wird beim Build erzeugt):
// TOURNEY_FWS = true  -> Full Web Site mit Plugin-Header/-Footer
// TOURNEY_FWS = false -> eingebettet in das Theme, ohne Plugin-Header/-Footer
if (file_exists(TOURNEY_PLUGIN_PATH . 'includes/config.php')) {
    require_once TOURNEY_PLUGIN_PATH . 'includes/config.php';
}
if (!defined('TOURNEY_FWS')) {
    define('TOURNEY_FWS', true);
}

/**
 * Plugin activation hook.
 *
 * Initialisiert Schema- und Versions-Optionen bei der Aktivierung des Plugins.
 * Führt bei Bedarf Upgrades aus.
 *
 * @return void
 */
function tourney_activate() {
    $installed_db_ver = get_option('tourney_db_version');
    if ($installed_db_ver === false) {
        add_option('tourney_db_version', TOURNEY_DB_VERSION);
    } elseif (version_compare($installed_db_ver, TOURNEY_DB_VERSION, '<')) {
        tourney_upgrade_db($installed_db_ver, TOURNEY_DB_VERSION);
        update_option('tourney_db_version', TOURNEY_DB_VERSION);
    }
    update_option('tourney_version', TOURNEY_VERSION);
}
register_activation_hook(__FILE__, 'tourney_activate');

/**
 * Führt Versionsprüfungen und eventuelle DB-Migrationen beim Laden der Plugins durch.
 *
 * @return void
 */
function tourney_check_version() {
    $installed_db_ver = get_option('tourney_db_version');
    if ($installed_db_ver && version_compare($installed_db_ver, TOURNEY_DB_VERSION, '<')) {
        tourney_upgrade_db($installed_db_ver, TOURNEY_DB_VERSION);
        update_option('tourney_db_version', TOURNEY_DB_VERSION);
    } elseif ($installed_db_ver === false) {
        // Falls Option noch fehlt (z. B. Plugin wurde vor Hook-Implementierung aktiviert)
        update_option('tourney_db_version', TOURNEY_DB_VERSION);
    }
    if (get_option('tourney_version') !== TOURNEY_VERSION) {
        update_option('tourney_version', TOURNEY_VERSION);
    }
}
add_action('plugins_loaded', 'tourney_check_version');

/**
 * Führt schrittweise Upgrades / Migrationen der Datenbank durch.
 *
 * @param string $from_version Bisherige Version.
 * @param string $to_version   Neue Zielversion.
 * @return void
 */
function tourney_upgrade_db($from_version, $to_version) {
    error_log(sprintf('Tourney DB upgraded from %s to %s', $from_version, $to_version));
}


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
require_once TOURNEY_PLUGIN_PATH . 'includes/user.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/cpt.php';
// Tourney-Konfigurator (Einstellungsseite) nur bei Full Web Site
if (TOURNEY_FWS) {
    require_once TOURNEY_PLUGIN_PATH . 'includes/settings.php';
}
require_once TOURNEY_PLUGIN_PATH . 'includes/api/user.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/webauthn.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/player.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/club.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/tourney.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/competition.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/stage.php';
require_once TOURNEY_PLUGIN_PATH . 'includes/api/json.php';
// Payhip-Webhook (Kauf von Turnier-Kontingent) nur bei Full Web Site
if (TOURNEY_FWS) {
    require_once TOURNEY_PLUGIN_PATH . 'includes/api/payhip.php';
}
require_once TOURNEY_PLUGIN_PATH . 'includes/helpers.php';


/**
 * Renders the shortcode for the "tourney" application/plugin.
 *
 * This function retrieves the necessary URLs and a nonce for secure communication
 * and then generates the HTML output for the application's front-end. It outputs
 * a container for the application and a script tag that initializes the main
 * JavaScript application with the required data.
 *
 * The shortcode `[tourney]` embeds the application; `[tourney page="login"]`
 * (login, register, verify) opens a specific page directly. The start view is
 * derived from TOURNEY_FWS, the WordPress homepage and the user's edit rights.
 *
 * @param array $atts Shortcode attributes.
 * @return string The complete HTML output for the shortcode.
 */
function tourney_render($atts) {

    $atts = shortcode_atts( array(
        'page' => '',      // optional page parameter for more specific views (login, register, verify)
    ), $atts );

    $jsPath  = plugin_dir_path(__FILE__) . 'js/main.js';
    $jsVer   = file_exists($jsPath) ? filemtime($jsPath) : TOURNEY_VERSION;
    $jsUrl   = plugins_url('js/main.js', __FILE__) . '?v=' . $jsVer;
    $dataUrl = plugins_url('data/', __FILE__);
    $imgUrl  = plugins_url('img/', __FILE__);
    $playUrl = get_option('tourney_server_url', get_option('tourney_url', ''));
    $pageId  = get_the_ID();
    $homeUrl = home_url();
    $nonce   = wp_create_nonce('wp_rest');

    $post = get_post($pageId);
    $slug = $post ? $post->post_name : '';
    error_log('Slug=' . print_r($slug, true));

    // URL Parameter 
    $logLevel  = isset($_GET['logLevel']) ? $_GET['logLevel'] : 'debug';
    $tourney   = isset($_GET['tourney']) ? sanitize_title($_GET['tourney']) : '';
    $isHome    = is_front_page();

    // Check request URI path to see if a tournament permalink URL was called (e.g. /tourney/organizer/slug/)
    $uriPath  = trim(parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH), '/');
    $segments = array_values(array_filter(explode('/', $uriPath)));
    if (count($segments) >= 2 && $segments[0] === 'tourney') {
        $tourney = end($segments);
    } elseif ($post && $post->post_type === 'tourney') {
        $tourney = $slug;
    }

    // Schreibrecht auf die Seite, die das Turnier enthält (bei ?tourney=<slug> der Turnier-Post selbst)
    $editPostId = $pageId;
    if ($tourney !== '' && $tourney !== $slug) {
        $found = get_posts(array(
            'name'        => $tourney,
            'post_type'   => 'tourney',
            'post_status' => 'any',
            'numberposts' => 1,
            'fields'      => 'ids',
        ));
        $editPostId = !empty($found) ? $found[0] : 0;
    }
    $canEdit = tourney_user_can_edit_post($editPostId);

    $turnstile_key = get_option('cfturnstile_key', getenv('TURNSTILE_SITEKEY') ?: '');
    if (!empty($turnstile_key)) {
         wp_enqueue_script('cloudflare-turnstile', 'https://challenges.cloudflare.com/turnstile/v0/api.js', array(), null, true);
    }

    $output = '<div class="tourney-spa-root alignfull">';
    $output .= '<span id="Main_ParamId" 
        data-version="' . esc_attr(TOURNEY_VERSION) . '" 
        data-page="' . esc_attr($atts['page']) . '" 
        data-slug="' . esc_attr($slug) . '"
        data-posttype="' . esc_attr($post ? $post->post_type : '') . '"
        data-dataurl="' . esc_url($dataUrl) . '" 
        data-imgurl="' . esc_url($imgUrl) . '" 
        data-homeurl="' . esc_url($homeUrl) . '" 
        data-playurl="' . esc_url($playUrl) . '" 
        data-nonce="' . esc_attr($nonce) . '" 
        data-pageid="' . esc_attr($pageId) . '" 
        data-turnstilesitekey="' . esc_attr($turnstile_key) . '"
        data-fws="' . (TOURNEY_FWS ? '1' : '0') . '"
        data-ishome="' . ($isHome ? '1' : '0') . '"
        data-canedit="' . ($canEdit ? '1' : '0') . '"
    ></span>';   
   
    $output .= '<span id="Main_NavbarId"></span>';
    $output .= '<span id="Main_ContextHeaderId"></span>';
    $wrapperClass = TOURNEY_FWS ? 'd-flex flex-column min-vh-100' : 'd-flex flex-column';
    $output .= '<div id="Main_AppWrapper" class="' . esc_attr($wrapperClass) . '">';
    $output .= '   <div id="Main_ContentId" class="flex-grow-1">';
    $output .= '      <div class="d-flex mt-5 justify-content-center"><span>Wird geladen...</span></div>';
    $output .= '   </div>';
    $output .= '   <span id="Main_FooterId"></span>';
    $output .= '</div>';
    $output .= '<span id="Footer_ConsoleClickId" data-command=""></span>';
    $output .= '<span id="Main_DynContentId"></span>';
    $output .= '<script type="module">';

    $output .= 'import { startApp } from "' . esc_url($jsUrl) . '";';
    $output .= 'startApp("' . esc_attr(TOURNEY_VERSION) . '", "' . esc_attr($logLevel) . '", "' . esc_attr($tourney) . '");';
    $output .= '</script>';
    $output .= '</div>';

    return $output;
}
add_shortcode('tourney', 'tourney_render');

/**
 * Konfiguriert PHPMailer für MailCatcher im Docker-Netzwerk.
 *
 * @param PHPMailer $phpmailer Das PHPMailer-Instanz-Objekt.
 */
add_action('phpmailer_init', function($phpmailer) {
    $phpmailer->isSMTP();
    $phpmailer->Host = 'mailcatcher'; // Hostname im Docker-Netzwerk
    $phpmailer->Port = 1025;
    $phpmailer->SMTPAuth = false;
    $phpmailer->SMTPSecure = '';
    $phpmailer->SMTPAutoTLS = false;
}, 99999);

/**
 * Verhindert, dass WordPress den [tourney] Shortcode in <p>-Tags einschließt,
 * da dieser Block-Elemente (div) generiert, was zu invalidem HTML (stray </p>) führt.
 * Dieser Filter läuft bei Priorität 12 (nach do_shortcode), um die generierten Container sauber zu säubern.
 */
function tourney_unautop_shortcode($content) {
    if (false === strpos($content, 'tourney-spa-root')) {
        return $content;
    }
    // Entfernt umgebende <p>-Tags um die tourney-spa-root Division
    $content = preg_replace('/<p>\s*(<div class="tourney-spa-root".*?<\/div>)\s*<\/p>/is', '$1', $content);
    return $content;
}
add_filter('the_content', 'tourney_unautop_shortcode', 12);


/**
 * Nutzt bei eingebettetem Plugin (TOURNEY_FWS = false) das Seiten-Template ('page.php') statt des Beitrags-Templates ('single.php'),
 * damit keine Beitrags-Metadaten ("Verfasst von ...", Kategorien, Kommentare) angezeigt werden.
 *
 * @param array $templates Liste der Template-Dateinamen.
 * @return array Angepasste Template-Hierarchie.
 */
function tourney_pure_mode_page_hierarchy( $templates ) {
    $post = get_post();
    if ( ! TOURNEY_FWS && $post && $post->post_type === 'tourney' ) {
        array_unshift( $templates, 'page.php' );
    }
    return $templates;
}
add_filter( 'single_template_hierarchy', 'tourney_pure_mode_page_hierarchy' );

/**
 * 2. Das leere Template erzwingen, bei Full Web Site (TOURNEY_FWS = true) für alle Frontend-Seiten:
 *    es erscheinen keine WordPress-Header/-Footer, nur die vom Scala.js-Client erzeugten.
 *    Eingebettet (TOURNEY_FWS = false) bleiben Theme-Header und -Footer erhalten.
 *
 * @param string $template Pfad des von WordPress gewählten Templates.
 * @return string Unverändertes Template im eingebetteten Betrieb.
 */
function tourney_force_blank_template( $template ) {
    if ( TOURNEY_FWS ) {
        tourney_render_direct_blank_page();
        exit;
    }
    return $template;
}
add_filter( 'template_include', 'tourney_force_blank_template', 999 );

/**
 * Blendet bei Full Web Site (TOURNEY_FWS = true) die WordPress-Admin-Leiste im Frontend aus,
 * außer für WordPress-Administratoren.
 *
 * @param bool $show Ob WordPress die Admin-Leiste anzeigen würde.
 * @return bool True, wenn die Admin-Leiste angezeigt werden soll.
 */
function tourney_admin_bar_only_for_admins( $show ) {
    if ( TOURNEY_FWS && ! in_array( 'administrator', (array) wp_get_current_user()->roles, true ) ) {
        return false;
    }
    return $show;
}
add_filter( 'show_admin_bar', 'tourney_admin_bar_only_for_admins' );

/**
 * 3. Das nackte HTML-Gerüst inklusive App-Root ausgeben
 */
function tourney_render_direct_blank_page() {
    ?>
    <!DOCTYPE html>
    <html <?php language_attributes(); ?>>
    <head>
        <meta charset="<?php bloginfo( 'charset' ); ?>">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><?php wp_title('|', true, 'right'); ?></title>
        <?php 
        // Lädt Header-Metas und CSS-Dateien
        wp_head(); 
        ?>
        <style>
            html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                overflow-x: hidden;
            }
        </style>
    </head>
    <body <?php body_class(); ?>>
        <?php 
        echo tourney_render( tourney_shortcode_atts_of_current_post() );
        wp_footer(); 
        ?>
    </body>
    </html>
    <?php
}


/**
 * Liest die Attribute des ersten [tourney]-Shortcodes im aktuell angezeigten Post
 * (z. B. page="login"), damit sie auch auf der leeren Seite gelten.
 *
 * @return array Shortcode-Attribute oder leeres Array, wenn kein Shortcode vorhanden ist.
 */
function tourney_shortcode_atts_of_current_post() {
    $post = is_singular() ? get_queried_object() : null;
    if ( $post instanceof WP_Post && preg_match( '/' . get_shortcode_regex( array( 'tourney' ) ) . '/', $post->post_content, $m ) ) {
        $atts = shortcode_parse_atts( $m[3] );
        return is_array( $atts ) ? $atts : array();
    }
    return array();
}


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
    // Basis-Stylesheet mit Cache-Busting (filemtime oder TOURNEY_VERSION)
    $style_css_path = TOURNEY_PLUGIN_PATH . 'css/style.css';
    $style_ver      = file_exists($style_css_path) ? filemtime($style_css_path) : TOURNEY_VERSION;
    wp_enqueue_style( 
        'mein-plugin-basis-style', 
        plugin_dir_url( __FILE__ ) . 'css/style.css', 
        array(), 
        $style_ver 
    );    

    // Externe Bibliotheken
    wp_enqueue_style( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css', array(), '5.3.3' );
    wp_enqueue_style( 'bootstrap-icons', 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css', array(), '1.11.3' );
    wp_enqueue_script( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js', array(), '5.3.3', false );
    if ( TOURNEY_FWS ) {
        wp_enqueue_script( 'payhip', 'https://payhip.com/payhip.js', array(), null, false );
    }

    // Tourney-Haupt-Stylesheet mit Cache-Busting
    $main_css_path = TOURNEY_PLUGIN_PATH . 'css/main.css';
    $main_ver      = file_exists($main_css_path) ? filemtime($main_css_path) : TOURNEY_VERSION;
    wp_enqueue_style( 'tourney_style', plugin_dir_url(__FILE__) . 'css/main.css', array(), $main_ver);
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

/**
 * Enable 'lang' parameter for Polylang in WP REST API (Free version workaround).
 * Allows fetching pages/posts by specific language using ?lang=XX.
 */
function tourney_polylang_rest_query_filter($args, $request) {
    $lang = $request->get_param('lang');
    if (!empty($lang)) {
        $args['lang'] = $lang;
    }
    return $args;
}
add_filter('rest_post_query', 'tourney_polylang_rest_query_filter', 10, 2);
add_filter('rest_page_query', 'tourney_polylang_rest_query_filter', 10, 2);
add_filter('rest_tourney_query', 'tourney_polylang_rest_query_filter', 10, 2);




