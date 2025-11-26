package app.simsmartgsm.controller;

import app.simsmartgsm.modem.PortScanService;
import app.simsmartgsm.modem.PortScanService.PortInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST Controller cho modem operations (scan, SMS, call)
 */
@RestController
@RequestMapping("/api/modem-call")
@RequiredArgsConstructor
@Slf4j
public class ModemApiController {

    private final PortScanService portScanService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * SSE endpoint for progressive port scanning
     * GET /api/modem-call/scan-ports-stream
     */
    @GetMapping(value = "/scan-ports-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter scanPortsStream() {
        log.info("🔍 SSE scan-ports-stream endpoint called");
        SseEmitter emitter = new SseEmitter(120000L); // 2 minute timeout

        executor.execute(() -> {
            try {
                log.info("📡 Starting progressive port scan...");

                // Send scan start event
                Map<String, Object> startEvent = new HashMap<>();
                startEvent.put("message", "Starting port scan...");
                sendEvent(emitter, "scan-start", startEvent);
                log.info("✅ Sent scan-start event");

                // Scan ports progressively with callback
                List<PortInfo> allPorts = portScanService.scanAllPortsProgressive(portInfo -> {
                    try {
                        log.info("📱 Found port: {} - {}", portInfo.getComPort(), portInfo.getPhoneNumber());

                        // Send port-found event for ALL ports (not just available ones)
                        if (portInfo != null) {
                            Map<String, Object> portData = new HashMap<>();
                            portData.put("comPort", portInfo.getComPort());
                            portData.put("phoneNumber", portInfo.getPhoneNumber());
                            portData.put("carrier", portInfo.getCarrier());
                            portData.put("imei", portInfo.getImei());
                            portData.put("signalStrength", portInfo.getSignalStrength());
                            portData.put("available", portInfo.isAvailable());
                            portData.put("status", portInfo.getStatus());

                            sendEvent(emitter, "port-found", portData);
                            log.info("✅ Sent port-found event for {}", portInfo.getComPort());
                        }
                    } catch (Exception e) {
                        log.error("❌ Error sending port-found event", e);
                    }
                });

                log.info("📊 Scan complete. Total ports found: {}", allPorts.size());

                // Send scan complete event
                Map<String, Object> completeEvent = new HashMap<>();
                completeEvent.put("totalPorts", allPorts.size());
                completeEvent.put("message", "Scan completed successfully");
                sendEvent(emitter, "scan-complete", completeEvent);
                log.info("✅ Sent scan-complete event");

                emitter.complete();
                log.info("✅ SSE connection completed");

            } catch (Exception e) {
                log.error("❌ Error during port scanning", e);
                try {
                    Map<String, Object> errorEvent = new HashMap<>();
                    errorEvent.put("error", e.getMessage());
                    sendEvent(emitter, "scan-error", errorEvent);
                } catch (Exception ex) {
                    log.error("❌ Error sending error event", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.info("🏁 SSE scan completed"));
        emitter.onTimeout(() -> {
            log.warn("⏰ SSE scan timeout");
            emitter.complete();
        });
        emitter.onError(e -> log.error("❌ SSE scan error", e));

        return emitter;
    }

    /**
     * Helper method to send SSE events
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        String jsonData = objectMapper.writeValueAsString(data);
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(jsonData));
        log.debug("📤 Sent SSE event: {} - {}", eventName, jsonData);
    }
}
