<?php
/**
 * WebAuthn (Passkeys) API for Tourney
 * Library: lbuchs/WebAuthn
 */

use lbuchs\WebAuthn\WebAuthn;

// Function to get WebAuthn instance
function pd_get_webauthn() {
    $rpName = 'TurnierService';
    $rpId = parse_url(home_url(), PHP_URL_HOST);
    // Use localhost if domain is not set (for development)
    if (empty($rpId)) $rpId = 'localhost';
    
    return new WebAuthn($rpName, $rpId, ['android-safetynet', 'fido-u2f', 'apple', 'tpm']);
}

add_action('rest_api_init', function () {
    // REGISTRATION: Get Args
    register_rest_route('tourney/v1', '/auth/webauthn/register-args', [
        'methods' => 'GET',
        'callback' => 'pd_webauthn_register_args',
        'permission_callback' => function() { return is_user_logged_in(); }
    ]);

    // REGISTRATION: Process
    register_rest_route('tourney/v1', '/auth/webauthn/register', [
        'methods' => 'POST',
        'callback' => 'pd_webauthn_register_process',
        'permission_callback' => function() { return is_user_logged_in(); }
    ]);

    // LOGIN: Get Args
    register_rest_route('tourney/v1', '/auth/webauthn/login-args', [
        'methods' => 'GET',
        'callback' => 'pd_webauthn_login_args',
        'permission_callback' => '__return_true'
    ]);

    // LOGIN: Process
    register_rest_route('tourney/v1', '/auth/webauthn/login', [
        'methods' => 'POST',
        'callback' => 'pd_webauthn_login_process',
        'permission_callback' => '__return_true'
    ]);
});

// --- CALLBACKS ---

function pd_webauthn_register_args() {
    $user = wp_get_current_user();
    $webauthn = pd_get_webauthn();
    
    // Check if user already has credentials (for residents key logic)
    $args = $webauthn->getCreateArgs($user->ID, $user->user_login, $user->display_name, 60, true);
    
    // Store challenge in transient for 10 minutes
    set_transient('pd_webauthn_challenge_' . $user->ID, $webauthn->getChallenge(), 10 * MINUTE_IN_SECONDS);
    
    return $args;
}

function pd_webauthn_register_process($request) {
    $user = wp_get_current_user();
    $params = $request->get_json_params();
    $challenge = get_transient('pd_webauthn_challenge_' . $user->ID);
    
    if (!$challenge) {
        return new WP_Error('challenge_expired', 'Challenge abgelaufen.', ['status' => 403]);
    }

    try {
        $webauthn = pd_get_webauthn();
        $credential = $webauthn->processCreate(
            $params['clientDataJSON'],
            $params['attestationObject'],
            $challenge,
            true, // require user presence
            true, // require user verification
            false // don't check domain (useful for local dev, enable in production)
        );

        // Store credential in user meta
        $keys = get_user_meta($user->ID, 'pd_webauthn_keys', true) ?: [];
        $keys[] = [
            'credentialId' => $credential->credentialId,
            'publicKey' => $credential->publicKey,
            'attestationFormat' => $credential->attestationFormat,
            'counter' => $credential->counter,
            'userHandle' => $credential->userHandle
        ];
        update_user_meta($user->ID, 'pd_webauthn_keys', $keys);

        return ['status' => 'success', 'message' => 'Passkey erfolgreich hinzugefügt.'];
    } catch (\Exception $e) {
        return new WP_Error('registration_failed', $e->getMessage(), ['status' => 400]);
    }
}

function pd_webauthn_login_args() {
    $webauthn = pd_get_webauthn();
    $args = $webauthn->getGetArgs([], 60, true, true); // Allow all users (discoverable credentials)
    
    // Store challenge in transient. Since we don't know the user yet, we use a random ID
    $challengeId = wp_generate_password(16, false);
    set_transient('pd_webauthn_login_challenge_' . $challengeId, $webauthn->getChallenge(), 10 * MINUTE_IN_SECONDS);
    
    return [
        'args' => $args,
        'challengeId' => $challengeId
    ];
}

function pd_webauthn_login_process($request) {
    $params = $request->get_json_params();
    $challengeId = $params['challengeId'];
    $challenge = get_transient('pd_webauthn_login_challenge_' . $challengeId);
    
    if (!$challenge) {
        return new WP_Error('challenge_expired', 'Challenge abgelaufen.', ['status' => 403]);
    }

    // Find user by userHandle (which is the user ID in our case)
    $userId = (int)$params['userHandle'];
    if (!$userId) {
        return new WP_Error('invalid_user', 'Benutzer konnte nicht identifiziert werden.', ['status' => 403]);
    }

    $keys = get_user_meta($userId, 'pd_webauthn_keys', true);
    $foundKey = null;
    foreach ($keys as $key) {
        if ($key['credentialId'] === $params['id']) {
            $foundKey = $key;
            break;
        }
    }

    if (!$foundKey) {
        return new WP_Error('key_not_found', 'Passkey nicht in deinem Account gefunden.', ['status' => 403]);
    }

    try {
        $webauthn = pd_get_webauthn();
        $webauthn->processGet(
            $params['clientDataJSON'],
            $params['authenticatorData'],
            $params['signature'],
            $foundKey['publicKey'],
            $challenge
        );

        // LOGIN SUCCESSFUL
        wp_set_current_user($userId);
        wp_set_auth_cookie($userId, true);

        return ['status' => 'success', 'message' => 'Login erfolgreich.'];
    } catch (\Exception $e) {
        return new WP_Error('login_failed', $e->getMessage(), ['status' => 403]);
    }
}
