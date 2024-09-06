package com.soocompany.wodify;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableSchedulerLock(defaultLockAtLeastFor = "40s", defaultLockAtMostFor = "50s")
@SpringBootApplication
@EnableAsync
public class WodifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(WodifyApplication.class, args);
	}

}
