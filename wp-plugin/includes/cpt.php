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
        'basic'         => 'Turniergrunddaten',
        'clubs'         => 'Vereine',
        'competitions'  => 'Wettbewerbe',
        'players'       => 'Spieler'
    ];

    // Runden 001–128 hinzufügen
    for ($i = 1; $i <= 128; $i++) {
        $key = sprintf('round%03d', $i);
        $meta_fields[$key] = 'Runde ' . $i;
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