<?php

function trny_custom_post_type() {
    register_post_type('tourney',
        array(
            'labels'      => array(
                'name'          => 'Tourneys',
                'singular_name' => 'Tourney',
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
add_action('init', 'trny_custom_post_type');


function register_trny_custom_meta_fields() {
    $meta_fields = [
        'tourney'       => 'Turniergrunddaten',
        'clubs'         => 'Vereine',
        'players'       => 'Spieler',
        'startdate'     => 'StartDatum',
        'enddate'       => 'EndDatum',
        'ident'         => 'Identifikationsnummer',
        'organizer'     => 'Organisator'
    ];

    // Stages 001–128 hinzufügen
    for ($i = 1; $i <= 128; $i++) {
        $key = sprintf('stage%03d', $i);
        $meta_fields[$key] = 'Stage ' . $i;
    }

    // Wettbewerbe 01–64 hinzufügen
    for ($i = 1; $i <= 64; $i++) {
        $key = sprintf('competition%02d', $i);
        $meta_fields[$key] = 'Wettbewerbe ' . $i;
    }    

    foreach ( $meta_fields as $key => $description ) {

        // Hauptfeld
        register_post_meta( 'tourney', $key, array(
            'show_in_rest'      => true,
            'single'            => true,
            'type'              => 'string',
            'auth_callback'     => function() {
                return current_user_can( 'edit_posts' );
            },
            'sanitize_callback' => null,
            'description'       => $description,
        ) );

        // Shadow Timestamp Feld
        register_post_meta( 'tourney', "_{$key}_ts", array(
            'show_in_rest'      => true,
            'single'            => true,
            'type'              => 'integer',
            'auth_callback'     => function() {
                return current_user_can( 'edit_posts' );
            },
            'sanitize_callback' => 'absint',
            'description'       => $description . ' Timestamp',
        ) );
    }
}
add_action( 'rest_api_init', 'register_trny_custom_meta_fields' );


add_action('rest_api_init', function () {
    register_rest_route('tourney/v1', '/convert-to-cpt', [
        'methods' => 'POST',
        'permission_callback' => function () {
            return current_user_can('edit_posts'); // oder strenger!
        },
        'callback' => function ($request) {

            $post_id = $request->get_param('post_id');
            $slug = $request->get_param('slug');
            $target_type = $request->get_param('target_type');

            if (!$post_id && !$slug) {
                return ApiHelper::error("missing_params", "post_id oder slug fehlt", "", "", HttpStatus::BAD_REQUEST);
            }

            if (!$target_type) {
                return ApiHelper::error("missing_params", "target_type fehlt", "", "", HttpStatus::BAD_REQUEST);
            }

            // Find post by slug if no ID provided
            if (!$post_id && $slug) {
                $args = [
                    'name'        => $slug,
                    'post_type'   => ['page', 'post'], // Search in pages and posts
                    'post_status' => 'any',
                    'numberposts' => 1
                ];
                $posts = get_posts($args);
                if (!empty($posts)) {
                    $post_id = $posts[0]->ID;
                }
            }

            if (!$post_id) {
                return ApiHelper::error("not_found", "Post nicht gefunden (ID oder Slug ungültig)", "", "", HttpStatus::NOT_FOUND);
            }

            $post = get_post($post_id);

            if (!$post) {
                return ApiHelper::error("not_found", "Post nicht gefunden", "", "", HttpStatus::NOT_FOUND);
            }

            // Update
            wp_update_post([
                'ID' => $post_id,
                'post_type' => $target_type
            ]);

            return [
                'success' => true,
                'post_id' => $post_id,
                'new_type' => $target_type
            ];
        }
    ]);
});

add_action('rest_api_init', function () {
    // Registriert das Feld 'translations' für Seiten (page) und Beiträge (post)
    // Falls du Custom Post Types hast, erweitere das Array, z.B. ['page', 'post', 'turnier']
    register_rest_field(['page', 'post'], 'translations', [
        'get_callback' => function ($post_array) {
            $post_id = $post_array['id'];
            
            // Prüfen, ob Polylang aktiv ist und die Funktion existiert
            if (function_exists('pll_get_post_translations')) {
                // Gibt ein Array zurück: ['de' => 123, 'en' => 456]
                return pll_get_post_translations($post_id);
            }
            
            return null;
        },
        'schema' => [
            'description' => __('Polylang translations IDs mapping.'),
            'type'        => 'object',
        ],
    ]);
});