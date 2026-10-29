package com.ucms.analytics.client;

import com.ucms.analytics.exception.UpstreamException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/** Uniform mapping of another service's failures: its 404 → our 404 with a message that
 *  says what was missing; anything else (5xx, connection refused, timeout) → 503. */
final class Upstream {
    private Upstream() {}

    static <T> T call(String service, String notFoundMessage, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value() && notFoundMessage != null) {
                throw new EntityNotFoundException(notFoundMessage);
            }
            throw new UpstreamException(service + " returned " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw new UpstreamException(service + " is unavailable right now — try again shortly.");
        }
    }
}
