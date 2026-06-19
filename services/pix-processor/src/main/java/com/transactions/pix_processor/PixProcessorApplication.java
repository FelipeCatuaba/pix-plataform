package com.transactions.pix_processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class PixProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixProcessorApplication.class, args);
	}

}
