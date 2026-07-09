<?php

/**
 * Registriert die REST-API-Endpunkte für die Turnier-Verwaltung.
 */
add_action('rest_api_init', function () {

    // GET /tourney/v1/read - Gibt die Turnierdaten zurück
    register_rest_route('tourney/v1', '/read', [
        'methods' => 'GET',
        'callback' => 'tourney_get_read',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/tourney-sync - Synchronisiert die Turnierdaten
    register_rest_route('tourney/v1', '/tourney-sync', [
        'methods' => 'POST',
        'callback' => 'tourney_sync_tourney',
        'permission_callback' => '__return_true'
    ]);

    // GET /tourney/v1/organizers - Gibt alle Parent-Posts (Organisatoren) zurück
    register_rest_route('tourney/v1', '/organizers', [
        'methods' => 'GET',
        'callback' => 'tourney_get_organizers',
        'permission_callback' => '__return_true'
    ]);

    // GET /tourney/v1/search - Sucht nach Turnieren
    register_rest_route('tourney/v1', '/search', [
        'methods' => 'GET',
        'callback' => 'tourney_api_search',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/create
    register_rest_route('tourney/v1', '/create', [
        'methods'  => 'POST',
        'callback' => 'tourney_api_create',
        'permission_callback' => '__return_true'
    ]);

    // GET /tourney/v1/meta-data
    register_rest_route('tourney/v1', '/meta-data', [
        'methods' => 'GET',
        'callback' => 'tourney_get_meta_data',
        'permission_callback' => '__return_true'
    ]);

    // POST /tourney/v1/meta-data
    register_rest_route('tourney/v1', '/meta-data', [
        'methods' => 'POST',
        'callback' => 'tourney_update_meta_data',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

    // DELETE /tourney/v1/delete
    register_rest_route('tourney/v1', '/delete', [
        'methods' => 'DELETE',
        'callback' => 'tourney_api_delete',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

    // POST /tourney/v1/convert-to-page
    register_rest_route('tourney/v1', '/convert-to-page', [
        'methods' => 'POST',
        'callback' => 'tourney_api_convert_to_page',
        'permission_callback' => function () {
            return current_user_can('edit_posts');
        }
    ]);

});

/**
 * Wandelt ein Turnier (CPT 'tourney') in eine normale Seite ('page') um.
 */
function tourney_api_convert_to_page(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $post = get_post($post_id);
    if (!$post || $post->post_type !== 'tourney') {
        return ApiHelper::error("invalid_type", "Post existiert nicht oder ist kein Turnier", "", "", HttpStatus::BAD_REQUEST);
    }

    // Post in eine Seite umwandeln und aus der Turnier-Hierarchie lösen
    $result = wp_update_post([
        'ID'          => $post_id,
        'post_type'   => 'page',
        'post_parent' => 0
    ]);

    if (is_wp_error($result)) {
        return ApiHelper::error("update_failed", $result->get_error_message(), "", "", HttpStatus::INTERNAL_SERVER_ERROR);
    }

    return [
        'success'  => true,
        'new_type' => 'page',
        'post_id'  => $post_id
    ];
}

/**
 * Löscht ein Turnier unwiderruflich.
 */
function tourney_api_delete(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    if (get_post_type($post_id) !== 'tourney') {
        return ApiHelper::error("invalid_type", "Post is not a tournament", "", "", HttpStatus::BAD_REQUEST);
    }

    // Unwiderruflich löschen
    $result = wp_delete_post($post_id, true);

    if (!$result) {
        return ApiHelper::error("delete_failed", "Konnte Turnier nicht löschen", "", "", HttpStatus::INTERNAL_SERVER_ERROR);
    }

    return ['success' => true];
}

/**
 * Hilfsfunktion zum Abrufen des Organisators mit Fallback.
 */
function tourney_get_organizer_fallback($post_id) {
    $organizer = get_post_meta($post_id, 'organizer', true);
    if (!empty($organizer)) {
        return $organizer;
    }
    
    $post = get_post($post_id);
    if (!$post) return 'Unbekannt';
    
    $author_id = $post->post_author;
    $user_org = get_user_meta($author_id, 'organizer', true);
    if (!empty($user_org)) {
        return $user_org;
    }
    
    $user_data = get_userdata($author_id);
    if ($user_data) {
        return !empty($user_data->display_name) ? $user_data->display_name : $user_data->user_login;
    }
    
    return 'Unbekannt';
}

/**
 * Gibt die speziellen Meta-Felder zurück.
 */
function tourney_get_meta_data(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }
    
    return [
        'startDate' => get_post_meta($post_id, 'startDate', true),
        'endDate'   => get_post_meta($post_id, 'endDate', true),
        'ident'     => get_post_meta($post_id, 'ident', true),
        'category'  => get_post_meta($post_id, 'category', true),
        'organizer' => tourney_get_organizer_fallback($post_id),
        'clicktt'   => get_post_meta($post_id, 'clicktt', true),
    ];
}

/**
 * Aktualisiert die speziellen Meta-Felder.
 */
function tourney_update_meta_data(WP_REST_Request $request) {
    $post_id = ApiHelper::getPostId($request);
    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $params = $request->get_json_params();
    
    if (isset($params['startDate'])) update_post_meta($post_id, 'startDate', intval($params['startDate']));
    if (isset($params['endDate']))   update_post_meta($post_id, 'endDate',   intval($params['endDate']));
    if (isset($params['ident']))     update_post_meta($post_id, 'ident',     sanitize_text_field($params['ident']));
    if (isset($params['category']))  update_post_meta($post_id, 'category',  sanitize_text_field($params['category']));
    if (isset($params['organizer'])) update_post_meta($post_id, 'organizer', sanitize_text_field($params['organizer']));
    if (isset($params['clicktt']))   update_post_meta($post_id, 'clicktt',   $params['clicktt']);
    
    return ['success' => true];
}

/**
 * Gibt eine Liste aller Organisatoren (Parent-Posts) zurück.
 */
function tourney_get_organizers(WP_REST_Request $request) {
    $parents = get_posts([
        'post_type'      => 'tourney',
        'post_parent'    => 0,
        'post_status'    => 'publish',
        'posts_per_page' => -1,
        'orderby'        => 'title',
        'order'          => 'ASC'
    ]);

    $result = [];
    foreach ($parents as $parent) {
        // Zähle Kinder
        $children = get_posts([
            'post_type'   => 'tourney',
            'post_parent' => $parent->ID,
            'post_status' => 'publish',
            'posts_per_page' => -1,
            'fields'      => 'ids'
        ]);

        $result[] = [
            'id'    => $parent->ID,
            'title' => $parent->post_title,
            'slug'  => $parent->post_name,
            'count' => count($children)
        ];
    }

    return $result;
}

/**
 * Gibt die Turnierdaten für eine bestimmte Post-ID zurück.
 */
function tourney_get_read(WP_REST_Request $request)
{
    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ver = $meta . "_ts";
    $version = intval(get_post_meta($post_id, $meta_ver, true));

    $tourney_json = get_post_meta($post_id, $meta, true);
    $tourney = $tourney_json ? json_decode($tourney_json, true) : null;
    
    // Migration & Consistency: Ensure wpId and organizer are correct and legacy id is removed
    if ($tourney && $meta === 'basic') {
        $tourney['wpId'] = intval($post_id);
        if (isset($tourney['id'])) unset($tourney['id']);
        
        if (empty($tourney['organizer'])) {
            $tourney['organizer'] = tourney_get_organizer_fallback($post_id);
        }
        
        $clicktt_val = get_post_meta($post_id, 'clicktt', true);
        $tourney['clicktt'] = !empty($clicktt_val) ? $clicktt_val : ($tourney['clicktt'] ?? '');
        
        $post = get_post($post_id);
        if ($post) {
            $parent_slug = 'unbekannt';
            if ($post->post_parent) {
                $parent_post = get_post($post->post_parent);
                if ($parent_post) {
                    $parent_slug = $parent_post->post_name;
                }
            }
            $tourney['slug'] = "tourney/" . $parent_slug . "/" . $post->post_name;
        }
    }

    return [
        "version" => $version,
        "tourney" => $tourney
    ];
}

/**
 * Synchronisiert die Turnierdaten mit globalem optimistic locking.
 */
function tourney_sync_tourney(WP_REST_Request $request)
{
    if (!current_user_can('edit_posts')) {
        return ApiHelper::error("auth_required", "Sie müssen angemeldet sein, um diese Aktion auszuführen.", "", "tourney_sync_tourney", HttpStatus::UNAUTHORIZED);
    }

    $post_id = ApiHelper::getPostId($request);
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Post ID or Slug missing", "", "", HttpStatus::BAD_REQUEST);
    }

    $body = json_decode($request->get_body(), true);

    if (!$body) {
        return ApiHelper::error("invalid_body", "Invalid JSON", "", "", HttpStatus::BAD_REQUEST);
    }

    $client_ver = intval($body["version"] ?? 0);
    $new_tourney = $body["tourney"] ?? null;
    
    $meta_ver = $meta . "_ts";
    $stored_ver = intval(get_post_meta($post_id, $meta_ver, true));

    // 🔒 Optimistic locking Check
    if ($stored_ver !== 0 && $client_ver !== $stored_ver) {
        return ApiHelper::error(
            "version_mismatch", 
            "Tournament data has been modified by another user.", 
            "Stored Version: $stored_ver, Sent Version: $client_ver", 
            "tourney_sync_tourney", 
            HttpStatus::CONFLICT
        );
    }

    // Neue Version
    $next_ver = $stored_ver + 1;

    // 💾 Speichern
    if ($new_tourney) {
        if ($meta === 'basic') {
            if (isset($new_tourney['clicktt'])) {
                update_post_meta($post_id, 'clicktt', wp_slash($new_tourney['clicktt']));
                $new_tourney['clicktt'] = '';
            }
        }
        update_post_meta($post_id, $meta, wp_slash(wp_json_encode($new_tourney, JSON_UNESCAPED_UNICODE)));
        
        // Index meta fields for searching if this is the basic meta
        if ($meta === 'basic') {
            // 1. Seitentitel synchronisieren
            if (!empty($new_tourney['name'])) {
                wp_update_post([
                    'ID'         => $post_id,
                    'post_title' => $new_tourney['name']
                ]);
            }

            // 2. Einzelne Metafelder für Abfragen synchronisieren
            if (isset($new_tourney['startDate'])) {
                update_post_meta($post_id, 'startDate', intval($new_tourney['startDate']));
            }
            if (isset($new_tourney['endDate'])) {
                update_post_meta($post_id, 'endDate', intval($new_tourney['endDate']));
            }
            if (isset($new_tourney['ident'])) {
                update_post_meta($post_id, 'ident', sanitize_text_field($new_tourney['ident']));
            }
            if (isset($new_tourney['category'])) {
                update_post_meta($post_id, 'category', sanitize_text_field($new_tourney['category']));
            }
            if (isset($new_tourney['clicktt'])) {
                update_post_meta($post_id, 'clicktt', $new_tourney['clicktt']);
            }

            // Organizer Logik
            $organizer = $new_tourney['organizer'] ?? '';
            if (empty($organizer)) {
                $author_id = get_post_field('post_author', $post_id);
                $organizer = get_user_meta($author_id, 'organizer', true);
            }
            update_post_meta($post_id, 'organizer', sanitize_text_field($organizer));
        }
    } else {
        delete_post_meta($post_id, $meta);
    }
    update_post_meta($post_id, $meta_ver, $next_ver);

    return [
        "version" => $next_ver
    ];
}

/**
 * Erstellt ein neues Turnier (CPT 'tourney') mit einem vorgegebenen Slug
 * oder aktualisiert ein bestehendes, falls der Slug bereits existiert.
 *
 * @param WP_REST_Request $request
 * @return array|WP_REST_Response
 */
function tourney_api_create(WP_REST_Request $request) {
    if (!current_user_can('edit_posts')) {
        return ApiHelper::error("auth_required", "Sie müssen angemeldet sein, um diese Aktion auszuführen.", "", "tourney_api_create", HttpStatus::UNAUTHORIZED);
    }

    $body = json_decode($request->get_body(), true);

    if (empty($body)) {
        return ApiHelper::error("missing_payload", "JSON-Payload ist erforderlich.", "", "tourney_api_create", HttpStatus::BAD_REQUEST);
    }

    $tourney_name = $body['name'] ?? '';
    $start_date   = $body['startDate'] ?? '';
    $ident        = $body['ident'] ?? '';

    if (empty($tourney_name) || empty($start_date)) {
        return ApiHelper::error("missing_params", "Name und Startdatum sind erforderlich.", "", "tourney_api_create", HttpStatus::BAD_REQUEST);
    }

    $current_user = wp_get_current_user();
    $username = ($current_user && $current_user->exists()) ? $current_user->user_login : '';
    $organizer = ($current_user && $current_user->exists()) ? get_user_meta($current_user->ID, 'organizer', true) : '';

    // Parent slug selection logic: organizer > username > 'admin'
    $parent_base = !empty($organizer) ? $organizer : (!empty($username) ? $username : 'admin');
    $parent_slug = sanitize_title($parent_base);
    $parent_title = !empty($organizer) ? $organizer : (!empty($username) ? $username : 'admin');

    $parent_id = 0;

    $existing_parents = get_posts([
        'name'           => $parent_slug,
        'post_type'      => 'tourney',
        'post_parent'    => 0,
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    if (!empty($existing_parents)) {
        $parent_id = $existing_parents[0]->ID;
    } else {
        $parent_id = wp_insert_post([
            'post_title'  => $parent_title,
            'post_name'   => $parent_slug,
            'post_type'   => 'tourney',
            'post_status' => 'publish',
        ]);
    }

    // Generiere den Turnier-Slug aus Datum und Name: <jjjjMMdd>-<name>
    $slug_base = $start_date . '-' . $tourney_name;
    $turnier_slug = sanitize_title($slug_base);

    // Prüfen, ob bereits ein Turnier mit diesem Slug unter diesem Parent existiert
    $existing_posts = get_posts([
        'name'           => $turnier_slug,
        'post_type'      => 'tourney',
        'post_parent'    => $parent_id,
        'post_status'    => 'any',
        'posts_per_page' => 1,
    ]);

    $post_data = [
        'post_title'   => $tourney_name,
        'post_name'    => $turnier_slug,
        'post_parent'  => $parent_id,
        'post_content' => '[playdemo mode="multi"]',
        'post_type'    => 'tourney',
        'post_status'  => 'publish',
    ];

    if (!empty($existing_posts)) {
        // Aktualisieren
        $post_data['ID'] = $existing_posts[0]->ID;
        $result_id = wp_update_post($post_data);
        $action = 'updated';
    } else {
        // Neu erstellen
        $result_id = wp_insert_post($post_data);
        $action = 'created';
    }

    if (is_wp_error($result_id)) {
        return ApiHelper::error("db_error", $result_id->get_error_message(), "", "tourney_api_create_tourney", HttpStatus::INTERNAL_SERVER_ERROR);
    }

    // Aktualisiere die ID im Body, damit sie mit der WordPress Post-ID übereinstimmt (wpId für Scala Model)
    $body['wpId'] = $result_id;

    // Speichere den gesamten Payload als initialen Stand in 'basic'
    update_post_meta($result_id, 'basic', wp_slash(wp_json_encode($body, JSON_UNESCAPED_UNICODE)));
    
    // Index meta fields for searching
    update_post_meta($result_id, 'startDate', intval($start_date));
    update_post_meta($result_id, 'organizer', sanitize_text_field($body['organizer'] ?? $organizer));
    if (isset($body['endDate'])) {
        update_post_meta($result_id, 'endDate', intval($body['endDate']));
    }
    if (isset($body['category'])) {
        update_post_meta($result_id, 'category', sanitize_text_field($body['category']));
    }

    // Initialisiere Version auf 1, falls neu
    if ($action === 'created') {
        update_post_meta($result_id, 'basic_ts', 1);
    }

    // Setze ident Metafield
    if (!empty($ident)) {
        update_post_meta($result_id, 'ident', $ident);
    }

    // Setze clicktt Metafield
    if (isset($body['clicktt'])) {
        update_post_meta($result_id, 'clicktt', $body['clicktt']);
    }

    // Full hierarchical slug for the response
    $full_slug = "tourney/" . $parent_slug . "/" . $turnier_slug;

    return [
        'success'  => true,
        'action'   => $action,
        'pageId'   => $result_id,
        'parentId' => $parent_id,
        'username' => $username,
        'organizer'=> $organizer,
        'slug'     => $full_slug,
        'version'  => intval(get_post_meta($result_id, 'basic_ts', true))
    ];
}

/**
 * Sucht nach Turnieren basierend auf verschiedenen Kriterien.
 */
function tourney_api_search(WP_REST_Request $request) {
    $q         = $request->get_param('q');
    $organizer = $request->get_param('organizer');
    $date_from = $request->get_param('dateFrom');
    $order     = strtoupper($request->get_param('order') ?? 'DESC');

    $args = [
        'post_type'      => 'tourney',
        'post_status'    => ['publish', 'private'],
        'posts_per_page' => -1, // Wir holen alle und filtern/limitieren in PHP für maximale Kompatibilität
        'post_parent__not_in' => [0],
        'orderby'        => 'date',
        'order'          => 'DESC'
    ];

    if (!empty($q)) {
        $args['s'] = $q;
    }

    $query = new WP_Query($args);
    $posts = $query->posts;

    $results = [];
    foreach ($posts as $post) {
        // Filter out templates
        if (stripos(trim($post->post_title), 'template') === 0 || stripos(trim($post->post_name), 'template') === 0) {
            continue;
        }

        $start_date = get_post_meta($post->ID, 'startDate', true);
        $org_meta   = tourney_get_organizer_fallback($post->ID);
        
        $basic_json = get_post_meta($post->ID, 'basic', true);
        $basic = $basic_json ? json_decode($basic_json, true) : null;
        
        // Fallbacks für Altdaten aus dem JSON
        if (empty($start_date) && $basic && isset($basic['startDate'])) {
            $start_date = $basic['startDate'];
        }

        $start_date_int = intval($start_date);

        // 1. Filter: Nur Turniere mit gültigem Startdatum (> 0)
        if ($start_date_int <= 0) {
            continue;
        }

        // 2. Filter: Startdatum ab (falls angegeben)
        if (!empty($date_from) && $start_date_int < intval($date_from)) {
            continue;
        }

        // 3. Filter: Organisator (falls angegeben)
        if (!empty($organizer) && stripos($org_meta, $organizer) === false) {
            continue;
        }

        $status = $basic['status'] ?? 'Active';

        $results[] = [
            'id'        => $post->ID,
            'name'      => $post->post_title,
            'organizer' => $org_meta,
            'startDate' => $start_date_int,
            'status'    => $status,
            'slug'      => $post->post_name
        ];
    }

    // 4. Sortierung in PHP
    usort($results, function($a, $b) use ($order) {
        if ($a['startDate'] == $b['startDate']) return 0;
        if ($order === 'ASC') {
            return ($a['startDate'] < $b['startDate']) ? -1 : 1;
        } else {
            return ($a['startDate'] > $b['startDate']) ? -1 : 1;
        }
    });

    // 5. Limitierung auf 100 Ergebnisse
    return array_slice($results, 0, 100);
}
