package com.premaseem.maestro.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetryExecutorTest {

    @Mock
    private Sleeper sleeper;

    private RetryExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RetryExecutor(RetryPolicy.of(3, Duration.ofMillis(10)), sleeper);
    }

    @Test
    void succeedsOnFirstAttemptWithoutSleeping() {
        RetryOutcome<String> outcome = executor.execute(() -> "ok");

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.value()).isEqualTo("ok");
        assertThat(outcome.attempts()).isEqualTo(1);
        verify(sleeper, never()).sleep(any(Duration.class));
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        AtomicInteger calls = new AtomicInteger(0);

        RetryOutcome<String> outcome = executor.execute(() -> {
            int call = calls.incrementAndGet();
            if (call < 3) {
                throw new TransientStageException("flaky, call " + call);
            }
            return "recovered";
        });

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.value()).isEqualTo("recovered");
        assertThat(outcome.attempts()).isEqualTo(3);
        verify(sleeper, times(2)).sleep(Duration.ofMillis(10));
    }

    @Test
    void givesUpAfterMaxAttemptsAndReportsTheLastFailure() {
        RetryOutcome<String> outcome = executor.execute(() -> {
            throw new TransientStageException("always fails");
        });

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.attempts()).isEqualTo(3);
        assertThat(outcome.failure()).hasMessageContaining("always fails");
        verify(sleeper, times(2)).sleep(any(Duration.class));
    }

    @Test
    void nonTransientFailuresPropagateImmediatelyWithoutRetrying() {
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new IllegalStateException("policy violation, not retryable");
        })).isInstanceOf(IllegalStateException.class);

        verify(sleeper, never()).sleep(any(Duration.class));
    }
}
