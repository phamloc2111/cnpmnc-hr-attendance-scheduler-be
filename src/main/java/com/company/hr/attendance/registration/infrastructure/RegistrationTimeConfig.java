package com.company.hr.attendance.registration.infrastructure;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RegistrationTimeConfig {
    @Bean("registrationClock") Clock registrationClock() { return Clock.systemUTC(); }
}
