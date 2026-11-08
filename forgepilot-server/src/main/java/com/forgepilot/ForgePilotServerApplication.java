package com.forgepilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.forgepilot.system.persistence",
        "com.forgepilot.project.persistence",
        "com.forgepilot.execution.persistence",
        "com.forgepilot.delivery.persistence"
})
public class ForgePilotServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgePilotServerApplication.class, args);
    }
}
