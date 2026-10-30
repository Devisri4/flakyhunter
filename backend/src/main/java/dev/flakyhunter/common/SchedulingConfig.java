package dev.flakyhunter.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (ingestion worker, stuck-job recovery). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
