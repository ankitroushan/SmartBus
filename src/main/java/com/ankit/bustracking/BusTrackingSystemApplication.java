package com.ankit.bustracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BusTrackingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(BusTrackingSystemApplication.class, args);
	}

}
