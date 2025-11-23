package com.offerhunt.auth.oauth;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface GithubEmailService {

    /**
     * Возвращает email GitHub пользователя. Сначала берёт из атрибутов,
     * при отсутствии — ходит в /user/emails и выбирает primary + verified.
     * Может вернуть null, если email не удалось получить.
     */
    String resolveEmail(OAuth2User user, OAuth2AuthorizedClient client);
}
