package com.opsera.pipelineassistant.security;

import jakarta.servlet.http.HttpServletRequest;

public final class IpAddressUtil {

    private IpAddressUtil() {}

    /**
     * Extracts the client IP address from the request, checking proxy headers before
     * falling back to the direct remote address. Takes the first entry from
     * X-Forwarded-For to handle proxy chains.
     */
    public static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
