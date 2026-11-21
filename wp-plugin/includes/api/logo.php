<?php
/**
 * Eigenes Logo eines Turniers (Turnier-Administration, Reiter "Homepage").
 *
 * Das Logo wird in der WordPress-Mediathek als Anhang des Turnier-Posts gespeichert, beim Hochladen auf höchstens
 * TOURNEY_LOGO_MAX_PX Pixel Kantenlänge verkleinert und in der Post-Meta "tourney_logo" (Attachment-ID) vermerkt.
 * Ein neues Logo ersetzt das alte, beim endgültigen Löschen des Turniers wird es mit gelöscht.
 * Angezeigt wird es auf der Turnierseite (TourneyWelcome) und auf den Urkunden; der Client erhält die Adresse
 * über read-aci (Feld logoUrl, siehe tourney_read_aci).
 */

/** Maximale Dateigröße beim Hochladen in Byte (2 MB). */
const TOURNEY_LOGO_MAX_BYTES = 2 * 1024 * 1024;

/** Maximale Kantenlänge des gespeicherten Logos in Pixel. */
const TOURNEY_LOGO_MAX_PX = 400;

/** Erlaubte Bildformate (kein SVG: kann Skripte enthalten). */
const TOURNEY_LOGO_MIME_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

add_action('rest_api_init', function () {

    // POST /tourney/v1/logo?postId=<id> - Logo hochladen (multipart/form-data, Feld "file"), ersetzt ein vorhandenes
    // DELETE /tourney/v1/logo?postId=<id> - Logo entfernen
    register_rest_route('tourney/v1', '/logo', [
        [
            'methods'             => 'POST',
            'callback'            => 'tourney_upload_logo',
            'permission_callback' => 'tourney_api_can_edit_request_post'
        ],
        [
            'methods'             => 'DELETE',
            'callback'            => 'tourney_delete_logo',
            'permission_callback' => 'tourney_api_can_edit_request_post'
        ],
    ]);

});

/**
 * Liefert die Adresse des Logos eines Turniers.
 *
 * @param int $post_id Post-ID des Turniers.
 * @return string URL des Logos oder "" ohne Logo.
 */
function tourney_logo_url($post_id) {
    $id = intval(get_post_meta($post_id, 'tourney_logo', true));
    if ($id <= 0) {
        return '';
    }
    $url = wp_get_attachment_url($id);
    return $url ? $url : '';
}

/**
 * Löscht das Logo eines Turniers (Anhang samt Dateien und Post-Meta).
 *
 * @param int $post_id Post-ID des Turniers.
 * @return void
 */
function tourney_remove_logo($post_id) {
    $id = intval(get_post_meta($post_id, 'tourney_logo', true));
    if ($id > 0) {
        wp_delete_attachment($id, true);
    }
    delete_post_meta($post_id, 'tourney_logo');
}

/**
 * Lädt ein Logo hoch: prüft Größe und Format, speichert es als Anhang des Turniers, verkleinert es auf
 * TOURNEY_LOGO_MAX_PX und ersetzt ein vorhandenes Logo.
 *
 * @param WP_REST_Request $request Parameter postId (oder slug), Datei im Feld "file".
 * @return array|WP_REST_Response ["logoUrl" => URL] oder Fehler (logo_missing, logo_too_large, logo_type, logo_save).
 */
function tourney_upload_logo(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }
    $files = $request->get_file_params();
    $file  = $files['file'] ?? null;
    if (!$file || !empty($file['error']) || empty($file['tmp_name'])) {
        return ApiHelper::error("logo_missing", "No file uploaded", "", "tourney_upload_logo", HttpStatus::BAD_REQUEST);
    }
    if (intval($file['size']) > TOURNEY_LOGO_MAX_BYTES) {
        return ApiHelper::error("logo_too_large", "File larger than 2 MB", "", "tourney_upload_logo", HttpStatus::BAD_REQUEST);
    }
    // Format anhand des Inhalts prüfen, nicht nur anhand der Endung
    $check = wp_check_filetype_and_ext($file['tmp_name'], $file['name']);
    $info  = @getimagesize($file['tmp_name']);
    if (empty($check['type']) || !in_array($check['type'], TOURNEY_LOGO_MIME_TYPES, true)
        || !$info || !in_array($info['mime'], TOURNEY_LOGO_MIME_TYPES, true)) {
        return ApiHelper::error("logo_type", "Only PNG, JPG or WebP allowed", "", "tourney_upload_logo", HttpStatus::BAD_REQUEST);
    }

    require_once ABSPATH . 'wp-admin/includes/file.php';
    require_once ABSPATH . 'wp-admin/includes/media.php';
    require_once ABSPATH . 'wp-admin/includes/image.php';

    $_FILES['tourney_logo'] = $file;
    $attachment_id = media_handle_upload('tourney_logo', $post_id, ['post_title' => 'Logo ' . get_the_title($post_id)]);
    if (is_wp_error($attachment_id)) {
        return ApiHelper::error("logo_save", $attachment_id->get_error_message(), "", "tourney_upload_logo", HttpStatus::INTERNAL_SERVER_ERROR);
    }

    // Auf höchstens TOURNEY_LOGO_MAX_PX verkleinern (Seitenverhältnis bleibt erhalten) und Metadaten neu erzeugen
    $path   = get_attached_file($attachment_id);
    $editor = wp_get_image_editor($path);
    if (!is_wp_error($editor)) {
        $size = $editor->get_size();
        if ($size['width'] > TOURNEY_LOGO_MAX_PX || $size['height'] > TOURNEY_LOGO_MAX_PX) {
            $editor->resize(TOURNEY_LOGO_MAX_PX, TOURNEY_LOGO_MAX_PX, false);
            $editor->save($path);
        }
    }
    wp_update_attachment_metadata($attachment_id, wp_generate_attachment_metadata($attachment_id, $path));

    // Vorhandenes Logo ersetzen
    tourney_remove_logo($post_id);
    update_post_meta($post_id, 'tourney_logo', $attachment_id);

    return ["logoUrl" => tourney_logo_url($post_id)];
}

/**
 * Entfernt das Logo eines Turniers.
 *
 * @param WP_REST_Request $request Parameter postId (oder slug).
 * @return array|WP_REST_Response ["success" => true] oder Fehler.
 */
function tourney_delete_logo(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }
    tourney_remove_logo($post_id);
    return ["success" => true];
}

/**
 * Löscht das Logo, wenn das Turnier endgültig gelöscht wird.
 *
 * @param int $post_id Post-ID.
 * @return void
 */
function tourney_logo_delete_post($post_id) {
    if (intval(get_post_meta($post_id, 'tourney_logo', true)) > 0) {
        tourney_remove_logo($post_id);
    }
}
add_action('before_delete_post', 'tourney_logo_delete_post');
