package com.campus.complaints;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusComplaintManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusComplaintManagementApplication.class, args);
    }
}
