<?php

/**
 * Payhip REST API Webhook Endpoint.
 *
 * Provides an endpoint for receiving purchase webhooks from Payhip to update
 * the user's allowed_tourneys count and purchase status.
 *
 * @package Tourney
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit; // Exit if accessed directly.
}

/**
 * Registers REST API route for Payhip webhook.
 */
add_action( 'rest_api_init', function () {
    register_rest_route( 'payhip/v1', '/webhook', array(
        'methods'             => 'POST',
        'callback'            => 'handle_payhip_webhook',
        'permission_callback' => '__return_true',
    ) );
} );

/**
 * Handles incoming webhook calls from Payhip.
 *
 * Parses customer email and purchased item details, finds the corresponding
 * WordPress user, and updates their allowed_tourneys counter by adding the bought amount.
 *
 * @param WP_REST_Request $request The REST request instance.
 * @return WP_REST_Response Response object indicating success or failure.
 */
function handle_payhip_webhook( WP_REST_Request $request ) {
    $params = $request->get_params();

    // 1. Check for customer email address
    $email = sanitize_email( $params['email'] ?? $params['customer_email'] ?? '' );
    if ( empty( $email ) ) {
        return new WP_REST_Response( 'Keine E-Mail übergeben', 400 );
    }

    // 2. Search WordPress user by email address
    $user = get_user_by( 'email', $email );
    if ( ! $user ) {
        return new WP_REST_Response( 'User nicht gefunden', 404 );
    }

    // 3. Determine number of purchased tournaments
    $tourneys_to_add = 0;
    if ( isset( $params['tourneys'] ) && intval( $params['tourneys'] ) > 0 ) {
        $tourneys_to_add = intval( $params['tourneys'] );
    } elseif ( isset( $params['allowed_tourneys'] ) && intval( $params['allowed_tourneys'] ) > 0 ) {
        $tourneys_to_add = intval( $params['allowed_tourneys'] );
    } elseif ( isset( $params['count'] ) && intval( $params['count'] ) > 0 ) {
        $tourneys_to_add = intval( $params['count'] );
    } else {
        $product_name = $params['product_name'] ?? $params['item_name'] ?? '';
        if ( preg_match( '/(\d+)\s*Turnier/i', $product_name, $matches ) ) {
            $tourneys_to_add = intval( $matches[1] );
        } elseif ( ! empty( $params['quantity'] ) && intval( $params['quantity'] ) > 0 ) {
            $tourneys_to_add = intval( $params['quantity'] );
        }
    }

    // Default to 1 tournament if count could not be parsed
    if ( $tourneys_to_add <= 0 ) {
        $tourneys_to_add = 1;
    }

    // 4. Determine purchase price
    $price = 0.0;
    if ( isset( $params['price'] ) ) {
        $price = floatval( $params['price'] );
    } elseif ( isset( $params['amount'] ) ) {
        $price = floatval( $params['amount'] );
    }

    // 5. Update UserProfile (add purchase to history & increment available count)
    tourney_user_profile_add_purchase( $user->ID, $tourneys_to_add, $price );

    update_user_meta( $user->ID, 'payhip_kauf_status', 'aktiv' );
    update_user_meta( $user->ID, 'payhip_last_purchase_date', current_time( 'mysql' ) );

    return new WP_REST_Response( 'User erfolgreich aktualisiert', 200 );
}
