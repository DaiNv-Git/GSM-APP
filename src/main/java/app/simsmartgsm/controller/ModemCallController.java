package app.simsmartgsm.controller;

import app.simsmartgsm.entity.CallRecord;
import app.simsmartgsm.repository.CallRecordRepository;
import app.simsmartgsm.modem.ModemCallService;
import app.simsmartgsm.modem.PortScanService;
import app.simsmartgsm.service.ModemRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller MỚI để xử lý cuộc gọi và ghi âm từ MODEM
 * Tương tự logic C# App (SimmartApp 2)
 */
@RestController
@RequestMapping("/api/modem-call")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Modem Call API", description = "API mới để gọi điện và ghi âm từ MODEM (giống C# logic)")
public class ModemCallController {

    private final ModemRecordingService modemRecordingService;
    private final CallRecordRepository callRecordRepository;
    private final ModemCallService modemCallService;
    private final PortScanService portScanService;

    /**
     * Thực hiện cuộc gọi với ghi âm từ MODEM
     * INPUT ĐƠN GIẢN: chỉ comPort, targetPhone, record
     */
    @PostMapping("/make-call")
    @Operation(summary = "Thực hiện cuộc gọi từ MODEM", description = "Gọi điện qua modem. File ghi âm lưu ở ổ C local.")
    public ResponseEntity<?> makeModemCall(
            @RequestParam String comPort,
            @RequestParam String targetPhone,
            @RequestParam(defaultValue = "false") boolean record) {
        try {
            // Generate unique order ID
            String orderId = UUID.randomUUID().toString();

            // Gọi điện qua ModemCallService (HOÀN TOÀN MỚI, không dùng ComManager)
            String recordFileName = modemCallService.makeCall(
                    comPort,
                    null, // simPhone - không cần
                    targetPhone,
                    record,
                    orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "📞 Cuộc gọi đã được khởi tạo");
            response.put("orderId", orderId);
            response.put("comPort", comPort);
            response.put("targetPhone", targetPhone);
            response.put("recording", record);
            response.put("recordFileName", recordFileName);

            log.info("📞 Call initiated: comPort={}, target={}, record={}",
                    comPort, targetPhone, record);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error making call", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()));
        }
    }

    /**
     * Scan tất cả COM ports và lấy thông tin SIM
     */
    @GetMapping("/scan-ports")
    @Operation(summary = "Scan COM Ports", description = "Scan tất cả COM ports và lấy thông tin SIM (phone number, carrier, IMEI, signal)")
    public ResponseEntity<?> scanPorts() {
        try {
            log.info("🔍 Scanning COM ports...");

            List<PortScanService.PortInfo> ports = portScanService.scanAllPorts();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Scan hoàn tất",
                    "totalPorts", ports.size(),
                    "ports", ports));

        } catch (Exception e) {
            log.error("Error scanning ports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()));
        }
    }

    /**
     * Gửi SMS qua modem
     */
    @PostMapping("/send-sms")
    @Operation(summary = "Gửi SMS qua MODEM", description = "Gửi tin nhắn SMS qua modem")
    public ResponseEntity<?> sendSms(
            @RequestParam String comPort,
            @RequestParam String targetPhone,
            @RequestParam String message) {
        try {
            String orderId = UUID.randomUUID().toString();

            modemCallService.sendSms(comPort, targetPhone, message, orderId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "📨 Tin nhắn đã được gửi",
                    "comPort", comPort,
                    "targetPhone", targetPhone,
                    "orderId", orderId));

        } catch (Exception e) {
            log.error("Error sending SMS", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()));
        }
    }

    /**
     * Shutdown application (for desktop app mode)
     */
    @PostMapping("/shutdown")
    @Operation(summary = "Shutdown Application", description = "Tắt ứng dụng (dùng cho desktop app mode)")
    public ResponseEntity<?> shutdownApplication() {
        log.info("🛑 Application shutdown requested from UI");

        // Shutdown sau 1 giây để response kịp trả về
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Application shutting down..."));
    }

    /**
     * Webhook để nhận data từ serial port
     * Tương tự C# SerialPort_DataReceived
     */
    @PostMapping("/serial-data")
    @Operation(summary = "Nhận data từ serial port", description = "Webhook để nhận và xử lý WAV data từ modem. Internal use only.")
    public ResponseEntity<?> handleSerialData(
            @RequestParam String comPort,
            @RequestBody byte[] data,
            @RequestParam(required = false) String textData) {
        try {
            // Xử lý data (detect RIFF, accumulate bytes, save when complete)
            modemRecordingService.handleSerialData(comPort, data, textData != null ? textData : "");

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error handling serial data for port: {}", comPort, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Lấy lịch sử cuộc gọi từ MODEM
     */
    @GetMapping("/call-history")
    @Operation(summary = "Lịch sử cuộc gọi MODEM", description = "Xem lịch sử các cuộc gọi được thực hiện qua MODEM với ghi âm")
    public ResponseEntity<?> getModemCallHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String simPhone,
            @RequestParam(required = false) String comPort) {
        try {
            Pageable pageable = PageRequest.of(
                    page - 1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt"));

            Page<CallRecord> result;

            // Filter theo simPhone và comPort
            if (simPhone != null && !simPhone.isEmpty() && comPort != null && !comPort.isEmpty()) {
                result = callRecordRepository.findBySimPhoneContainingIgnoreCaseAndComPort(
                        simPhone, comPort, pageable);
            } else if (simPhone != null && !simPhone.isEmpty()) {
                result = callRecordRepository.findBySimPhoneContainingIgnoreCase(simPhone, pageable);
            } else if (comPort != null && !comPort.isEmpty()) {
                result = callRecordRepository.findByComPort(comPort, pageable);
            } else {
                // Chỉ lấy calls từ MODEM (serviceCode = MODEM_CALL)
                result = callRecordRepository.findByServiceCode("MODEM_CALL", pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("page", page);
            response.put("size", size);
            response.put("totalPages", result.getTotalPages());
            response.put("totalElements", result.getTotalElements());
            response.put("calls", result.getContent());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching modem call history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Download file ghi âm
     */
    @GetMapping("/recording/{fileName}")
    @Operation(summary = "Download file ghi âm", description = "Download WAV file đã ghi âm từ modem")
    public ResponseEntity<?> downloadRecording(@PathVariable String fileName) {
        try {
            String recordingPath = modemRecordingService.getRecordingPath(fileName);
            Path filePath = Paths.get(recordingPath);

            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "File not found: " + fileName));
            }

            byte[] fileData = Files.readAllBytes(filePath);

            return ResponseEntity.ok()
                    .header("Content-Type", "audio/wav")
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + ".wav\"")
                    .body(fileData);

        } catch (Exception e) {
            log.error("Error downloading recording: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy thông tin recording folder
     */
    @GetMapping("/recording-config")
    @Operation(summary = "Cấu hình recording", description = "Lấy thông tin về folder lưu recordings")
    public ResponseEntity<?> getRecordingConfig() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "recordingSavePath", modemRecordingService.getRecordingSavePath()));
    }

    /**
     * Cập nhật recording folder
     */
    @PostMapping("/recording-config")
    @Operation(summary = "Cập nhật cấu hình recording", description = "Thay đổi folder lưu recordings")
    public ResponseEntity<?> updateRecordingConfig(@RequestParam String savePath) {
        try {
            modemRecordingService.setRecordingSavePath(savePath);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Recording save path updated",
                    "newPath", savePath));
        } catch (Exception e) {
            log.error("Error updating recording config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Kiểm tra trạng thái ghi âm của một port
     */
    @GetMapping("/recording-status/{comPort}")
    @Operation(summary = "Trạng thái ghi âm", description = "Kiểm tra xem port có đang ghi âm không")
    public ResponseEntity<?> getRecordingStatus(@PathVariable String comPort) {
        boolean isRecording = modemRecordingService.isDownloading(comPort);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "comPort", comPort,
                "isRecording", isRecording));
    }

    /**
     * Cleanup resources cho một port
     */
    @DeleteMapping("/cleanup/{comPort}")
    @Operation(summary = "Cleanup port resources", description = "Dọn dẹp resources cho một COM port")
    public ResponseEntity<?> cleanupPort(@PathVariable String comPort) {
        try {
            modemRecordingService.cleanupPort(comPort);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Port resources cleaned up",
                    "comPort", comPort));
        } catch (Exception e) {
            log.error("Error cleaning up port: {}", comPort, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
