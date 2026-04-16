<?php


/**
 * Adds custom user profile fields to the 'Edit User' and 'Your Profile' screens.
 *
 * @param WP_User $user The WP_User object.
 */
function playdemo_add_custom_user_profile_fields( $user ) {
    ?>
    <h3><?php esc_html_e( 'Additional Profile Information', 'user-custom-fields-organizer' ); ?></h3>

    <table class="form-table">
        <tr>
            <th><label for="organizer"><?php esc_html_e( 'Organizer', 'user-custom-fields-organizer' ); ?></label></th>
            <td>
                <input type="text" name="organizer" id="organizer" value="<?php echo esc_attr( get_user_meta( $user->ID, 'organizer', true ) ); ?>" class="regular-text" /><br />
                <span class="description"><?php esc_html_e( 'Please enter name of organizer, e.g. club name.', 'user-custom-fields-organizer' ); ?></span>
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
}
// For saving fields on the 'Edit User' screen for administrators
add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
// For saving fields on the 'Your Profile' screen (user's own profile)
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