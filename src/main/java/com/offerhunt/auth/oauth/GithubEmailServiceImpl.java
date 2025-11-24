package com.offerhunt.auth.oauth;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GithubEmailServiceImpl implements GithubEmailService {

    private static final Logger log = LoggerFactory.getLogger(GithubEmailServiceImpl.class);

    private final RestClient restClient;

    public GithubEmailServiceImpl(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("https://api.github.com")
            .build();
    }

    @Override
    public GithubEmail resolveEmail(OAuth2User user, OAuth2AuthorizedClient client) {
        String email = user.<String>getAttribute("email");
        if (email != null) {
            // из user-атрибутов мы не знаем флаг верификации, считаем false
            return new GithubEmail(email, false);
        }

        if (client == null || client.getAccessToken() == null) {
            log.warn("GitHub OAuth: access token is missing while resolving email");
            return null;
        }

        try {
            List<Map<String, Object>> emails = restClient.get()
                .uri("/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

            if (emails == null || emails.isEmpty()) {
                return null;
            }

            // primary && verified
            Map<String, Object> primaryVerifiedEntry = emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                .findFirst()
                .orElse(null);

            if (primaryVerifiedEntry != null) {
                String e = (String) primaryVerifiedEntry.get("email");
                if (e != null) {
                    return new GithubEmail(e, true);
                }
            }

            // fallback — первый любой email, флаг verified берём из записи
            for (Map<String, Object> e : emails) {
                String addr = (String) e.get("email");
                if (addr != null) {
                    boolean verified = Boolean.TRUE.equals(e.get("verified"));
                    return new GithubEmail(addr, verified);
                }
            }

            return null;
        } catch (RestClientException ex) {
            log.error("GitHub OAuth: failed to call /user/emails", ex);
            return null;
        }
    }
}
