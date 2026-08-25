package com.sunrisedental.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebFilter(
        urlPatterns = {
                "/api/v1/*",
                "/dashboard.html",
                "/patients.html",
                "/appointments.html",
                "/billing.html"
        }
)
public class AuthenticationFilter implements Filter {

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

        String contextPath =
                request.getContextPath();

        String requestPath =
                request.getRequestURI()
                        .substring(contextPath.length());

        if (isPublicPath(requestPath)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session =
                request.getSession(false);

        boolean authenticated =
                session != null
                        && session.getAttribute(
                        "userId"
                ) != null;

        if (authenticated) {
            chain.doFilter(request, response);
            return;
        }

        if (requestPath.endsWith(".html")) {
            response.sendRedirect(
                    contextPath + "/login.html"
            );
            return;
        }

        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
                {
                    "success": false,
                    "message": "Authentication required"
                }
                """);
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/health")
                || path.equals(
                "/api/v1/health/database"
        )
                || path.startsWith(
                "/api/v1/auth/"
        );
    }
}