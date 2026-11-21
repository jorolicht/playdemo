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
 * Prüft, ob der aktuelle Benutzer Schreibrechte auf eine Seite bzw. ein Turnier hat.
 *
 * Schreibrecht besteht bei WordPress-Recht 'edit_post' für den Post oder, für Benutzer mit
 * 'edit_posts' (z. B. TourneyAdmin), wenn der Veranstalter des Turniers dem eigenen
 * Veranstalter, Benutzernamen oder der E-Mail-Adresse entspricht.
 *
 * @param int $post_id Die WordPress Post-ID.
 * @return bool True, wenn der aktuelle Benutzer den Post bearbeiten darf.
 */
function tourney_user_can_edit_post( $post_id ) {
    $post_id = intval( $post_id );
    if ( $post_id <= 0 || ! is_user_logged_in() ) {
        return false;
    }
    if ( current_user_can( 'edit_post', $post_id ) ) {
        return true;
    }
    if ( ! current_user_can( 'edit_posts' ) ) {
        return false;
    }
    $organizer = get_post_meta( $post_id, 'organizer', true );
    if ( empty( $organizer ) ) {
        return false;
    }
    $user = wp_get_current_user();
    return in_array( $organizer, array( get_user_meta( $user->ID, 'organizer', true ), $user->user_login, $user->user_email ), true );
}

/**
 * REST-Permission-Callback: Schreibrecht auf den im Request (postId bzw. slug) angegebenen Post.
 *
 * @param WP_REST_Request $request Der REST-Request.
 * @return bool True, wenn der aktuelle Benutzer den Post bearbeiten darf.
 */
function tourney_api_can_edit_request_post( WP_REST_Request $request ) {
    $post_id = ApiHelper::getPostId( $request );
    if ( ! tourney_user_can_edit_post( $post_id ) ) {
        return false;
    }
    // Archivierte Turniere: nur noch lesen (Administratoren und Redakteure dürfen weiter bearbeiten)
    if ( ! in_array( $request->get_method(), array( 'GET', 'HEAD' ), true ) && tourney_is_archived( $post_id )
         && ! tourney_is_admin_or_editor() ) {
        return new WP_Error( 'tourney_archived', 'Das Turnier ist archiviert und kann nicht mehr bearbeitet werden.', array( 'status' => 403 ) );
    }
    return true;
}

/** Tage nach Turnierende, ab denen ein Cloud-Turnier archiviert (schreibgeschützt) ist. */
const TOURNEY_ARCHIVE_DAYS = 365;

/**
 * Prüft, ob ein Cloud-Turnier archiviert ist: TOURNEY_ARCHIVE_DAYS Tage nach dem Turnierende (ersatzweise
 * Turnierbeginn), frühestens nach dem Anlegen, solange der Autor keine gültige Vereins-/Verbandslizenz hat. Archivierte
 * Turniere bleiben öffentlich lesbar. Nur auf der Website (TOURNEY_FWS), nicht im Embed-Plugin.
 *
 * @param int $post_id Post-ID des Turniers.
 * @return bool
 */
function tourney_is_archived( $post_id ) {
    $post_id = intval( $post_id );
    if ( ! ( defined( 'TOURNEY_FWS' ) && TOURNEY_FWS ) || $post_id <= 0 || get_post_type( $post_id ) !== 'tourney' ) {
        return false;
    }
    $created = substr( (string) get_post_field( 'post_date', $post_id ), 0, 10 ) ?: wp_date( 'Y-m-d' );
    $end     = intval( get_post_meta( $post_id, 'endDate', true ) ) ?: intval( get_post_meta( $post_id, 'startDate', true ) );
    $end_dt  = $end > 19000101 ? DateTime::createFromFormat( 'Ymd', (string) $end ) : false;
    // Frist ab dem späteren Datum: Turnierende bzw. Anlegen (nachträglich angelegte Turniere bleiben 1 Jahr bearbeitbar)
    $base    = $end_dt && $end_dt->format( 'Y-m-d' ) > $created ? $end_dt->format( 'Y-m-d' ) : $created;
    $limit   = ( new DateTime( $base ) )->modify( '+' . TOURNEY_ARCHIVE_DAYS . ' days' )->format( 'Y-m-d' );
    if ( $limit >= wp_date( 'Y-m-d' ) ) {
        return false;
    }
    return ! tourney_user_license_valid( (int) get_post_field( 'post_author', $post_id ) );
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

/** Gültigkeit gekaufter Turniere in Jahren (ab Kaufdatum). */
const TOURNEY_CREDIT_VALID_YEARS = 2;

/**
 * Retrieves the UserProfile array structure for a given user ID.
 * Performs automatic legacy migration from 'allowed_tourneys' if 'user_profile' meta is missing/empty.
 *
 * Guthaben wird in Posten ("lots") geführt: je Kauf ein Posten mit Restanzahl und Ablaufdatum (Kaufdatum +
 * TOURNEY_CREDIT_VALID_YEARS). Posten ohne Ablaufdatum gelten unbefristet (Altbestand vor Einführung der Posten,
 * manuelle Gutschriften). "available" ist die Summe der noch gültigen Posten.
 *
 * @param int $user_id The ID of the user.
 * @return array Array with keys: available (int), executed (int), history (array of Purchase arrays),
 *               lots (array of ["remaining" => int, "expires" => "Y-m-d" oder ""])
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

    // Altbestand ohne Posten: bisheriges Guthaben als ein unbefristeter Posten
    if ( isset( $profile['lots'] ) && is_array( $profile['lots'] ) ) {
        $lots = tourney_clean_credit_lots( $profile['lots'] );
    } else {
        $legacy = isset( $profile['available'] ) ? max( 0, intval( $profile['available'] ) ) : 0;
        $lots   = $legacy > 0 ? array( array( 'remaining' => $legacy, 'expires' => '' ) ) : array();
    }

    return array(
        'available' => tourney_credit_available( $lots ),
        'executed'  => isset( $profile['executed'] ) ? max( 0, intval( $profile['executed'] ) ) : 0,
        'history'   => $clean_history,
        'lots'      => $lots,
    );
}

/**
 * Bereinigt Guthabenposten: nur Posten mit Restanzahl > 0, Ablaufdatum "Y-m-d" oder "".
 *
 * @param array $lots Posten.
 * @return array Bereinigte Posten.
 */
function tourney_clean_credit_lots( array $lots ) {
    $clean = array();
    foreach ( $lots as $lot ) {
        if ( ! is_array( $lot ) ) {
            continue;
        }
        $remaining = max( 0, intval( $lot['remaining'] ?? 0 ) );
        $expires   = (string) ( $lot['expires'] ?? '' );
        if ( $remaining > 0 && ( $expires === '' || preg_match( '/^\d{4}-\d{2}-\d{2}$/', $expires ) ) ) {
            $clean[] = array( 'remaining' => $remaining, 'expires' => $expires );
        }
    }
    return $clean;
}

/**
 * Prüft, ob ein Guthabenposten heute noch gültig ist.
 *
 * @param array $lot Posten.
 * @return bool true ohne Ablaufdatum oder wenn das Ablaufdatum heute oder später ist.
 */
function tourney_credit_lot_valid( array $lot ) {
    return $lot['expires'] === '' || $lot['expires'] >= wp_date( 'Y-m-d' );
}

/**
 * Summe der gültigen Guthabenposten.
 *
 * @param array $lots Posten.
 * @return int Verfügbare Turniere.
 */
function tourney_credit_available( array $lots ) {
    $sum = 0;
    foreach ( $lots as $lot ) {
        if ( tourney_credit_lot_valid( $lot ) ) {
            $sum += $lot['remaining'];
        }
    }
    return $sum;
}

/**
 * Bucht 1 Turnier aus dem am frühesten ablaufenden gültigen Posten ab (unbefristete Posten zuletzt).
 *
 * @param array $lots Posten (wird verändert).
 * @return string|false Ablaufdatum des belasteten Postens ("" = unbefristet) oder false ohne gültiges Guthaben.
 */
function tourney_credit_take( array &$lots ) {
    $best = -1;
    foreach ( $lots as $i => $lot ) {
        if ( $lot['remaining'] < 1 || ! tourney_credit_lot_valid( $lot ) ) {
            continue;
        }
        if ( $best < 0 ) {
            $best = $i;
            continue;
        }
        $b = $lots[ $best ]['expires'];
        $e = $lot['expires'];
        if ( $b === '' && $e !== '' || ( $e !== '' && $b !== '' && $e < $b ) ) {
            $best = $i;
        }
    }
    if ( $best < 0 ) {
        return false;
    }
    $lots[ $best ]['remaining'] -= 1;
    return $lots[ $best ]['expires'];
}

/**
 * Saves the UserProfile structure for a given user ID as a JSON-encoded string.
 *
 * Wurde "available" vom Aufrufer geändert (Administrator, Management-Konsole), wird die Differenz als
 * unbefristeter Posten gutgeschrieben bzw. von den gültigen Posten abgebucht.
 *
 * @param int   $user_id The ID of the user.
 * @param array $profile Array containing available, executed, history and lots.
 * @return bool True on success.
 */
function tourney_update_user_profile( $user_id, array $profile ) {
    $lots = tourney_clean_credit_lots( isset( $profile['lots'] ) && is_array( $profile['lots'] ) ? $profile['lots'] : array() );
    if ( isset( $profile['available'] ) ) {
        $delta = max( 0, intval( $profile['available'] ) ) - tourney_credit_available( $lots );
        if ( $delta > 0 ) {
            $lots[] = array( 'remaining' => $delta, 'expires' => '' );
        }
        for ( $i = 0; $i < -$delta; $i++ ) {
            if ( tourney_credit_take( $lots ) === false ) {
                break;
            }
        }
        $lots = tourney_clean_credit_lots( $lots );
    }

    $clean_profile = array(
        'available' => tourney_credit_available( $lots ),
        'executed'  => isset( $profile['executed'] ) ? max( 0, intval( $profile['executed'] ) ) : 0,
        'history'   => isset( $profile['history'] ) && is_array( $profile['history'] ) ? array_values( $profile['history'] ) : array(),
        'lots'      => array_values( $lots ),
    );

    $json_string = wp_json_encode( $clean_profile );
    update_user_meta( $user_id, 'user_profile', $json_string );
    update_user_meta( $user_id, 'UserProfile', $json_string );
    return true;
}

/**
 * Adds a purchase to the user's history and a credit lot valid for TOURNEY_CREDIT_VALID_YEARS years.
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
    $bought  = DateTime::createFromFormat( 'YmdHi', substr( (string) $date, 0, 12 ) ) ?: new DateTime();
    $profile['lots'][] = array(
        'remaining' => $count,
        'expires'   => $bought->modify( '+' . TOURNEY_CREDIT_VALID_YEARS . ' years' )->format( 'Y-m-d' ),
    );
    unset( $profile['available'] );   // Guthaben ergibt sich aus den Posten

    $profile['history'][] = array(
        'date'  => (string) $date,
        'count' => $count,
        'price' => $price,
    );
    tourney_update_user_profile( $user_id, $profile );
    return tourney_get_user_profile( $user_id );
}

/**
 * Reserviert 1 Turnier vom Guthaben (beim Anlegen eines Cloud-Turniers) und zählt es als durchgeführt.
 *
 * @param int $user_id The ID of the user.
 * @return string|false Ablaufdatum des belasteten Postens ("" = unbefristet) oder false ohne gültiges Guthaben.
 */
function tourney_user_reserve_tournament( $user_id ) {
    $profile = tourney_get_user_profile( $user_id );
    $expires = tourney_credit_take( $profile['lots'] );
    if ( $expires === false ) {
        return false;
    }
    unset( $profile['available'] );
    $profile['executed'] += 1;
    tourney_update_user_profile( $user_id, $profile );
    return $expires;
}

/**
 * Gibt ein reserviertes Turnier zurück (Turnier ohne Ergebnis gelöscht).
 *
 * @param int    $user_id The ID of the user.
 * @param string $expires Ablaufdatum des ursprünglich belasteten Postens ("" = unbefristet).
 * @return void
 */
function tourney_user_refund_tournament( $user_id, $expires ) {
    $profile = tourney_get_user_profile( $user_id );
    $profile['lots'][] = array( 'remaining' => 1, 'expires' => (string) $expires );
    unset( $profile['available'] );
    $profile['executed'] = max( 0, $profile['executed'] - 1 );
    tourney_update_user_profile( $user_id, $profile );
}

/**
 * Executes a tournament for a user (Reservierung, siehe tourney_user_reserve_tournament).
 *
 * @param int $user_id The ID of the user.
 * @return bool True if successfully reserved, false without valid credit.
 */
function tourney_user_profile_execute_tournament( $user_id ) {
    return tourney_user_reserve_tournament( $user_id ) !== false;
}

/**
 * Datum, bis zu dem die Vereins- bzw. Verbandslizenz eines Benutzers gilt.
 *
 * @param int $user_id The ID of the user.
 * @return string "Y-m-d" oder "" ohne Lizenz.
 */
function tourney_user_license_until( $user_id ) {
    $until = (string) get_user_meta( $user_id, 'tourney_license_until', true );
    return preg_match( '/^\d{4}-\d{2}-\d{2}$/', $until ) ? $until : '';
}

/**
 * Prüft, ob die Lizenz eines Benutzers heute gilt (Turniere ohne Guthaben anlegen, keine Archivfrist).
 *
 * @param int $user_id The ID of the user.
 * @return bool
 */
function tourney_user_license_valid( $user_id ) {
    $until = tourney_user_license_until( $user_id );
    return $until !== '' && $until >= wp_date( 'Y-m-d' );
}

/**
 * Setzt bzw. entfernt ("") die Lizenz eines Benutzers.
 *
 * @param int    $user_id The ID of the user.
 * @param string $until   "Y-m-d" oder "".
 * @return void
 */
function tourney_user_set_license( $user_id, $until ) {
    $until = trim( (string) $until );
    if ( preg_match( '/^\d{4}-\d{2}-\d{2}$/', $until ) ) {
        update_user_meta( $user_id, 'tourney_license_until', $until );
    } elseif ( $until === '' ) {
        delete_user_meta( $user_id, 'tourney_license_until' );
    }
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
            <th><?php esc_html_e( 'Guthaben (gültig bis)', 'tourney' ); ?></th>
            <td>
                <?php if ( ! empty( $profile['lots'] ) ) : ?>
                    <table class="widefat striped" style="max-width: 400px;">
                        <thead><tr><th><?php esc_html_e( 'Turniere', 'tourney' ); ?></th><th><?php esc_html_e( 'gültig bis', 'tourney' ); ?></th></tr></thead>
                        <tbody>
                            <?php foreach ( $profile['lots'] as $lot ) : ?>
                                <tr<?php echo tourney_credit_lot_valid( $lot ) ? '' : ' style="opacity:.5"'; ?>>
                                    <td><?php echo esc_html( $lot['remaining'] ); ?></td>
                                    <td><?php echo esc_html( $lot['expires'] === '' ? __( 'unbefristet', 'tourney' ) : wp_date( 'd.m.Y', strtotime( $lot['expires'] ) ) ); ?><?php echo tourney_credit_lot_valid( $lot ) ? '' : ' (' . esc_html__( 'abgelaufen', 'tourney' ) . ')'; ?></td>
                                </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                <?php else : ?>
                    <span class="description"><?php esc_html_e( 'Kein Guthaben.', 'tourney' ); ?></span>
                <?php endif; ?>
                <span class="description"><?php esc_html_e( 'Gekaufte Turniere gelten 2 Jahre ab Kauf; manuell gutgeschriebene unbefristet.', 'tourney' ); ?></span>
            </td>
        </tr>
        <tr>
            <th><label for="tourney_license_until"><?php esc_html_e( 'Vereins-/Verbandslizenz gültig bis', 'tourney' ); ?></label></th>
            <td>
                <?php $license_until = tourney_user_license_until( $user->ID ); ?>
                <?php if ( $can_edit_counter ) : ?>
                    <input type="date" name="tourney_license_until" id="tourney_license_until" value="<?php echo esc_attr( $license_until ); ?>" /><br />
                    <span class="description"><?php esc_html_e( 'Solange die Lizenz gilt: Turniere ohne Guthaben anlegen, keine Archivfrist. Leer = keine Lizenz.', 'tourney' ); ?></span>
                <?php else : ?>
                    <input type="date" id="tourney_license_until" value="<?php echo esc_attr( $license_until ); ?>" disabled="disabled" />
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
// Zusätzliche Profil-Informationen (UserProfile) nur bei Full Web Site
if ( TOURNEY_FWS ) {
    add_action( 'show_user_profile', 'tourney_add_custom_user_profile_fields' );
    add_action( 'edit_user_profile', 'tourney_add_custom_user_profile_fields' );
}

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
        if ( isset( $_POST['tourney_license_until'] ) ) {
            tourney_user_set_license( $user_id, sanitize_text_field( wp_unslash( $_POST['tourney_license_until'] ) ) );
        }
    }
}
if ( TOURNEY_FWS ) {
    add_action( 'personal_options_update', 'ucfe_save_custom_user_profile_fields' );
    add_action( 'edit_user_profile_update', 'ucfe_save_custom_user_profile_fields' );
}

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
if ( TOURNEY_FWS ) {
    add_action( 'user_register', 'tourney_assign_unmatched_purchases_on_register' );
}