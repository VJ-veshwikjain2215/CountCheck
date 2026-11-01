package com.countcheck;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class Web {
  private final Store store;
  private final org.springframework.security.core.session.SessionRegistry sessions;

  public Web(Store store, org.springframework.security.core.session.SessionRegistry sessions) {
    this.store = store;
    this.sessions = sessions;
  }

  private Store.Member currentMember(Principal principal) {
    var request =
        ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder
                    .currentRequestAttributes())
            .getRequest();
    String key = Web.class.getName() + ".member";
    var member = (Store.Member) request.getAttribute(key);
    if (member == null) {
      member = store.member(principal.getName());
      request.setAttribute(key, member);
    }
    return member;
  }

  private void revoke(String email) {
    for (Object p : sessions.getAllPrincipals())
      if (p instanceof org.springframework.security.core.userdetails.UserDetails u
          && u.getUsername().equalsIgnoreCase(email))
        for (var session : sessions.getAllSessions(p, false)) session.expireNow();
  }

  @ModelAttribute
  void common(Model m, Principal principal, jakarta.servlet.http.HttpServletRequest request) {
    if (principal != null && !request.getRequestURI().equals("/health")) {
      var member = currentMember(principal);
      m.addAttribute("member", member);
      m.addAttribute(
          "initial",
          new String(Character.toChars(member.name().codePointAt(0)))
              .toUpperCase(java.util.Locale.ROOT));
    }
  }

  @GetMapping("/")
  String home() {
    return "home";
  }

  @GetMapping("/health")
  @ResponseBody
  String health() {
    return "ok";
  }

  @GetMapping("/privacy")
  String privacy() {
    return "privacy";
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/register")
  String register() {
    return "register";
  }

  @PostMapping("/register")
  String register(
      @RequestParam String name,
      @RequestParam String business,
      @RequestParam String email,
      @RequestParam String password,
      Model m) {
    try {
      String code = store.register(name, business, email, password);
      m.addAttribute("recovery", code);
      return "recovery-success";
    } catch (IllegalArgumentException | DuplicateKeyException e) {
      m.addAttribute(
          "error",
          e instanceof DuplicateKeyException
              ? "An account could not be created with these details. Try signing in."
              : e.getMessage());
      m.addAttribute("name", name);
      m.addAttribute("business", business);
      m.addAttribute("email", email);
      return "register";
    }
  }

  @GetMapping("/app")
  String dashboard(Principal p, Model m) {
    long b = currentMember(p).businessId();
    m.addAttribute("stats", store.stats(b));
    m.addAttribute("entries", store.history(b, false, null, null, 6));
    m.addAttribute("page", "overview");
    return "dashboard";
  }

  @GetMapping("/products")
  String products(
      Principal p,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Long afterId,
      Model m) {
    var rows = store.searchProducts(currentMember(p).businessId(), q, after, afterId, 51);
    boolean more = rows.size() > 50;
    var shown = rows.subList(0, Math.min(50, rows.size()));
    m.addAttribute("products", shown);
    m.addAttribute("productCount", store.productCount(currentMember(p).businessId()));
    m.addAttribute("moreProducts", more);
    if (more) m.addAttribute("lastProduct", shown.getLast());
    m.addAttribute("q", q);
    m.addAttribute("page", "products");
    return "products";
  }

  @PostMapping("/products")
  String product(
      Principal p,
      @RequestParam String name,
      @RequestParam String sku,
      @RequestParam String unit,
      @RequestParam String location,
      @RequestParam String recorded,
      RedirectAttributes flash) {
    try {
      store.addProduct(currentMember(p).businessId(), name, sku, unit, location, recorded);
      flash.addFlashAttribute("message", "Product added. Ready for your first count.");
    } catch (IllegalArgumentException | DuplicateKeyException e) {
      flash.addFlashAttribute(
          "error",
          e instanceof DuplicateKeyException
              ? "That SKU already exists in your workspace."
              : e.getMessage());
    }
    return "redirect:/products";
  }

  @GetMapping("/counts/new")
  String form(Principal p, @RequestParam(defaultValue = "") String q, Model m) {
    m.addAttribute(
        "products", store.searchProducts(currentMember(p).businessId(), q, null, null, 100));
    m.addAttribute("q", q);
    m.addAttribute("requestId", UUID.randomUUID());
    m.addAttribute("page", "new");
    return "count-form";
  }

  @PostMapping("/counts")
  String submit(
      Principal p,
      @RequestParam String requestId,
      @RequestParam long productId,
      @RequestParam String recorded,
      @RequestParam String physical,
      @RequestParam(defaultValue = "") String note,
      Model m,
      jakarta.servlet.http.HttpServletResponse response) {
    var member = currentMember(p);
    try {
      long id = store.submit(member, requestId, productId, recorded, physical, note);
      response.setStatus(303);
      response.setHeader("Location", "/counts/" + id);
      return null;
    } catch (IllegalArgumentException ex) {
      response.setStatus(400);
      m.addAttribute("error", ex.getMessage());
      var available = new java.util.ArrayList<>(store.products(member.businessId()));
      if (available.stream().noneMatch(product -> product.id() == productId)) {
        try {
          available.add(store.product(member.businessId(), productId));
        } catch (Store.Missing ignored) {
        }
      }
      m.addAttribute("products", available);
      m.addAttribute("page", "new");
      m.addAttribute("requestId", requestId);
      m.addAttribute("selectedProduct", productId);
      m.addAttribute("recorded", recorded);
      m.addAttribute("physical", physical);
      m.addAttribute("note", note);
      return "count-form";
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException ex) {
      response.setStatus(503);
      m.addAttribute("requestId", requestId);
      m.addAttribute("productId", productId);
      m.addAttribute("recorded", recorded);
      m.addAttribute("physical", physical);
      m.addAttribute("note", note);
      return "retry";
    }
  }

  @GetMapping("/counts")
  String history(
      Principal p,
      @RequestParam(defaultValue = "false") boolean mismatch,
      @RequestParam(required = false) String before,
      @RequestParam(required = false) Long beforeId,
      Model m,
      jakarta.servlet.http.HttpServletRequest request) {
    for (var parameter : request.getParameterMap().entrySet())
      if (!Set.of("mismatch", "before", "beforeId").contains(parameter.getKey())
          || parameter.getValue().length != 1)
        throw new IllegalArgumentException("Invalid history parameters.");
    String filter = request.getParameter("mismatch");
    if (filter != null && !filter.equals("true") && !filter.equals("false"))
      throw new IllegalArgumentException("Invalid mismatch filter.");
    if ((before == null) != (beforeId == null) || beforeId != null && beforeId <= 0)
      throw new IllegalArgumentException("Invalid history cursor.");
    Instant instant = null;
    if (before != null) {
      try {
        instant = Instant.parse(before);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid history cursor.");
      }
    }
    var rows = store.history(currentMember(p).businessId(), mismatch, instant, beforeId, 21);
    boolean more = rows.size() > 20;
    var shown = rows.subList(0, Math.min(20, rows.size()));
    m.addAttribute("entries", shown);
    m.addAttribute("more", more);
    if (more) m.addAttribute("last", shown.getLast());
    m.addAttribute("mismatch", mismatch);
    m.addAttribute("page", "history");
    return "history";
  }

  @GetMapping("/counts/{id}")
  String detail(Principal p, @PathVariable long id, Model m) {
    m.addAttribute("entry", store.detail(currentMember(p).businessId(), id));
    m.addAttribute("page", "history");
    return "detail";
  }

  @GetMapping("/counts/export.csv")
  ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody>
      export(Principal p) {
    long business = currentMember(p).businessId();
    if (store.entryCount(business) > 10000)
      throw new IllegalArgumentException(
          "Export is limited to 10,000 records. Contact support for larger exports.");
    org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body =
        output -> {
          var writer =
              new java.io.BufferedWriter(
                  new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8));
          writer.write("ID,Item,SKU,Location,Recorded,Physical,Difference,UTC,Note\r\n");
          Instant before = null;
          Long beforeId = null;
          int remaining = 10000;
          while (remaining > 0) {
            var rows = store.history(business, false, before, beforeId, Math.min(500, remaining));
            if (rows.isEmpty()) break;
            for (var e : rows) {
              writer.write(
                  e.id()
                      + ","
                      + cell(e.name())
                      + ","
                      + cell(e.sku())
                      + ","
                      + cell(e.location())
                      + ","
                      + e.recorded()
                      + ","
                      + e.physical()
                      + ","
                      + e.delta()
                      + ","
                      + e.created()
                      + ","
                      + cell(e.note())
                      + "\r\n");
            }
            remaining -= rows.size();
            var last = rows.getLast();
            before = last.created();
            beforeId = last.id();
            writer.flush();
            if (rows.size() < 500) break;
          }
          writer.flush();
        };
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=countcheck-history.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(body);
  }

  static String cell(String s) {
    if (s.startsWith("=")
        || s.startsWith("+")
        || s.startsWith("-")
        || s.startsWith("@")
        || s.startsWith("\t")
        || s.startsWith("\r")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  @GetMapping("/settings")
  String settings(Model m) {
    m.addAttribute("page", "settings");
    return "settings";
  }

  @PostMapping("/settings")
  String password(
      Principal p,
      @RequestParam String current,
      @RequestParam String password,
      RedirectAttributes flash) {
    try {
      store.changePassword(currentMember(p), current, password);
      revoke(p.getName());
      return "redirect:/login?changed";
    } catch (IllegalArgumentException e) {
      flash.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings";
  }

  @GetMapping("/recover")
  String recover() {
    return "recover";
  }

  @PostMapping("/recover")
  String recover(
      @RequestParam String email,
      @RequestParam String code,
      @RequestParam String password,
      Model m) {
    try {
      String next = store.recover(email, code, password);
      revoke(email);
      m.addAttribute("recovery", next);
      return "recovery-success";
    } catch (IllegalArgumentException e) {
      m.addAttribute("error", e.getMessage());
      return "recover";
    }
  }

  @PostMapping("/settings/delete")
  String delete(
      Principal p, @RequestParam String password, jakarta.servlet.http.HttpServletRequest request) {
    store.deleteAccount(currentMember(p), password);
    revoke(p.getName());
    request.getSession().invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return "redirect:/";
  }
}
