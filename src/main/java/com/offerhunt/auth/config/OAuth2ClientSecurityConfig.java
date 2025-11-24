package com.offerhunt.auth.config;

import com.offerhunt.auth.oauth.OAuth2LoginFailureHandler;
import com.offerhunt.auth.oauth.OAuth2LoginSuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
public class OAuth2ClientSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain oauth2ClientSecurityFilterChain(
        HttpSecurity http,
        OAuth2LoginSuccessHandler successHandler,
        OAuth2LoginFailureHandler failureHandler
    ) throws Exception {

        http
            .securityMatcher("/oauth2/authorization/**", "/login/oauth2/**")
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            );

        return http.build();
    }
}
