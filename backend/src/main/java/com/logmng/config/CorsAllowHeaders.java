package com.logmng.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * With {@code Access-Control-Allow-Credentials: true}, browsers reject {@code Access-Control-Allow-Headers: *}
 * on preflight (Fetch / CORS). Echo {@code Access-Control-Request-Headers} or use an explicit list.
 */
public final class CorsAllowHeaders {

    /** Covers JSON API + Bearer + typical SPA headers */
    static final String DEFAULT =
            "Content-Type, Authorization, Accept, Accept-Language, X-Requested-With, Cache-Control, Pragma";

    private CorsAllowHeaders() {
    }

    static void setOnResponse(HttpServletRequest request, HttpServletResponse response) {
        String requested = request.getHeader("Access-Control-Request-Headers");
        if (requested != null && !requested.isBlank()) {
            response.setHeader("Access-Control-Allow-Headers", requested.strip());
        } else {
            response.setHeader("Access-Control-Allow-Headers", DEFAULT);
        }
    }
}
