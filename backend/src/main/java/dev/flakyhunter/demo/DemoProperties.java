package dev.flakyhunter.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The public demo project (flakyhunter.demo.*). */
@ConfigurationProperties("flakyhunter.demo")
public record DemoProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("demo") String slug,
		@DefaultValue("60") int days,
		@DefaultValue("300") int tests,
		@DefaultValue("4") int runsPerDay) {
}
