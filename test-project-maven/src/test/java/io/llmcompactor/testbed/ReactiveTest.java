package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that produces Project Reactor stack traces with embedded call sites.
 * Reactor stack traces are notoriously complex with suppressed exceptions
 * showing the operator assembly point.
 */
public class ReactiveTest {

    @Test
    void testReactiveError() {
        // This will throw an exception with Reactor's complex stack trace
        // including suppressed exceptions showing operator call sites
        Mono<String> mono = Mono.error(new IllegalStateException("Reactive error from Mono"));
        
        // Intentionally wrong assertion to make test fail
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            mono.block();
        });
        
        assertEquals("WRONG MESSAGE", thrown.getMessage());
    }

    @Test
    void testReactiveErrorWithOperatorTrace() {
        // This produces a stack trace with operator assembly location
        Mono<String> result = Mono.<String>just("value")
            .<String>flatMap(v -> Mono.error(new RuntimeException("Error in flatMap")))
            .map(v -> v.toUpperCase());
        
        // Intentionally wrong assertion to make test fail
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            result.block();
        });
        
        assertEquals("WRONG MESSAGE", thrown.getMessage());
    }

    @Test
    void testReactiveErrorWithMultipleOperators() {
        // Complex chain producing deep stack trace with multiple operators
        Mono<Integer> mono = Mono.just(10)
            .map(v -> v * 2)
            .flatMap(v -> {
                if (v > 15) {
                    return Mono.error(new IllegalArgumentException("Value too large: " + v));
                }
                return Mono.just(v);
            })
            .map(v -> v + 1);
        
        // Intentionally wrong assertion to make test fail
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            mono.block();
        });
        
        assertTrue(thrown.getMessage().contains("WRONG"));
    }
}
