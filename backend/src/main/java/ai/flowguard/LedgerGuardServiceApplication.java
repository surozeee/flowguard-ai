package ai.flowguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "ai.flowguard",
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "ai\\.flowguard\\.fixture\\..*"),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "ai\\.flowguard\\.gateway\\..*"),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "ai\\.flowguard\\.auth\\..*")
        })
public class LedgerGuardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerGuardServiceApplication.class, args);
    }
}
