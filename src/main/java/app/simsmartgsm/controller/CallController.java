package app.simsmartgsm.controller;

import app.simsmartgsm.service.ModemCallService;
import app.simsmartgsm.service.ModemCallService.CallState;
import app.simsmartgsm.service.ModemCallService.RecordingState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

/**
 * REST API cho call và recording
 * API đơn giản: gọi startCall() là tự động xử lý hết
 */
@RestController
@RequestMapping("/api/call")
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final ModemCallService callService;

    /**
     * Bắt đầu cuộc gọi - TỰ ĐỘNG XỬ LÝ TOÀN BỘ
     * POST /api/call/start
     * Gọi đi → Ghi âm → Auto hangup → Download recording
     */
    @PostMapping("/start")
    public ResponseEntity<?> startCall(
            @RequestParam String comPort,
            @RequestParam String phoneNumber,
            @RequestParam(defaultValue = "true") boolean record,
            @RequestParam(defaultValue = "30") int maxDurationSeconds) {

        try {
            boolean success = callService.startCall(comPort, phoneNumber, record, maxDurationSeconds);

            if (success) {
                return ResponseEntity.ok().body(new ApiResponse(
                        true,
                        "Call started successfully - auto recording and hangup enabled",
                        new CallStatusResponse(comPort, CallState.DIALING, phoneNumber)));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(
                        false,
                        "Failed to start call",
                        null));
            }

        } catch (Exception e) {
            log.error("Error starting call", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    /**
     * Trả lời cuộc gọi đến
     * POST /api/call/answer
     */
    @PostMapping("/answer")
    public ResponseEntity<?> answerCall(@RequestParam String comPort) {
        try {
            boolean success = callService.answerCall(comPort);

            if (success) {
                return ResponseEntity.ok().body(new ApiResponse(
                        true,
                        "Call answered successfully",
                        new CallStatusResponse(comPort, CallState.ACTIVE, null)));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(
                        false,
                        "Failed to answer call",
                        null));
            }

        } catch (Exception e) {
            log.error("Error answering call", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    /**
     * Kết thúc cuộc gọi
     * POST /api/call/end
     */
    @PostMapping("/end")
    public ResponseEntity<?> endCall(@RequestParam String comPort) {
        try {
            boolean success = callService.endCall(comPort);

            if (success) {
                return ResponseEntity.ok().body(new ApiResponse(
                        true,
                        "Call ended successfully",
                        null));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(
                        false,
                        "Failed to end call",
                        null));
            }

        } catch (Exception e) {
            log.error("Error ending call", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    /**
     * Bắt đầu ghi âm (thủ công - nếu không dùng auto)
     * POST /api/call/recording/start
     */
    @PostMapping("/recording/start")
    public ResponseEntity<?> startRecording(@RequestParam String comPort) {
        try {
            boolean success = callService.startRecording(comPort);

            if (success) {
                return ResponseEntity.ok().body(new ApiResponse(
                        true,
                        "Recording started successfully",
                        new RecordingStatusResponse(comPort, RecordingState.RECORDING)));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(
                        false,
                        "Failed to start recording",
                        null));
            }

        } catch (Exception e) {
            log.error("Error starting recording", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    /**
     * Dừng ghi âm
     * POST /api/call/recording/stop
     */
    @PostMapping("/recording/stop")
    public ResponseEntity<?> stopRecording(@RequestParam String comPort) {
        try {
            boolean success = callService.stopRecording(comPort);

            if (success) {
                return ResponseEntity.ok().body(new ApiResponse(
                        true,
                        "Recording stopped successfully",
                        new RecordingStatusResponse(comPort, RecordingState.DOWNLOADING)));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(
                        false,
                        "Failed to stop recording",
                        null));
            }

        } catch (Exception e) {
            log.error("Error stopping recording", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    /**
     * Download file ghi âm
     * GET /api/call/recording/download
     */
    @GetMapping("/recording/download")
    public ResponseEntity<Resource> downloadRecording(
            @RequestParam(required = false) String comPort,
            @RequestParam(required = false) String fileName) {
        try {
            String filePath = null;

            // If fileName provided, use it directly
            if (fileName != null && !fileName.isEmpty()) {
                filePath = callService.getRecordingPath(fileName);
            }
            // Otherwise, try to get from active session
            else if (comPort != null && !comPort.isEmpty()) {
                filePath = callService.downloadRecording(comPort);
            }

            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(filePath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading recording", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lấy trạng thái cuộc gọi
     * GET /api/call/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getCallStatus(@RequestParam String comPort) {
        try {
            CallState callState = callService.getCallState(comPort);
            RecordingState recordingState = callService.getRecordingState(comPort);

            return ResponseEntity.ok().body(new ApiResponse(
                    true,
                    "Status retrieved successfully",
                    new FullStatusResponse(comPort, callState, recordingState)));

        } catch (Exception e) {
            log.error("Error getting call status", e);
            return ResponseEntity.internalServerError().body(new ApiResponse(
                    false,
                    "Error: " + e.getMessage(),
                    null));
        }
    }

    // Response classes
    public static class ApiResponse {
        public boolean success;
        public String message;
        public Object data;

        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    public static class CallStatusResponse {
        public String comPort;
        public CallState callState;
        public String phoneNumber;

        public CallStatusResponse(String comPort, CallState callState, String phoneNumber) {
            this.comPort = comPort;
            this.callState = callState;
            this.phoneNumber = phoneNumber;
        }
    }

    public static class RecordingStatusResponse {
        public String comPort;
        public RecordingState recordingState;

        public RecordingStatusResponse(String comPort, RecordingState recordingState) {
            this.comPort = comPort;
            this.recordingState = recordingState;
        }
    }

    public static class FullStatusResponse {
        public String comPort;
        public CallState callState;
        public RecordingState recordingState;

        public FullStatusResponse(String comPort, CallState callState, RecordingState recordingState) {
            this.comPort = comPort;
            this.callState = callState;
            this.recordingState = recordingState;
        }
    }
}
