package com.sunrisedental.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter(urlPatterns = "/*")
public class SecurityHeadersFilter implements Filter {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "form-action 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "object-src 'none'";

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest request =
                (HttpServletRequest) servletRequest;

        HttpServletResponse response =
                (HttpServletResponse) servletResponse;

        response.setHeader(
                "X-Content-Type-Options",
                "nosniff"
        );

        response.setHeader(
                "X-Frame-Options",
                "DENY"
        );

        response.setHeader(
                "Referrer-Policy",
                "no-referrer"
        );

        response.setHeader(
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=()"
        );

        response.setHeader(
                "Content-Security-Policy",
                CONTENT_SECURITY_POLICY
        );

        if (request.isSecure()) {
            response.setHeader(
                    "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains"
            );
        }

        if (isSensitivePath(request)) {
            response.setHeader(
                    "Cache-Control",
                    "no-store, no-cache, must-revalidate"
            );

            response.setHeader(
                    "Pragma",
                    "no-cache"
            );

        } else if (isStaticAsset(request)) {
            /*
               "no-cache" still lets the browser keep a copy, but it has to
               revalidate before using it, so Tomcat answers an unchanged
               file with 304 Not Modified. Without this the stylesheets and
               scripts carry no cache directive at all, and the browser is
               free to serve an old copy for hours - which makes a deployed
               change look as though it never happened.
            */
            response.setHeader(
                    "Cache-Control",
                    "no-cache"
            );
        }

        chain.doFilter(
                servletRequest,
                servletResponse
        );
    }

    /* Stylesheets, scripts, fonts and images served from /assets/ */
    private boolean isStaticAsset(
            HttpServletRequest request
    ) {
        return pathOf(request).startsWith("/assets/");
    }

    private boolean isSensitivePath(
            HttpServletRequest request
    ) {
        String path = pathOf(request);

        return path.startsWith("/api/")
                || path.equals("/dashboard.html")
                || path.equals("/patients.html")
                || path.equals("/appointments.html")
                || path.equals("/billing.html")
                || path.equals("/treatments.html")
                || path.equals("/reports.html")
                || path.equals("/help.html");
    }

    /* Request path with the context path removed */
    private String pathOf(HttpServletRequest request) {
        return request.getRequestURI()
                .substring(request.getContextPath().length());
    }
}