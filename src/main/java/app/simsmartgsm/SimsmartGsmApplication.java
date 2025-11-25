package app.simsmartgsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class SimsmartGsmApplication {

	public static void main(String[] args) {

		// Khởi chạy app và lưu context lại
		ConfigurableApplicationContext ctx = SpringApplication.run(SimsmartGsmApplication.class, args);

		// Gán lại context vào AppRestarter để có thể restart
//		AppRestarter.setContext(ctx);
	}
}
