package com.countcheck;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestGuard extends OncePerRequestFilter {
  private final TrafficLimits limits = new TrafficLimits(java.time.Clock.systemUTC());

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/css/")
        || req.getRequestURI().startsWith("/js/")
        || req.getRequestURI().equals("/health")) {
      chain.doFilter(req, res);
      return;
    }
    boolean write = "POST".equals(req.getMethod());
    boolean accountAccess =
        write && java.util.Set.of("/login", "/register", "/recover").contains(req.getRequestURI());
    boolean limited = !limits.allow(req.getRemoteAddr(), write, accountAccess);
    if (limited) {
      res.setHeader("Retry-After", "60");
      res.sendError(429);
      return;
    }
    if ("POST".equals(req.getMethod())) {
      if (req.getContentLengthLong() > 16384) {
        res.sendError(413);
        return;
      }
      String type = req.getContentType();
      if (type == null || !type.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
        res.sendError(415);
        return;
      }
      // Read the bounded raw body before any form parser; wrap with an explicit form parameter map.
      byte[] body = req.getInputStream().readNBytes(16385);
      if (body.length > 16384) {
        res.sendError(413);
        return;
      }
      java.util.Map<String, String[]> params = new java.util.LinkedHashMap<>();
      try {
        for (String part : new String(body, java.nio.charset.StandardCharsets.UTF_8).split("&")) {
          if (part.isEmpty()) continue;
          String[] kv = part.split("=", 2);
          String k = java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8);
          String v =
              java.net.URLDecoder.decode(
                  kv.length > 1 ? kv[1] : "", java.nio.charset.StandardCharsets.UTF_8);
          if (params.putIfAbsent(k, new String[] {v}) != null) {
            res.sendError(400);
            return;
          }
        }
      } catch (IllegalArgumentException e) {
        res.sendError(400);
        return;
      }
      java.util.Set<String> allowed =
          switch (req.getRequestURI()) {
            case "/register" -> java.util.Set.of("_csrf", "name", "business", "email", "password");
            case "/login" -> java.util.Set.of("_csrf", "username", "password");
            case "/recover" -> java.util.Set.of("_csrf", "email", "code", "password");
            case "/products" ->
                java.util.Set.of("_csrf", "name", "sku", "unit", "location", "recorded");
            case "/counts" ->
                java.util.Set.of("_csrf", "requestId", "productId", "recorded", "physical", "note");
            case "/settings" -> java.util.Set.of("_csrf", "current", "password");
            case "/settings/delete" -> java.util.Set.of("_csrf", "password");
            case "/logout" -> java.util.Set.of("_csrf");
            default -> java.util.Set.of();
          };
      if (!allowed.containsAll(params.keySet())) {
        res.sendError(400);
        return;
      }
      if (req.getQueryString() != null) {
        res.sendError(400);
        return;
      }
      HttpServletRequestWrapper wrapped =
          new HttpServletRequestWrapper(req) {
            public String getParameter(String n) {
              String[] v = params.get(n);
              return v == null ? null : v[0];
            }

            public String[] getParameterValues(String n) {
              return params.get(n);
            }

            public java.util.Map<String, String[]> getParameterMap() {
              return java.util.Collections.unmodifiableMap(params);
            }

            public java.util.Enumeration<String> getParameterNames() {
              return java.util.Collections.enumeration(params.keySet());
            }
          };
      chain.doFilter(wrapped, res);
      return;
    }
    chain.doFilter(req, res);
  }
}
