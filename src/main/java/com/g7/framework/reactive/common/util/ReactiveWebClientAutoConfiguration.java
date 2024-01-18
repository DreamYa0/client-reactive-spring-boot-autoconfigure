package com.g7.framework.reactive.common.util;

import com.g7.framework.reactive.common.util.http.ReactiveRest;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.spring.config.EnableReactiveFeignClients;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author dreamyao
 * @title
 * @date 2022/1/26 2:07 下午
 * @since 1.0.0
 */
@AutoConfiguration
@EnableReactiveFeignClients(value = "com.*.**.integrate.client")
public class ReactiveWebClientAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveWebClientAutoConfiguration.class);
    private static final String G7_INSIDE_HEAD_REQUEST_TIME = "x-inside-request-time";
    private static final ConnectionProvider CONNECTION_PROVIDER = ConnectionProvider
            .builder("reactive-client")
            .maxConnections(Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE)
            .maxIdleTime(Duration.ofSeconds(60))
            .pendingAcquireMaxCount(Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE)
            .build();

    @Bean
    @ConditionalOnMissingBean(value = WebClient.class)
    public WebClient webClient() {
        if (logger.isDebugEnabled()) {
            logger.debug("initialization WebClient...");
        }
        return WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(
                                HttpClient.create(
                                                // 弹性扩容模式
                                                CONNECTION_PROVIDER)
                                        .compress(Boolean.TRUE)
                                        .doOnRequest((httpClientRequest, connection) -> {
                                            //设置全局请求30秒超时
                                            connection.addHandlerFirst(new ReadTimeoutHandler(30,
                                                    TimeUnit.SECONDS));

                                            httpClientRequest.requestHeaders().add(G7_INSIDE_HEAD_REQUEST_TIME,
                                                    System.currentTimeMillis());
                                        })
                                        .doOnResponse((httpClientResponse, connection) -> {
                                            String requestTime = httpClientResponse.requestHeaders()
                                                    .get(G7_INSIDE_HEAD_REQUEST_TIME);
                                            if (Objects.nonNull(requestTime)) {
                                                httpClientResponse.responseHeaders().add(G7_INSIDE_HEAD_REQUEST_TIME,
                                                        requestTime);
                                            }
                                        })
                                        .doOnError((httpClientRequest, throwable) ->
                                                        logger.error("Web client request error", throwable),
                                                (httpClientResponse, throwable) ->
                                                        logger.error("Web client response error", throwable))
                        )
                )
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecConfigurer -> codecConfigurer.defaultCodecs()
                                .maxInMemorySize(5 * 1024 * 1024))
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(value = ReactiveRest.class)
    public ReactiveRest reactiveRest(WebClient webClient) {
        if (logger.isDebugEnabled()) {
            logger.debug("initialization ReactiveRest...");
        }
        return new ReactiveRest(webClient);
    }
}
