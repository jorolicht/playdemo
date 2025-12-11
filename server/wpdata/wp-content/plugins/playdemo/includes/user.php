<?php


/**
 * Adds custom user profile fields to the 'Edit User' and 'Your Profile' screens.
 *
 * @param WP_User $user The WP_User object.
 */
function playdemo_add_custom_user_profile_fields( $user ) {
    ?>
    <h3><?php esc_html_e( 'Additional Profile Information', 'user-custom-fields-club' ); ?></h3>

    <table class="form-table">
        <tr>
            <th><label for="club_name"><?php esc_html_e( 'Club Name', 'user-custom-fields-club' ); ?></label></th>
            <td>
                <input type="text" name="club_name" id="club_name" value="<?php echo esc_attr( get_user_meta( $user->ID, 'club_name', true ) ); ?>" class="regular-text" /><br />
                <span class="description"><?php esc_html_e( 'Please enter your club name.', 'user-custom-fields-club' ); ?></span>
            </td>
        </tr>
    </table>
    <?php
}
// For displaying fields on the 'Edit User' screen for administrators
add_action( 'show_user_profile', 'playdemo_add_custom_user_profile_fields' );
// For displaying fields on the 'Your Profile' screen (user's own profile)
add_action( 'edit_user_profile', 'playdemo_add_custom_user_profile_fields' );

/**
 * Saves the custom user profile fields.
 *
 * @param int $user_id The ID of the user being saved.
 */
function ucfe_save_custom_user_profile_fields( $user_id ) {
    // Check if the current user has permission to edit this user's profile.
    // Also check nonce for security (though WordPress handles this for core user profile updates).
    if ( ! current_user_can( 'edit_user', $user_id ) ) {
        return false;
    }

    // Sanitize and save the 'club_name' field.
    if ( isset( $_POST['club_name'] ) ) {
        // Sanitize the input before saving.
        $club_name = sanitize_text_field( $_POST['club_name'] );
        update_user_meta( $user_id, 'club_name', $club_name );
    } else {
        // If the field wasn't set (e.g., if it was hidden or not submitted for some reason),
        // you might want to delete the meta or set it to an empty string.
        delete_user_meta( $user_id, 'club_name' );
    }
}
// For saving fields on the 'Edit User' screen for administrators
add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
// For saving fields on the 'Your Profile' screen (user's own profile)
add_action( 'edit_user_profile_update', 'ucfe_save_custom_user_profile_fields' );


/**
 * Function to retrieve the custom field for display outside the admin.
 *
 * @param int $user_id The ID of the user.
 * @return string The user's favorite color, or an empty string if not set.
 */
function ucfe_get_user_favorite_color( $user_id ) {
    return get_user_meta( $user_id, 'club_name', true );
}