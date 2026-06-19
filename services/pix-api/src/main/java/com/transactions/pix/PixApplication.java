package com.transactions.pix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PixApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixApplication.class, args);
	}

}
