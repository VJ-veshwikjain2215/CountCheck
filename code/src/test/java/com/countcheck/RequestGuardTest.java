package com.countcheck;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestGuardTest {
  MockHttpServletRequest form(String body) {
    var req = new MockHttpServletRequest("POST", "/counts");
    req.setRemoteAddr("127.0.0.1");
    req.setContentType("application/x-www-form-urlencoded");
    req.setContent(body.getBytes(StandardCharsets.UTF_8));
    return req;
  }

  @Test
  void formIsParsedBeforeSecurity() throws Exception {
    var response = new MockHttpServletResponse();
    var called = new AtomicBoolean();
    new RequestGuard()
        .doFilter(
            form("_csrf=token&note=hello+world&recorded=0"),
            response,
            (req, res) -> {
              assertEquals("hello world", req.getParameter("note"));
              assertEquals("token", req.getParameter("_csrf"));
              called.set(true);
            });
    assertTrue(called.get());
  }

  @Test
  void rejectsDuplicateParameters() throws Exception {
    var response = new MockHttpServletResponse();
    new RequestGuard()
        .doFilter(
            form("recorded=1&recorded=2"),
            response,
            (req, res) -> fail("Must not reach controller"));
    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsUnexpectedFields() throws Exception {
    var response = new MockHttpServletResponse();
    new RequestGuard()
        .doFilter(
            form("difference=100"), response, (req, res) -> fail("Must not reach controller"));
    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMalformedUrlEncoding() throws Exception {
    var response = new MockHttpServletResponse();
    new RequestGuard()
        .doFilter(form("note=%ZZ"), response, (req, res) -> fail("Must not reach controller"));
    assertEquals(400, response.getStatus());
  }

  @Test
  void capsUnknownLengthBodies() throws Exception {
    var req =
        new MockHttpServletRequest("POST", "/counts") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    req.setRemoteAddr("127.0.0.1");
    req.setContentType("application/x-www-form-urlencoded");
    req.setContent(("note=" + "x".repeat(17000)).getBytes(StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse();
    new RequestGuard()
        .doFilter(req, response, (request, res) -> fail("Must not parse oversize body"));
    assertEquals(413, response.getStatus());
  }

  @Test
  void rejectsPostQueryStrings() throws Exception {
    var req = form("note=test");
    req.setQueryString("recorded=100");
    var response = new MockHttpServletResponse();
    new RequestGuard()
        .doFilter(req, response, (request, res) -> fail("Ambiguous parameters must be rejected"));
    assertEquals(400, response.getStatus());
  }
}
