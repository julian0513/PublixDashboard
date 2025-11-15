package com.julian.publixai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Publix AI Dashboard Application
 * 
 * Main Spring Boot application entry point.
 * 
 * Default profile is 'dev' which uses PostgreSQL.
 * Use 'h2' profile for in-memory H2 database (testing only).
 */
@SpringBootApplication
public class PublixAiDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(PublixAiDashboardApplication.class, args);
    }
}
