package com.countcheck;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security {
  @Bean
  static org.springframework.security.core.session.SessionRegistry sessions() {
    return new org.springframework.security.core.session.SessionRegistryImpl();
  }

  @Bean
  static org.springframework.boot.web.servlet.ServletListenerRegistrationBean<
          org.springframework.security.web.session.HttpSessionEventPublisher>
      sessionEvents() {
    return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(
        new org.springframework.security.web.session.HttpSessionEventPublisher());
  }

  @Bean
  static PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http, org.springframework.security.core.session.SessionRegistry sessions)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/login",
                        "/register",
                        "/recover",
                        "/privacy",
                        "/health",
                        "/css/**",
                        "/js/**",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        "/app/**", "/products/**", "/counts/**", "/settings", "/settings/delete")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(
            s -> s.maximumSessions(5).expiredUrl("/login?expired").sessionRegistry(sessions))
        .formLogin(
            f -> f.loginPage("/login").defaultSuccessUrl("/app", true).failureUrl("/login?error"))
        .logout(
            l ->
                l.logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; font-src 'self'; base-uri 'none'; frame-ancestors"
                                + " 'none'; form-action 'self'")))
        .build();
  }
}
