package com.offerhunt.auth.oauth;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface GithubEmailService {

    /**
     * Email GitHub-пользователя и флаг верификации.
     * Может вернуть null, если email не удалось получить.
     */
    record GithubEmail(String email, boolean verified) { }

    GithubEmail resolveEmail(OAuth2User user, OAuth2AuthorizedClient client);
}