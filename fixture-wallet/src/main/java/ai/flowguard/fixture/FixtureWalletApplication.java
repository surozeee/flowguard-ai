package ai.flowguard.fixture;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FixtureWalletApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FixtureWalletApplication.class)
                .profiles("fixture")
                .run(args);
    }
}
