package com.ucms.identity.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Keycloak refusing a change (its password policy) is the caller's problem (400);
 *  Keycloak failing is ours (503). */
class KeycloakErrorMappingTest {

    private static HttpClientErrorException badRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", headers,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter();
        // RestClient installs this on the exceptions it throws.
        ex.setBodyConvertFunction(type -> {
            try {
                @SuppressWarnings("unchecked")
                Class<Object> target = (Class<Object>) type.toClass();
                return json.read(target, new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return ex;
    }

    @Test
    void passwordPolicyRejectionCarriesKeycloaksReason() {
        RuntimeException mapped = KeycloakAdminClient.translate("set password", badRequest(
                "{\"error\":\"invalidPasswordMinLengthMessage\",\"error_description\":\"Invalid password: minimum length 12.\"}"));
        assertThat(mapped).isInstanceOf(KeycloakRejectedException.class)
                .hasMessage("Invalid password: minimum length 12.");
    }

    @Test
    void badRequestWithoutAReadableReasonGetsAGenericOne() {
        RuntimeException mapped = KeycloakAdminClient.translate("set password", badRequest("not json"));
        assertThat(mapped).isInstanceOf(KeycloakRejectedException.class)
                .hasMessage("The sign-in service rejected this change.");
    }

    @Test
    void anythingElseMeansKeycloakIsUnavailable() {
        assertThat(KeycloakAdminClient.translate("create user", new ResourceAccessException("Connection refused")))
                .isInstanceOf(KeycloakUnavailableException.class);
        assertThat(KeycloakAdminClient.translate("create user",
                HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null)))
                .isInstanceOf(KeycloakUnavailableException.class);
    }
}
