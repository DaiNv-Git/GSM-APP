package app.simsmartgsm.controller;

import app.simsmartgsm.modem.PortScanService;
import app.simsmartgsm.modem.PortScanService.PortInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller cho modem operations (scan, SMS, call)
 */
@RestController
@RequestMapping("/api/modem-call")
@RequiredArgsConstructor
@Slf4j
public class ModemApiController {

    private final PortScanService portScanService;

    /**
     * Regular scan ports endpoint (non-SSE)
     * GET /api/modem-call/scan-ports
     * Sử dụng cho: 1) Nút scan 2) Auto-scan khi start app
     */
    @GetMapping("/scan-ports")
    public Map<String, Object> scanPorts() {
        log.info("🔍 scan-ports endpoint called");

        try {
            List<PortInfo> ports = portScanService.scanAllPorts();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ports", ports);
            response.put("message", "Scan completed");

            log.info("✅ Scan complete. Found {} ports", ports.size());
            return response;

        } catch (Exception e) {
            log.error("❌ Error scanning ports", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("ports", List.of());
            response.put("message", "Error: " + e.getMessage());
            return response;
        }
    }
}
