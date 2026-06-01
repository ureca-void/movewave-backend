package com.example.demo.config;

import com.example.demo.service.CustomOAuth2UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    private static final String FRONT_REDIRECT_URL = "FRONT_REDIRECT_URL";

    private static final List<String> ALLOWED_FRONT_ORIGINS = List.of(
            "http://127.0.0.1:5173",
            "http://localhost:5173",
            "http://127.0.0.1:4173",
            "http://localhost:4173",
            "https://test-three-phi-igcj296q62.vercel.app"
    );

    private static final String DEFAULT_FRONT_URL = "http://127.0.0.1:5173/";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Spotify 로그인 시작 전에 프론트 redirect 주소를 세션에 저장
                .addFilterBefore(
                        frontRedirectSaveFilter(),
                        OAuth2AuthorizationRequestRedirectFilter.class
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user").permitAll()
                        .anyRequest().permitAll()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )

                        // Spotify 로그인 성공 후 원래 프론트 주소로 이동
                        .successHandler((request, response, authentication) -> {
                            String redirectUrl = getRedirectUrlFromSession(request);

                            if (!isAllowedRedirect(redirectUrl)) {
                                redirectUrl = DEFAULT_FRONT_URL;
                            }

                            response.sendRedirect(redirectUrl);
                        })

                        // Spotify 로그인 실패/취소 후 원래 프론트 주소로 이동
                        .failureHandler((request, response, exception) -> {
                            String redirectUrl = getRedirectUrlFromSession(request);

                            if (!isAllowedRedirect(redirectUrl)) {
                                redirectUrl = DEFAULT_FRONT_URL;
                            }

                            response.sendRedirect(redirectUrl);
                        })
                )

                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")

                        // 로그아웃 후 프론트로 이동
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String redirectUrl = request.getParameter("redirect");

                            if (!isAllowedRedirect(redirectUrl)) {
                                redirectUrl = DEFAULT_FRONT_URL;
                            }

                            response.sendRedirect(redirectUrl);
                        })

                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                );

        return http.build();
    }

    // Spotify 로그인 요청 시 redirect 파라미터를 세션에 저장
    @Bean
    public OncePerRequestFilter frontRedirectSaveFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {

                if ("/oauth2/authorization/spotify".equals(request.getRequestURI())) {
                    String redirectUrl = request.getParameter("redirect");

                    if (isAllowedRedirect(redirectUrl)) {
                        request.getSession(true)
                                .setAttribute(FRONT_REDIRECT_URL, redirectUrl);
                    }
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    private String getRedirectUrlFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        String redirectUrl = (String) session.getAttribute(FRONT_REDIRECT_URL);
        session.removeAttribute(FRONT_REDIRECT_URL);

        return redirectUrl;
    }

    private boolean isAllowedRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(redirectUrl);
            String origin = uri.getScheme() + "://" + uri.getAuthority();

            return ALLOWED_FRONT_ORIGINS.contains(origin)
                    || origin.endsWith(".vercel.app");

        } catch (Exception e) {
            return false;
        }
    }

    // 프론트엔드에서 백엔드 API 요청 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://127.0.0.1:5173",
                "http://localhost:5173",
                "http://127.0.0.1:4173",
                "http://localhost:4173",
                "https://*.vercel.app"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository
    ) {
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository
                );

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }
}