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