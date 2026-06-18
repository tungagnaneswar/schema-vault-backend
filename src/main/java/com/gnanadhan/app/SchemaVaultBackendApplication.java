package com.gnanadhan.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SchemaVaultBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchemaVaultBackendApplication.class, args);
	}

}
