package com.offerhunt.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.issuer=http://issuer.test",
    "app.audience=offerhunt-api"
})
class SecuritySmokeTest {

    @Autowired
    MockMvc mvc;

    static WireMockServer wm;
    static RSAKey rsa;

    @BeforeAll
    static void beforeAll() throws Exception {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort()); // <-- 3.x
        wm.start();
        WireMock.configureFor("localhost", wm.port());

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        rsa = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) kp.getPublic())
            .privateKey((java.security.interfaces.RSAPrivateKey) kp.getPrivate())
            .keyID("k1")
            .build();

        String jwks = new JWKSet(rsa.toPublicJWK()).toString();
        wm.stubFor(
            WireMock.get(WireMock.urlEqualTo("/.well-known/jwks.json"))
                .willReturn(WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwks))
        );

        System.setProperty("app.jwks-url",
            "http://localhost:" + wm.port() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void afterAll() {
        wm.stop();
    }

    private String jwt(String role) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("http://issuer.test")
            .audience("offerhunt-api")
            .subject(UUID.randomUUID().toString())
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
            .claim("role", role)
            .build();
        SignedJWT j = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(),
            claims
        );
        j.sign(new RSASSASigner(rsa.toPrivateKey()));
        return j.serialize();
    }

    @Test
    void unauthorized_without_token() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidden_when_no_admin_role() throws Exception {
        mvc.perform(get("/api/admin/ping")
                .header("Authorization", "Bearer " + jwt("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void ok_with_valid_token() throws Exception {
        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + jwt("USER")))
            .andExpect(status().isOk());
    }
}