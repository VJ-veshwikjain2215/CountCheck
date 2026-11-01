package com.countcheck;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class Errors {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String invalid(IllegalArgumentException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error";
  }

  @ExceptionHandler(Store.Missing.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String missing(Model m) {
    m.addAttribute("message", "This record could not be found in your workspace.");
    return "error";
  }

  @ExceptionHandler(Store.Conflict.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  String conflict(Model m) {
    m.addAttribute(
        "message",
        "This request was already saved with different values. Check history before creating a new"
            + " entry.");
    return "error";
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  String unavailable(Model m, HttpServletRequest request, Exception failure) {
    StringBuilder diagnostic = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      diagnostic.append(cause.getClass().getSimpleName()).append(' ');
      if (cause instanceof java.sql.SQLException sql) {
        diagnostic.append("SQLSTATE=").append(sql.getSQLState()).append(' ');
      }
    }
    // Deliberately omit exception messages, SQL, request values and credentials.
    org.slf4j.LoggerFactory.getLogger(Errors.class).warn("Database request failed: {}", diagnostic);
    m.addAttribute(
        "message",
        "We could not confirm the result. If you were saving a count, restore and retry the same"
            + " entry. Check history before creating a new request.");
    return "error";
  }
}
