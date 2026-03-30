<?php


add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/get-meta/(?P<path>.+)', [
        'methods'  => 'GET',
        'callback' => 'trny_get_meta_handler',
        'permission_callback' => '__return_true', // Öffentlich lesbar oder 'current_user_can' für Schutz
    ]);
});

function trny_get_meta_handler($request) {
    $path = ltrim($request['path'], '/');
    $key  = $request->get_param('key');

    // Seite finden
    $page = get_page_by_path($path, OBJECT, ['tourney', 'page']);

    if (!$page) {
        return ApiHelper::error("not_found", "Seite nicht gefunden", "", "", HttpStatus::NOT_FOUND);
    }

    // Meta-Wert auslesen
    $meta_value = get_post_meta($page->ID, $key, true);

    // Falls der Inhalt ein JSON-String ist, dekodieren wir ihn für die API-Antwort,
    // damit die REST API ein sauberes JSON-Objekt zurückgibt statt eines "escapten" Strings.
    $decoded_value = json_decode($meta_value);
    $final_value = (json_last_error() === JSON_ERROR_NONE) ? $decoded_value : $meta_value;

    // return [
    //     'path'  => $path,
    //     'key'   => $key,
    //     'value' => $final_value
    // ];

    return 
      $final_value
    ; 
}



add_action('rest_api_init', function () {

    register_rest_route('tourney/v1', '/update-meta/(?P<postId>\d+)/(?P<cptMetaName>[a-zA-Z0-9_\-]+)', [
        'methods'  => 'POST',
        'callback' => 'trny_update_meta',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

});


function trny_update_meta(WP_REST_Request $request)
{
    $data = $request->get_json_params();

    if (!$data || !isset($data['timestamp'])) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Invalid payload'
        ], 400);
    }

    // URL Parameter lesen
    $metaName = sanitize_text_field($request->get_param('cptMetaName'));
    $postId   = intval($request->get_param('postId'));

    $timestamp = intval($data['timestamp'] ?? 0);

    if (!$metaName || !$postId) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Missing required fields'
        ], 400);
    }

    // Sicherstellen dass es der richtige CPT ist
    if (get_post_type($postId) !== 'tourney') {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Invalid CPT type'
        ], 400);
    }

    $shadowMeta = $metaName . '_ts';
    $storedTimestamp = intval(get_post_meta($postId, $shadowMeta, true));

    // 🔒 Optimistic locking
    if ($storedTimestamp !== $timestamp) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Timestamp mismatch',
            'stored_timestamp' => $storedTimestamp
        ], 409);
    }

    // JSON speichern
    update_post_meta($postId, $metaName, wp_json_encode($data));

    // neuen Timestamp setzen
    $newTimestamp = time();
    update_post_meta($postId, $shadowMeta, $newTimestamp);

    return new WP_REST_Response([
        'success' => true,
        'new_timestamp' => $newTimestamp
    ], 200);
}
