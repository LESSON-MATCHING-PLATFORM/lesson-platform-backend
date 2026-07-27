package com.kosa.fillinv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FillinvApplication {

    public static void main(String[] args) {
        SpringApplication.run(FillinvApplication.class, args);
    }

}
