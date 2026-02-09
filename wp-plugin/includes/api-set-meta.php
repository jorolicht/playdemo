<?php

add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/set-meta/(?P<path>.+)', [
        'methods'  => 'POST',
        'callback' => 'trny_update_meta_handler',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        },
        'args' => [
            'path' => [
                'sanitize_callback' => 'sanitize_text_field'
            ],
        ],
    ]);
});

function trny_update_meta_handler($request) {
    $path = ltrim($request['path'], '/');
    $key  = $request->get_param('key');

    // den rohen Body-Inhalt als String holen
    $raw_json_string = $request->get_body();

    if (empty($raw_json_string)) {
        return new WP_Error('missing_data', 'Der Request-Body ist leer.', ['status' => 400]);
    }


    // Alternative Suche über WP_Query (findet auch Entwürfe und ist flexibler)
    $args = [
        'name'        => basename($path), // Der reine Slug
        'post_type'   => ['tourney', 'page'],
        'post_status' => ['publish', 'draft', 'pending', 'private'],
        'posts_per_page' => 1
    ];
    
    $loop = new WP_Query($args);
    $page = $loop->posts[0] ?? null;

    if (!$page) {
        return new WP_Error('not_found', "Seite '$path' nicht im System.", ['status' => 404]);
    }

    update_post_meta($page->ID, $key, $raw_json_string);

    return [
        'status' => 'success',
        'updated_id' => $page->ID,
        'path_processed' => $path,
        'key_used' => $key
    ];
}


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
        return new WP_Error('not_found', 'Seite nicht gefunden', ['status' => 404]);
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