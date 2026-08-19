package vn.edu.school.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import vn.edu.school.schedule.shared.config.ExternalDatabaseUrl;

@SpringBootApplication
public class ScheduleManagerApplication {
    public static void main(String[] args) {
        ExternalDatabaseUrl.applyFromEnvironment();
        SpringApplication.run(ScheduleManagerApplication.class, args);
    }
}
