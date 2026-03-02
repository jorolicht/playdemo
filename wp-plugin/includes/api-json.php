<?php
/**
 * Registriert eine benutzerdefinierte REST-API-Route zum Speichern von JSON-Daten als benutzerdefinierte Beiträge.
 */ 

add_action('rest_api_init', function () {
    // Route: /save-json/{slug}/{object_type}
    register_rest_route('mein-tool/v1', '/save-json/(?P<slug>[a-zA-Z0-9-_]+)/(?P<object_type>[a-zA-Z0-9-_]+)', array(
        'methods' => 'POST',
        'callback' => 'handle_generic_json_post',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ));
});


add_action('rest_api_init', function () {
    
    // 1. ENDPUNKT: SPEICHERN (POST)
    register_rest_route('tourney/v1', '/save-json/(?P<slug>[a-zA-Z0-9-_]+)/(?P<object_type>[a-zA-Z0-9-_]+)', array(
        'methods' => 'POST',
        'callback' => 'handle_generic_json_post',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ));

    // 2. ENDPUNKT: AUSLESEN (GET)
    // Dieser Teil registriert die Route für den Abruf
    register_rest_route('tourney/v1', '/get-json/(?P<slug>[a-zA-Z0-9-_]+)', array(
        'methods' => 'GET',
        'callback' => 'handle_get_json_by_slug',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ));
    // Route: /save-json/{hierarchical-path}
    // Beispiel: /projekte/web-2026/kunde-person
    register_rest_route('tourney/v1', '/save-json/(?P<path>.+)', array(
        'methods' => 'POST',
        'callback' => 'handle_hierarchical_json_by_path',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ));


});

/**
 * Callback für den GET-Endpunkt
 */
function handle_get_json_by_slug($request) {
    $slug = $request['slug'];

    // Suche nach dem Post im Custom Post Type 'playdemo'
    $posts = get_posts(array(
        'name'           => $slug,
        'post_type'      => 'playdemo',
        'post_status'    => 'any', // Damit auch 'private' gefunden wird
        'posts_per_page' => 1,
    ));

    if (empty($posts)) {
        return new WP_Error('not_found', 'Eintrag mit diesem Slug existiert nicht.', array('status' => 404));
    }

    $post = $posts[0];
    
    // Meta-Daten auslesen (den Typ haben wir beim POST gespeichert)
    $object_type = get_post_meta($post->ID, 'object_typ', true);
    
    // Den JSON-Inhalt aus dem post_content dekodieren
    $raw_data = json_decode($post->post_content, true);

    // Antwort-Struktur bauen
    return new WP_REST_Response(array(
        'slug'        => $post->post_name,
        'object_typ'  => $object_type,
        'inhalt'      => array(
            $object_type => $raw_data
        ),
        'last_updated' => $post->post_modified
    ), 200);
}


function handle_generic_json_post($request) {
    $slug = $request['slug'];
    $object_type = $request['object_type'];
    $params = $request->get_json_params();

    $data = $params['inhalt'][$object_type] ?? null;

    if (!$data) {
        return new WP_Error('missing_object', "Objekt '{$object_type}' fehlt im Inhalt.", array('status' => 400));
    }

    $post_data = array(
        'post_title'   => sanitize_text_field($params['titel'] ?? ucfirst($object_type) . ": " . ($data['name'] ?? $slug)),
        'post_name'    => $slug,
        'post_content' => json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), // Nur das JSON
        'post_status'  => 'private',
        'post_type'    => 'playdemo',
    );

    $existing = get_posts(array(
        'name' => $slug, 'post_type' => 'playdemo', 'post_status' => 'any', 'numberposts' => 1
    ));

    if (!empty($existing)) {
        $post_data['ID'] = $existing[0]->ID;
        $post_id = wp_update_post($post_data);
    } else {
        $post_id = wp_insert_post($post_data);
    }

    // Speichern des Objekt-Typs in ein dediziertes Meta-Feld
    update_post_meta($post_id, 'object_typ', $object_type);
    
    // Die eigentlichen Daten als strukturiertes Meta (optional, für schnellere Abfragen)
    update_post_meta($post_id, '_api_data_json', $data);

    return new WP_REST_Response(array('success' => true, 'post_id' => $post_id, 'type_stored' => $object_type), 200);
}


function handle_hierarchical_json_by_path($request) {
    $path = trim($request['path'], '/');
    $params = $request->get_json_params();

    // 1. Letzten Teil des Pfades extrahieren (z.B. "kunde-person")
    $path_parts = explode('/', $path);
    $last_segment = end($path_parts);

    // 2. Object Type aus dem letzten Segment extrahieren (alles nach dem letzten Bindestrich)
    // "kunde-person" -> "person"
    if (strpos($last_segment, '_') === false) {
        return new WP_Error('invalid_format', 'Slug muss Format "prefix-objecttype" haben.', array('status' => 400));
    }
    
    $object_type = substr($last_segment, strrpos($last_segment, '_') + 1);
    
    // Daten aus dem JSON holen anhand des extrahierten Typs
    $data = $params['content'][$object_type] ?? null;

    if (!$data) {
        return new WP_Error('no_data', "Objekt '{$object_type}' nicht im JSON gefunden.", array('status' => 400));
    }

    // 3. Hierarchie durchlaufen / erstellen
    $parent_id = 0;
    foreach ($path_parts as $segment) {
        $existing = get_posts(array(
            'name'        => $segment,
            'post_type'   => 'playdemo',
            'post_parent' => $parent_id,
            'post_status' => 'any',
            'numberposts' => 1
        ));

        if (!empty($existing)) {
            $parent_id = $existing[0]->ID;
        } else {
            $parent_id = wp_insert_post(array(
                'post_title'  => ucfirst(str_replace('_', ' ', $segment)),
                'post_name'   => $segment,
                'post_parent' => $parent_id,
                'post_type'   => 'playdemo',
                'post_status' => 'publish'
            ));
        }
    }

    // 4. Finalen Post aktualisieren
    $post_id = $parent_id;
    wp_update_post(array(
        'ID'           => $post_id,
        'post_title'   => sanitize_text_field($params['titel'] ?? ucfirst($last_segment)),
        'post_content' => json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE),
    ));

    update_post_meta($post_id, 'object_typ', $object_type);

    return new WP_REST_Response(array(
        'success'       => true,
        'post_id'       => $post_id,
        'detected_type' => $object_type,
        'path'          => $path
    ), 200);
}



// 2. API-Route: Meta-Daten aktualisieren mit Timestamp-Check
add_action('rest_api_init', function () {

    register_rest_route('tourney/v1', '/update-meta', [
        'methods'  => 'POST',
        'callback' => 'update_tourney_meta',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

});

function update_tourney_meta(WP_REST_Request $request)
{
    $data = $request->get_json_params();

    if (!$data || !isset($data['wp'])) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Invalid payload'
        ], 400);
    }

    $metaName  = sanitize_key($data['wp']['cptMetaName']);
    $timestamp = intval($data['wp']['timestamp']);
    $postId    = intval($data['id']);

    if (!$metaName || !$postId) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Missing required fields'
        ], 400);
    }

    // Check CPT type
    if (get_post_type($postId) !== 'tourney') {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Invalid CPT'
        ], 400);
    }

    $shadowMeta = $metaName . '_ts';
    $storedTimestamp = intval(get_post_meta($postId, $shadowMeta, true));

    // 🔒 Timestamp check
    if ($storedTimestamp !== $timestamp) {
        return new WP_REST_Response([
            'success' => false,
            'message' => 'Timestamp mismatch',
            'stored_timestamp' => $storedTimestamp
        ], 409);
    }

    // Update meta with full JSON payload
    update_post_meta($postId, $metaName, wp_json_encode($data));

    // Update shadow timestamp to current time
    $newTimestamp = time();
    update_post_meta($postId, $shadowMeta, $newTimestamp);

    return new WP_REST_Response([
        'success' => true,
        'new_timestamp' => $newTimestamp
    ], 200);
}

// Wie du die Seite aufrufbar machst
// Falls du die Daten doch im Browser (z. B. zu Debugging-Zwecken) sehen möchtest, müsstest du zwei Dinge ändern:
// A) Die CPT-Registrierung anpassen: Du musst den Post-Type "abfragbar" machen.
// PHP
// 'public'             => true,  // Macht ihn grundsätzlich öffentlich
// 'publicly_queryable' => true,  // Erlaubt den Aufruf der URL
// 'exclude_from_search'=> false, // Erscheint dann auch in der Suche
//
// B) Den Beitragsstatus ändern: In deinem API-Code müsstest du den Status von private auf publish setzen:
//
// PHP
// 'post_status' => 'publish',