package com.example.demo.config;

import com.example.demo.service.CustomOAuth2UserService;
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
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user").permitAll().anyRequest().permitAll()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        // ⭐ defaultSuccessUrl을 제거하고 successHandler를 추가합니다.
                        .successHandler((request, response, authentication) -> {
                            // 세션이나 쿼리 파라미터에서 redirect 주소를 가져옵니다.
                            // OAuth2 로그인 시에는 주소창에 파라미터가 유지되지 않을 수 있으므로
                            // 프론트에서 보낸 state나 별도의 쿠키를 체크해야 할 수도 있지만,
                            // 가장 간단한 방법은 요청 파라미터를 확인하는 것입니다.
                            String redirectUrl = request.getParameter("redirect");

                            if (redirectUrl == null || redirectUrl.isEmpty()) {
                                redirectUrl = "http://127.0.0.1:5500/index.html"; // 기본값
                            }

                            response.sendRedirect(redirectUrl);
                        })
                        // ⭐ 실패(취소 포함) 시 핸들러 추가
                        .failureHandler((request, response, exception) -> {
                            // 로그아웃과 마찬가지로 프론트에서 보낸 redirect 파라미터를 읽습니다.
                            // 만약 파라미터 유실이 걱정된다면 Referer 헤더를 사용할 수도 있습니다.
                            String redirectUrl = request.getParameter("redirect");

                            if (redirectUrl == null || redirectUrl.isEmpty()) {
                                // Referer는 이전 페이지 주소를 담고 있는 브라우저 헤더입니다.
                                redirectUrl = request.getHeader("Referer");
                            }

                            if (redirectUrl == null || redirectUrl.contains("/login")) {
                                redirectUrl = "http://127.0.0.1:5500/index.html"; // 최후의 보루
                            }

                            // 에러 페이지로 보내지 않고 원래 페이지로 리다이렉트
                            response.sendRedirect(redirectUrl);
                        })
                )
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // ⭐ SuccessUrl 대신 SuccessHandler를 사용하여 동적 리다이렉트 처리
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String redirectUrl = request.getParameter("redirect");

                            // 파라미터가 없으면 기본 메인 페이지로, 있으면 해당 주소로 이동
                            if (redirectUrl == null || redirectUrl.isEmpty()) {
                                redirectUrl = "http://127.0.0.1:5500/index.html";
                            }

                            response.sendRedirect(redirectUrl);
                        })
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                );

        return http.build();
    }

    // 프론트엔드(5500)에서 백엔드(8080)로의 접근을 허용하는 CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://127.0.0.1:5500"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Set-Cookie"));


        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken() // 리프레시 토큰을 통한 갱신 기능 활성화
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientRepository);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }
}