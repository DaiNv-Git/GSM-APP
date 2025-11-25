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
		// Kiểm tra nếu có argument "--headless" thì chạy không có GUI
		boolean headless = args.length > 0 && "--headless".equals(args[0]);

		if (headless) {
			// Chạy Spring Boot bình thường (server mode)
			ConfigurableApplicationContext ctx = SpringApplication.run(SimsmartGsmApplication.class, args);
			System.out.println("🚀 Running in headless mode (server only)");
		} else {
			// Khởi động Desktop GUI Application
			System.out.println("🖥️ Launching Desktop Application...");
			app.simsmartgsm.ui.GsmDesktopApp.launchDesktopApp(args);
		}
	}
}
