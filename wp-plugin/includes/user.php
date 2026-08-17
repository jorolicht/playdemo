<?php

/**
 * Helper to check if a user (or current user) is an Administrator or Editor/TourneyMaster.
 *
 * @param WP_User|null $user Optional. The user object. Defaults to current logged-in user.
 * @return bool True if Administrator or Editor/TourneyMaster, false otherwise.
 */
function tourney_is_admin_or_editor( $user = null ) {
    if ( ! $user || ! ( $user instanceof WP_User ) ) {
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
    $subscriber_role = get_role( 'subscriber' );
    $tourney_admin_caps = $subscriber_role ? $subscriber_role->capabilities : array( 'read' => true );
    $tourney_admin_caps['edit_posts'] = true;
    $tourney_admin_caps['upload_files'] = true;

    if ( null === get_role( 'tourney_admin' ) ) {
        add_role( 'tourney_admin', __( 'TourneyAdmin', 'tourney' ), $tourney_admin_caps );
    }

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
 * Retrieves the UserProfile array structure for a given user ID.
 * Performs automatic legacy migration from 'allowed_tourneys' if 'user_profile' meta is missing/empty.
 *
 * @param int $user_id The ID of the user.
 * @return array Array with keys: available (int), executed (int), history (array of Purchase arrays)
 */
function tourney_get_user_profile( $user_id ) {
    $meta = get_user_meta( $user_id, 'user_profile', true );
    if ( empty( $meta ) ) {
        $meta = get_user_meta( $user_id, 'UserProfile', true );
    }

    $profile = null;
    if ( is_array( $meta ) ) {
        $profile = $meta;
    } elseif ( is_string( $meta ) && ! empty( $meta ) ) {
        $decoded = json_decode( $meta, true );
        if ( is_array( $decoded ) ) {
            $profile = $decoded;
        }
    }

    if ( ! is_array( $profile ) ) {
        $profile = array(
            'available' => 0,
            'executed'  => 0,
            'history'   => array(),
        );
    }

    $clean_history = array();
    if ( isset( $profile['history'] ) && is_array( $profile['history'] ) ) {
        foreach ( $profile['history'] as $item ) {
            if ( is_array( $item ) ) {
                $clean_history[] = array(
                    'date'  => isset( $item['date'] ) ? (string) $item['date'] : '',
                    'count' => isset( $item['count'] ) ? intval( $item['count'] ) : 0,
                    'price' => isset( $item['price'] ) ? floatval( $item['price'] ) : 0.0,
                );
            }
        }
    }

    return array(
        'available' => isset( $profile['available'] ) ? max( 0, intval( $profile['available'] ) ) : 0,
        'executed'  => isset( $profile['executed'] ) ? max( 0, intval( $profile['executed'] ) ) : 0,
        'history'   => $clean_history,
    );
}

/**
 * Saves the UserProfile structure for a given user ID as a JSON-encoded string.
 *
 * @param int   $user_id The ID of the user.
 * @param array $profile Array containing available, executed, and history.
 * @return bool True on success.
 */
function tourney_update_user_profile( $user_id, array $profile ) {
    $clean_profile = array(
        'available' => isset( $profile['available'] ) ? max( 0, intval( $profile['available'] ) ) : 0,
        'executed'  => isset( $profile['executed'] ) ? max( 0, intval( $profile['executed'] ) ) : 0,
        'history'   => isset( $profile['history'] ) && is_array( $profile['history'] ) ? array_values( $profile['history'] ) : array(),
    );

    $json_string = wp_json_encode( $clean_profile );
    update_user_meta( $user_id, 'user_profile', $json_string );
    update_user_meta( $user_id, 'UserProfile', $json_string );
    return true;
}

/**
 * Adds a purchase to the user's history and increments available tournaments count.
 *
 * @param int         $user_id The ID of the user.
 * @param int         $count   Number of tournaments bought.
 * @param float       $price   Purchase price.
 * @param string|null $date    Format yyyymmddhhmm. Defaults to current date/time.
 * @return array The updated UserProfile.
 */
function tourney_user_profile_add_purchase( $user_id, $count, $price = 0.0, $date = null ) {
    $count = max( 1, intval( $count ) );
    $price = floatval( $price );
    if ( empty( $date ) ) {
        $date = date( 'YmdHi' );
    }

    $profile = tourney_get_user_profile( $user_id );
    $profile['available'] += $count;

    $purchase = array(
        'date'  => (string) $date,
        'count' => $count,
        'price' => $price,
    );

    $profile['history'][] = $purchase;
    tourney_update_user_profile( $user_id, $profile );
    return $profile;
}

/**
 * Executes a tournament for a user, decrementing available by 1 and incrementing executed by 1 if available >= 1.
 *
 * @param int $user_id The ID of the user.
 * @return bool True if successfully decremented, false if available < 1.
 */
function tourney_user_profile_execute_tournament( $user_id ) {
    $profile = tourney_get_user_profile( $user_id );
    if ( $profile['available'] < 1 ) {
        return false;
    }

    $profile['available'] = max( 0, $profile['available'] - 1 );
    $profile['executed'] += 1;
    tourney_update_user_profile( $user_id, $profile );
    return true;
}

/**
 * Function to retrieve the remaining available tournaments count for a user.
 *
 * @param int $user_id The ID of the user.
 * @return int The number of available tournaments.
 */
function tourney_get_user_allowed_tourneys( $user_id ) {
    $profile = tourney_get_user_profile( $user_id );
    return $profile['available'];
}

/**
 * Adds custom user profile fields to the 'Edit User' and 'Your Profile' screens.
 *
 * @param WP_User $user The WP_User object.
 */
function tourney_add_custom_user_profile_fields( $user ) {
    $current_user = wp_get_current_user();
    $can_edit_counter = tourney_is_admin_or_editor( $current_user );
    $profile = tourney_get_user_profile( $user->ID );
    ?>
    <h3><?php esc_html_e( 'Zusätzliche Profil-Informationen (UserProfile)', 'tourney' ); ?></h3>

    <table class="form-table">
        <tr>
            <th><label for="organizer"><?php esc_html_e( 'Organizer', 'tourney' ); ?></label></th>
            <td>
                <input type="text" name="organizer" id="organizer" value="<?php echo esc_attr( get_user_meta( $user->ID, 'organizer', true ) ); ?>" class="regular-text" /><br />
                <span class="description"><?php esc_html_e( 'Bitte Name des Veranstalters eingeben, z.B. Vereinsname.', 'tourney' ); ?></span>
            </td>
        </tr>
        <tr>
            <th><label for="user_profile_available"><?php esc_html_e( 'Verfügbare Turniere (available)', 'tourney' ); ?></label></th>
            <td>
                <?php if ( $can_edit_counter ) : ?>
                    <input type="number" min="0" name="user_profile_available" id="user_profile_available" value="<?php echo esc_attr( $profile['available'] ); ?>" class="regular-text" /><br />
                    <span class="description"><?php esc_html_e( 'Anzahl der Turniere, die dieser Benutzer noch erstellen kann.', 'tourney' ); ?></span>
                <?php else : ?>
                    <input type="number" id="user_profile_available" value="<?php echo esc_attr( $profile['available'] ); ?>" class="regular-text" disabled="disabled" /><br />
                    <span class="description"><?php esc_html_e( 'Anzahl der Turniere, die Sie noch neu erstellen dürfen.', 'tourney' ); ?></span>
                <?php endif; ?>
            </td>
        </tr>
        <tr>
            <th><label for="user_profile_executed"><?php esc_html_e( 'Durchgeführte Turniere (executed)', 'tourney' ); ?></label></th>
            <td>
                <?php if ( $can_edit_counter ) : ?>
                    <input type="number" min="0" name="user_profile_executed" id="user_profile_executed" value="<?php echo esc_attr( $profile['executed'] ); ?>" class="regular-text" /><br />
                    <span class="description"><?php esc_html_e( 'Anzahl der vom Benutzer bereits durchgeführten Turniere.', 'tourney' ); ?></span>
                <?php else : ?>
                    <input type="number" id="user_profile_executed" value="<?php echo esc_attr( $profile['executed'] ); ?>" class="regular-text" disabled="disabled" /><br />
                <?php endif; ?>
            </td>
        </tr>
        <tr>
            <th><?php esc_html_e( 'Kaufhistorie (history)', 'tourney' ); ?></th>
            <td>
                <?php if ( ! empty( $profile['history'] ) ) : ?>
                    <table class="widefat striped" style="max-width: 600px;">
                        <thead>
                            <tr>
                                <th><?php esc_html_e( 'Datum (yyyymmddhhmm)', 'tourney' ); ?></th>
                                <th><?php esc_html_e( 'Anzahl', 'tourney' ); ?></th>
                                <th><?php esc_html_e( 'Preis (€)', 'tourney' ); ?></th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php foreach ( $profile['history'] as $item ) : ?>
                                <tr>
                                    <td><?php echo esc_html( $item['date'] ?? '' ); ?></td>
                                    <td><?php echo esc_html( $item['count'] ?? 0 ); ?></td>
                                    <td><?php echo esc_html( number_format( floatval( $item['price'] ?? 0 ), 2, ',', '.' ) ); ?> €</td>
                                </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                <?php else : ?>
                    <span class="description"><?php esc_html_e( 'Keine Käufe vorhanden.', 'tourney' ); ?></span>
                <?php endif; ?>
            </td>
        </tr>
    </table>
    <?php
}
add_action( 'show_user_profile', 'tourney_add_custom_user_profile_fields' );
add_action( 'edit_user_profile', 'tourney_add_custom_user_profile_fields' );

/**
 * Saves the custom user profile fields.
 *
 * @param int $user_id The ID of the user being saved.
 */
function ucfe_save_custom_user_profile_fields( $user_id ) {
    if ( ! current_user_can( 'edit_user', $user_id ) ) {
        return false;
    }

    if ( isset( $_POST['organizer'] ) ) {
        $organizer = sanitize_text_field( $_POST['organizer'] );
        update_user_meta( $user_id, 'organizer', $organizer );
    } else {
        delete_user_meta( $user_id, 'organizer' );
    }

    if ( tourney_is_admin_or_editor() ) {
        $profile = tourney_get_user_profile( $user_id );
        if ( isset( $_POST['user_profile_available'] ) ) {
            $profile['available'] = max( 0, intval( $_POST['user_profile_available'] ) );
        } elseif ( isset( $_POST['allowed_tourneys'] ) ) {
            $profile['available'] = max( 0, intval( $_POST['allowed_tourneys'] ) );
        }
        if ( isset( $_POST['user_profile_executed'] ) ) {
            $profile['executed'] = max( 0, intval( $_POST['user_profile_executed'] ) );
        }
        tourney_update_user_profile( $user_id, $profile );
    }
}
add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
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
 * Helper to get the log file path for unmatched purchases.
 *
 * @return string Absolute file path.
 */
function tourney_get_unmatched_purchases_file() {
    $upload_dir = wp_upload_dir();
    $dir = $upload_dir['basedir'];
    if ( ! file_exists( $dir ) ) {
        wp_mkdir_p( $dir );
    }
    return $dir . '/tourney_unmatched_purchases.log';
}

/**
 * Reads all unmatched purchase entries from log file.
 *
 * @return array Array of unmatched purchase arrays: [ ['email' => ..., 'count' => ..., 'price' => ..., 'date' => ..., 'product_name' => ...], ... ]
 */
function tourney_get_unmatched_purchases() {
    $file = tourney_get_unmatched_purchases_file();
    if ( ! file_exists( $file ) ) {
        return array();
    }
    $content = file_get_contents( $file );
    if ( empty( $content ) ) {
        return array();
    }
    $lines = explode( "\n", trim( $content ) );
    $entries = array();
    foreach ( $lines as $line ) {
        $line = trim( $line );
        if ( empty( $line ) ) {
            continue;
        }
        $decoded = json_decode( $line, true );
        if ( is_array( $decoded ) && ! empty( $decoded['email'] ) ) {
            $entries[] = array(
                'email'        => strtolower( sanitize_email( $decoded['email'] ) ),
                'count'        => isset( $decoded['count'] ) ? max( 1, intval( $decoded['count'] ) ) : 1,
                'price'        => isset( $decoded['price'] ) ? floatval( $decoded['price'] ) : 0.0,
                'date'         => isset( $decoded['date'] ) ? (string) $decoded['date'] : date( 'YmdHi' ),
                'product_name' => isset( $decoded['product_name'] ) ? sanitize_text_field( $decoded['product_name'] ) : '',
            );
        }
    }
    return $entries;
}

/**
 * Appends an unmatched purchase entry to the log file.
 *
 * @param string $email
 * @param int    $count
 * @param float  $price
 * @param string|null $date
 * @param string $product_name
 * @return bool
 */
function tourney_add_unmatched_purchase( $email, $count, $price = 0.0, $date = null, $product_name = '' ) {
    $file = tourney_get_unmatched_purchases_file();
    if ( empty( $date ) ) {
        $date = date( 'YmdHi' );
    }
    $entry = array(
        'email'        => strtolower( sanitize_email( $email ) ),
        'count'        => max( 1, intval( $count ) ),
        'price'        => floatval( $price ),
        'date'         => (string) $date,
        'product_name' => sanitize_text_field( $product_name ),
    );
    $line = wp_json_encode( $entry ) . "\n";
    return ( file_put_contents( $file, $line, FILE_APPEND | LOCK_EX ) !== false );
}

/**
 * Writes an array of unmatched purchase entries to the log file (overwriting existing content).
 *
 * @param array $entries
 * @return bool
 */
function tourney_save_unmatched_purchases( array $entries ) {
    $file = tourney_get_unmatched_purchases_file();
    $content = '';
    foreach ( $entries as $entry ) {
        if ( is_array( $entry ) && ! empty( $entry['email'] ) ) {
            $clean_entry = array(
                'email'        => strtolower( sanitize_email( $entry['email'] ) ),
                'count'        => max( 1, intval( $entry['count'] ) ),
                'price'        => floatval( $entry['price'] ),
                'date'         => (string) ( $entry['date'] ?? date( 'YmdHi' ) ),
                'product_name' => sanitize_text_field( $entry['product_name'] ?? '' ),
            );
            $content .= wp_json_encode( $clean_entry ) . "\n";
        }
    }
    return ( file_put_contents( $file, $content, LOCK_EX ) !== false );
}

/**
 * Schritt 2: Automatische Zuweisung bei Neuregistrierung (user_register Hook)
 * Checks log file for matching purchases when a new user registers and assigns them.
 *
 * @param int $user_id The ID of the newly registered user.
 */
function tourney_assign_unmatched_purchases_on_register( $user_id ) {
    $user = get_userdata( $user_id );
    if ( ! $user || empty( $user->user_email ) ) {
        return;
    }

    $email = strtolower( trim( $user->user_email ) );
    $all_entries = tourney_get_unmatched_purchases();
    if ( empty( $all_entries ) ) {
        return;
    }

    $remaining_entries = array();
    $assigned_count = 0;

    foreach ( $all_entries as $entry ) {
        if ( strtolower( trim( $entry['email'] ) ) === $email ) {
            tourney_user_profile_add_purchase( $user_id, $entry['count'], $entry['price'], $entry['date'] );
            $assigned_count++;
        } else {
            $remaining_entries[] = $entry;
        }
    }

    if ( $assigned_count > 0 ) {
        tourney_save_unmatched_purchases( $remaining_entries );
        update_user_meta( $user_id, 'payhip_kauf_status', 'aktiv' );
        update_user_meta( $user_id, 'payhip_last_purchase_date', current_time( 'mysql' ) );
    }
}
add_action( 'user_register', 'tourney_assign_unmatched_purchases_on_register' );