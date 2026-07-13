package com.example.attendance.common.config;

import com.example.attendance.common.config.security.CustomAccessDeniedHandler;
import com.example.attendance.common.config.security.CustomAuthenticationEntryPoint;
import com.example.attendance.common.config.security.JsonAuthenticationFilter;
import com.example.attendance.common.config.security.LoginFailureHandler;
import com.example.attendance.common.config.security.LoginSuccessHandler;
import com.example.attendance.common.config.security.SpaCsrfTokenRequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource) {
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager) throws Exception {

        var jsonAuthFilter = jsonAuthenticationFilter(authenticationManager);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers(
                    PathPatternRequestMatcher.withDefaults().matcher("/api/auth/login"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/auth/logout")
                )
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(this::configureAuthorization)
            .addFilterAt(jsonAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(csrfTokenForceLoadFilter(), BasicAuthenticationFilter.class)
            .logout(this::configureLogout)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint(objectMapper))
                .accessDeniedHandler(new CustomAccessDeniedHandler(objectMapper))
            );

        return http.build();
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>
                .AuthorizationManagerRequestMatcherRegistry auth) {
        auth
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

            .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()

            .requestMatchers(HttpMethod.GET, "/api/departments").authenticated()
            .requestMatchers(HttpMethod.POST, "/api/attendance/clock-in").authenticated()
            .requestMatchers(HttpMethod.POST, "/api/attendance/clock-out").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/attendance/today").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/attendance/history").authenticated()
            .requestMatchers(HttpMethod.PUT, "/api/attendance/*/memo").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/attendance/*/memo/history").authenticated()
            .requestMatchers(HttpMethod.POST, "/api/corrections").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/corrections").authenticated()

            .requestMatchers("/api/attendance/team/**").authenticated()
            .requestMatchers("/api/corrections/pending/**").authenticated()
            .requestMatchers("/api/corrections/*/approve").authenticated()
            .requestMatchers("/api/corrections/*/reject").authenticated()

            .requestMatchers("/api/employees/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/departments").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/departments/{id}").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/attendance/all").hasRole("ADMIN")
            .requestMatchers("/api/reports/**").hasRole("ADMIN")

            .anyRequest().denyAll();
    }

    private void configureLogout(LogoutConfigurer<HttpSecurity> logout) {
        logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((request, response, authentication) ->
                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID");
    }

    private static Filter csrfTokenForceLoadFilter() {
        return (request, response, chain) -> {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            chain.doFilter(request, response);
        };
    }

    private JsonAuthenticationFilter jsonAuthenticationFilter(
            AuthenticationManager authenticationManager) {
        JsonAuthenticationFilter filter = new JsonAuthenticationFilter(objectMapper);
        filter.setFilterProcessesUrl("/api/auth/login");
        filter.setAuthenticationManager(authenticationManager);
        filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        filter.setAuthenticationSuccessHandler(
            new LoginSuccessHandler(objectMapper));
        filter.setAuthenticationFailureHandler(new LoginFailureHandler(objectMapper));
        return filter;
    }
}
