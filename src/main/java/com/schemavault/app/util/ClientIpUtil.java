package com.schemavault.app.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility to extract the real client IP address from HTTP request headers.
 */
public class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * Extracts the real client IP address from standard HTTP headers
     * (X-Forwarded-For, X-Real-IP) or falls back to remote address.
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }
}
