package com.company.hr.attendance.diligence.infrastructure;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class DiligenceTimeConfiguration {
    @Bean("diligenceClock") Clock diligenceClock(){return Clock.systemUTC();}
}
