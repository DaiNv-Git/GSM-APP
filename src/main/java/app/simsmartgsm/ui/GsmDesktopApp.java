package app.simsmartgsm.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

/**
 * Desktop GUI Application cho GSM Manager
 * Hiển thị web interface trong một cửa sổ desktop nhỏ gọn
 */
public class GsmDesktopApp extends Application {

    private static ConfigurableApplicationContext springContext;
    private static final String APP_URL = "http://localhost:8080/gsm-manager.html";
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;

    private WebView webView;

    @Override
    public void init() throws Exception {
        // Khởi động Spring Boot trong background thread
        new Thread(() -> {
            String[] args = new String[0];
            springContext = SpringApplication.run(
                    app.simsmartgsm.SimsmartGsmApplication.class,
                    args);
        }).start();

        // Đợi server khởi động
        waitForServerReady();
    }

    @Override
    public void start(Stage stage) {
        // Tạo WebView để hiển thị web interface
        webView = new WebView();
        webView.getEngine().load(APP_URL);

        // Tạo scene
        Scene scene = new Scene(webView, WINDOW_WIDTH, WINDOW_HEIGHT);

        // Cấu hình stage
        stage.setTitle("GSM Manager - Desktop App");
        stage.setScene(scene);

        // Xử lý sự kiện đóng cửa sổ
        stage.setOnCloseRequest(event -> {
            event.consume(); // Ngăn đóng tự động
            handleCloseRequest();
        });

        // Hiển thị cửa sổ
        stage.show();

        System.out.println("🖥️ Desktop App started successfully!");
    }

    @Override
    public void stop() throws Exception {
        // Tắt Spring Boot khi đóng app
        if (springContext != null) {
            SpringApplication.exit(springContext, () -> 0);
        }
        System.out.println("👋 Application closed");
    }

    /**
     * Hiển thị popup xác nhận khi đóng app
     */
    private void handleCloseRequest() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận đóng ứng dụng");
        alert.setHeaderText("Bạn có chắc muốn đóng GSM Manager?");
        alert.setContentText("Tất cả các kết nối GSM sẽ bị ngắt.");

        // Tùy chỉnh nút
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            // Đóng app
            Platform.exit();
        }
    }

    /**
     * Đợi Spring Boot server khởi động xong
     */
    private void waitForServerReady() {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                java.net.URL url = new java.net.URL("http://localhost:8080/actuator/health");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1000);

                if (connection.getResponseCode() == 200) {
                    System.out.println("✅ Spring Boot server is ready!");
                    return;
                }
            } catch (Exception e) {
                // Server chưa sẵn sàng, thử lại
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            attempt++;
        }

        // Nếu không kết nối được, vẫn tiếp tục (fallback)
        System.out.println("⚠️ Could not verify server status, continuing anyway...");
    }

    /**
     * Launch desktop application
     */
    public static void launchDesktopApp(String[] args) {
        launch(args);
    }
}
