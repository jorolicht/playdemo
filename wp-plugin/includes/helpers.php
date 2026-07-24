<?php

if (!class_exists('ApiHelper')) {
    class ApiHelper {
        public static function error($msgCode, $in1 = "", $in2 = "", $callStack = "", $status = 400) {
            return new WP_REST_Response([
                "msgCode"   => $msgCode,
                "in1"       => (string)$in1,
                "in2"       => (string)$in2,
                "callStack" => (string)$callStack
            ], $status);
        }

        public static function getPostId(WP_REST_Request $request) {
            $post_id = intval($request->get_param('postId'));
            if (!$post_id) {
                $slug = $request->get_param('slug');
                if ($slug) {
                    // 1. Try matching by exact post_name (slug)
                    $posts = get_posts([
                        'name'           => $slug,
                        'post_type'      => ['tourney', 'page', 'post'],
                        'post_status'    => 'any',
                        'numberposts'    => 1,
                        'fields'         => 'ids'
                    ]);
                    if (!empty($posts)) {
                        $post_id = $posts[0];
                    } else {
                        // 2. Try matching by exact title
                        $posts = get_posts([
                            'title'          => $slug,
                            'post_type'      => ['tourney', 'page', 'post'],
                            'post_status'    => 'any',
                            'numberposts'    => 1,
                            'fields'         => 'ids'
                        ]);
                        if (!empty($posts)) {
                            $post_id = $posts[0];
                        } else {
                            // 3. Try matching by post_name containing the slug
                            global $wpdb;
                            $escaped_slug = '%' . $wpdb->esc_like($slug) . '%';
                            $post_id_val = $wpdb->get_var($wpdb->prepare(
                                "SELECT ID FROM $wpdb->posts WHERE post_type IN ('tourney', 'page', 'post') AND post_status != 'trash' AND post_name LIKE %s LIMIT 1",
                                $escaped_slug
                            ));
                            if ($post_id_val) {
                                $post_id = intval($post_id_val);
                            }
                        }
                    }
                }
            }
            return $post_id;
        }
    }
}

if (!class_exists('HttpStatus')) {
    class HttpStatus {
        public const OK = 200;
        public const CREATED = 201;
        public const BAD_REQUEST = 400;
        public const UNAUTHORIZED = 401;
        public const FORBIDDEN = 403;
        public const NOT_FOUND = 404;
        public const CONFLICT = 409;
        public const INTERNAL_SERVER_ERROR = 500;
    }
}