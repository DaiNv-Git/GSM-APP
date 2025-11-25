package app.simsmartgsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Device configuration để phân biệt data giữa các máy
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "device")
public class DeviceConfig {
    private String id = "default-device";
    private String name = "GSM-Node-01";
    private String location = "Unknown";
}
