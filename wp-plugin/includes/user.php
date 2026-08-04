<?php

/**
 * Helper to check if a user (or current user) is an Administrator or Editor/TourneyMaster.
 *
 * @param WP_User|null $user Optional. The user object. Defaults to current logged-in user.
 * @return bool True if Administrator or Editor/TourneyMaster, false otherwise.
 */
function tourney_is_admin_or_editor( $user = null ) {
    if ( ! $user ) {
        $user = wp_get_current_user();
    }
    if ( ! $user || ! $user->exists() ) {
        return false;
    }
    $roles = (array) $user->roles;
    return user_can( $user, 'manage_options' ) || 
           user_can( $user, 'edit_others_posts' ) || 
           in_array( 'administrator', $roles, true ) || 
           in_array( 'editor', $roles, true ) || 
           in_array( 'tourney_master', $roles, true );
}

/**
 * Registers custom WordPress roles: TourneyAdmin (Subscriber permissions + edit_posts) and TourneyMaster (Editor permissions).
 * Language-independent internal role slugs: 'tourney_admin' and 'tourney_master'.
 */
function tourney_register_custom_roles() {
    // 1. TourneyAdmin role with Subscriber (Abonnent) capabilities plus tournament editing
    $subscriber_role = get_role( 'subscriber' );
    $tourney_admin_caps = $subscriber_role ? $subscriber_role->capabilities : array( 'read' => true );
    $tourney_admin_caps['edit_posts'] = true;
    $tourney_admin_caps['upload_files'] = true;

    if ( null === get_role( 'tourney_admin' ) ) {
        add_role( 'tourney_admin', __( 'TourneyAdmin', 'tourney' ), $tourney_admin_caps );
    }

    // 2. TourneyMaster role with Editor (Redakteur) capabilities
    $editor_role = get_role( 'editor' );
    $tourney_master_caps = $editor_role ? $editor_role->capabilities : array(
        'read'               => true,
        'edit_posts'         => true,
        'edit_others_posts'  => true,
        'publish_posts'      => true,
        'read_private_posts' => true,
    );

    if ( null === get_role( 'tourney_master' ) ) {
        add_role( 'tourney_master', __( 'TourneyMaster', 'tourney' ), $tourney_master_caps );
    }
}
add_action( 'init', 'tourney_register_custom_roles' );

/**
 * Adds custom user profile fields to the 'Edit User' and 'Your Profile' screens.
 *
 * @param WP_User $user The WP_User object.
 */
function tourney_add_custom_user_profile_fields( $user ) {
    $current_user = wp_get_current_user();
    $can_edit_counter = tourney_is_admin_or_editor( $current_user );
    $allowed_count_meta = get_user_meta( $user->ID, 'allowed_tourneys', true );
    $allowed_val = ( $allowed_count_meta !== '' ) ? intval( $allowed_count_meta ) : 0;
    ?>
    <h3><?php esc_html_e( 'Zusätzliche Profil-Informationen', 'tourney' ); ?></h3>

    <table class="form-table">
        <tr>
            <th><label for="organizer"><?php esc_html_e( 'Organizer', 'tourney' ); ?></label></th>
            <td>
                <input type="text" name="organizer" id="organizer" value="<?php echo esc_attr( get_user_meta( $user->ID, 'organizer', true ) ); ?>" class="regular-text" /><br />
                <span class="description"><?php esc_html_e( 'Please enter name of organizer, e.g. club name.', 'tourney' ); ?></span>
            </td>
        </tr>
        <tr>
            <th><label for="allowed_tourneys"><?php esc_html_e( 'Anzahl erlaubter Turniere', 'tourney' ); ?></label></th>
            <td>
                <?php if ( $can_edit_counter ) : ?>
                    <input type="number" min="0" name="allowed_tourneys" id="allowed_tourneys" value="<?php echo esc_attr( $allowed_val ); ?>" class="regular-text" /><br />
                    <span class="description"><?php esc_html_e( 'Anzahl der Turniere, die dieser TurnierAdmin (Autor) noch neu erstellen darf.', 'tourney' ); ?></span>
                <?php else : ?>
                    <input type="number" id="allowed_tourneys" value="<?php echo esc_attr( $allowed_val ); ?>" class="regular-text" disabled="disabled" /><br />
                    <span class="description"><?php esc_html_e( 'Anzahl der Turniere, die Sie noch neu erstellen dürfen (kann nur von einem TurnierMaster oder Administrator geändert werden).', 'tourney' ); ?></span>
                <?php endif; ?>
            </td>
        </tr>
    </table>
    <?php
}
// For displaying fields on the 'Edit User' screen for administrators
add_action( 'show_user_profile', 'tourney_add_custom_user_profile_fields' );
// For displaying fields on the 'Your Profile' screen (user's own profile)
add_action( 'edit_user_profile', 'tourney_add_custom_user_profile_fields' );

/**
 * Saves the custom user profile fields.
 *
 * @param int $user_id The ID of the user being saved.
 */
function ucfe_save_custom_user_profile_fields( $user_id ) {
    // Check if the current user has permission to edit this user's profile.
    if ( ! current_user_can( 'edit_user', $user_id ) ) {
        return false;
    }

    // Sanitize and save the 'organizer' field.
    if ( isset( $_POST['organizer'] ) ) {
        $organizer = sanitize_text_field( $_POST['organizer'] );
        update_user_meta( $user_id, 'organizer', $organizer );
    } else {
        delete_user_meta( $user_id, 'organizer' );
    }

    // Only Administrators and Editors (TurnierMaster) can save changes to allowed_tourneys
    if ( tourney_is_admin_or_editor() && isset( $_POST['allowed_tourneys'] ) ) {
        $allowed_val = max( 0, intval( $_POST['allowed_tourneys'] ) );
        update_user_meta( $user_id, 'allowed_tourneys', $allowed_val );
    }
}
// For saving fields on the 'Edit User' screen for administrators
add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
// For saving fields on the 'Edit User' screen (user's own profile)
add_action( 'edit_user_profile_update', 'ucfe_save_custom_user_profile_fields' );

/**
 * Function to retrieve the custom field for display outside the admin.
 *
 * @param int $user_id The ID of the user.
 * @return string The organization name, or an empty string if not set.
 */
function ucfe_get_user_organizer( $user_id ) {
    return get_user_meta( $user_id, 'organizer', true );
}

/**
 * Function to retrieve the remaining allowed tournaments count for a user.
 *
 * @param int $user_id The ID of the user.
 * @return int The number of allowed tournaments.
 */
function tourney_get_user_allowed_tourneys( $user_id ) {
    $meta = get_user_meta( $user_id, 'allowed_tourneys', true );
    return ( $meta === '' ) ? 0 : intval( $meta );
}