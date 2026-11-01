package com.example.siga;

import org.springframework.boot.SpringApplication;

public class TestSigaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SigaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
