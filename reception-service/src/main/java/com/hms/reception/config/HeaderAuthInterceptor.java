package com.hms.reception.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class HeaderAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        
        // Since Gateway handles auth, we just trust headers if present.
        // For endpoints that require auth, we could enforce it here, 
        // but often we just extract it.
        request.setAttribute("userId", userId);
        request.setAttribute("userRole", userRole);
        return true;
    }
}
