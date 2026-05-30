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

});

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
    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
    }

    $meta_ver = $meta . "_ts";
    $version = intval(get_post_meta($post_id, $meta_ver, true));

    $tourney_json = get_post_meta($post_id, $meta, true);
    $tourney = $tourney_json ? json_decode($tourney_json, true) : null;

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

    $post_id = intval($request->get_param('postId'));
    $meta = $request->get_param('metafield-name') ?: 'basic';

    if (!$post_id) {
        return ApiHelper::error("missing_param", "Missing parameters", "", "", HttpStatus::BAD_REQUEST);
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
        update_post_meta($post_id, $meta, wp_json_encode($new_tourney, JSON_UNESCAPED_UNICODE));
        
        // Index meta fields for searching if this is the basic meta
        if ($meta === 'basic') {
            // Update Post Title if name is present
            if (!empty($new_tourney['name'])) {
                wp_update_post([
                    'ID'         => $post_id,
                    'post_title' => $new_tourney['name']
                ]);
            }

            if (isset($new_tourney['startDate'])) {
                update_post_meta($post_id, 'startDate', intval($new_tourney['startDate']));
            }
            if (isset($new_tourney['endDate'])) {
                update_post_meta($post_id, 'endDate', intval($new_tourney['endDate']));
            }
            if (isset($new_tourney['ident'])) {
                update_post_meta($post_id, 'ident', sanitize_text_field($new_tourney['ident']));
            }

            // Organizer logic with hierarchical fallback
            $organizer = $new_tourney['organizer'] ?? '';
            if (empty($organizer)) {
                $author_id = get_post_field('post_author', $post_id);
                $organizer = get_user_meta($author_id, 'organizer', true);
                if (empty($organizer)) {
                    $user_data = get_userdata($author_id);
                    if ($user_data) {
                        $organizer = !empty($user_data->display_name) ? $user_data->display_name : $user_data->user_login;
                    } else {
                        $organizer = 'user';
                    }
                }
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

    // Aktualisiere die ID im Body, damit sie mit der WordPress Post-ID übereinstimmt
    $body['id'] = $result_id;

    // Speichere den gesamten Payload als initialen Stand in 'basic'
    update_post_meta($result_id, 'basic', wp_json_encode($body, JSON_UNESCAPED_UNICODE));
    
    // Index meta fields for searching
    update_post_meta($result_id, 'startDate', intval($start_date));
    update_post_meta($result_id, 'organizer', sanitize_text_field($body['organizer'] ?? $organizer));
    if (isset($body['endDate'])) {
        update_post_meta($result_id, 'endDate', intval($body['endDate']));
    }

    // Initialisiere Version auf 1, falls neu
    if ($action === 'created') {
        update_post_meta($result_id, 'basic_ts', 1);
    }

    // Setze ident Metafield
    if (!empty($ident)) {
        update_post_meta($result_id, 'ident', $ident);
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
        $start_date = get_post_meta($post->ID, 'startDate', true);
        $org_meta   = get_post_meta($post->ID, 'organizer', true);
        
        $basic_json = get_post_meta($post->ID, 'basic', true);
        $basic = $basic_json ? json_decode($basic_json, true) : null;
        
        // Fallbacks für Altdaten aus dem JSON
        if (empty($start_date) && $basic && isset($basic['startDate'])) {
            $start_date = $basic['startDate'];
        }
        if (empty($org_meta) && $basic && isset($basic['organizer'])) {
            $org_meta = $basic['organizer'];
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

        // Organisator Fallback über Autor (wie zuvor)
        if (empty($org_meta)) {
            $author_id = $post->post_author;
            $user_org  = get_user_meta($author_id, 'organizer', true);
            if (!empty($user_org)) {
                $org_meta = $user_org;
            } else {
                $user_data = get_userdata($author_id);
                if ($user_data) {
                    $org_meta = !empty($user_data->display_name) ? $user_data->display_name : $user_data->user_login;
                }
            }
        }
        
        $status = $basic['status'] ?? 'Active';

        $results[] = [
            'id'        => $post->ID,
            'name'      => $post->post_title,
            'organizer' => $org_meta ?: 'Unbekannt',
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
