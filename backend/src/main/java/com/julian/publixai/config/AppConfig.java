package com.julian.publixai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * AppConfig
 *
 * Purpose: central place to wire production-safe clients and shared infrastructure beans.
 * This config creates the WebClient used to call the Python ML service.
 *
 * Guarantees:
 * - Explicit connection/read/write timeouts (no hangs).
 * - Small, bounded in-memory buffers for JSON payloads.
 * - Secret header applied to every ML request.
 */
@Configuration
@EnableConfigurationProperties(MlProperties.class)
public class AppConfig {

    /**
     * WebClient for the ML service (FastAPI).
     *
     * Notes:
     * - Timeouts: end-to-end guardrails using Reactor Netty + handlers.
     * - Headers: X-ML-Secret added automatically from properties.
     * - Buffering: limit max in-memory size to avoid unbounded growth.
     */
    @Bean
    public WebClient mlWebClient(MlProperties props) {
        final int timeoutMs = props.getTimeoutMs();

        // Netty HTTP client with strict timeouts at all stages
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        // Keep memory usage predictable for JSON
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2 MB
                .build();

        String baseUrl = props.getBaseUrl() != null ? props.getBaseUrl() : "http://localhost:8000";
        String secret = props.getSecret() != null ? props.getSecret() : "";
        
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-ML-Secret", secret)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                // Turn any 5xx into a clear exception if callers use exchange() paths
                .filter(ExchangeFilterFunctions.statusError(
                        HttpStatusCode::is5xxServerError,
                        resp -> new IllegalStateException("ML service 5xx: " + resp.statusCode())))
                .build();
    }
}
