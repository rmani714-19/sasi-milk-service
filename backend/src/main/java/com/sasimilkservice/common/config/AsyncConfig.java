package com.sasimilkservice.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables async + scheduled execution. Notifications and other side-effects run
 * off the request thread (spec: "Do not block API requests while sending
 * notifications"). In production the queue is Amazon SQS; here it is the
 * {@code notifications} table plus an in-process worker.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
