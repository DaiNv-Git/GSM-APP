package app.simsmartgsm.config;

import app.simsmartgsm.modem.PortScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Service tự động scan SIM khi ứng dụng khởi động
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StartupScanService {

    private final PortScanService portScanService;

    /**
     * Tự động scan ports khi ứng dụng đã sẵn sàng
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Application started - Auto-scanning SIM cards...");

        // Chạy scan trong background thread để không block startup
        new Thread(() -> {
            try {
                // Đợi 2 giây để đảm bảo tất cả services đã khởi động
                Thread.sleep(2000);

                log.info("🔍 Starting auto-scan for SIM cards...");
                List<PortScanService.PortInfo> ports = portScanService.scanAllPorts();

                log.info("✅ Auto-scan completed! Found {} SIM card(s)", ports.size());

                // Log thông tin các SIM tìm thấy
                if (!ports.isEmpty()) {
                    ports.forEach(port -> {
                        log.info("📱 SIM found: {} - Phone: {}, Carrier: {}, Signal: {}",
                                port.getComPort(),
                                port.getPhoneNumber(),
                                port.getCarrier(),
                                port.getSignalStrength());
                    });
                } else {
                    log.warn("⚠️ No SIM cards detected on startup");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("❌ Auto-scan interrupted", e);
            } catch (Exception e) {
                log.error("❌ Error during auto-scan", e);
            }
        }, "startup-scan-thread").start();
    }
}
