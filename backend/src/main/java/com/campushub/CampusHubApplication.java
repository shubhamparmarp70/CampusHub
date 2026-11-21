package com.campushub;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling
public class CampusHubApplication {
  public static void main(String[] a){ TimeZone.setDefault(TimeZone.getTimeZone("UTC")); SpringApplication.run(CampusHubApplication.class, a); }
}
