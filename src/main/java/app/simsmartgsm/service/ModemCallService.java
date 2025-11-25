package app.simsmartgsm.service;

import app.simsmartgsm.config.DeviceConfig;
import app.simsmartgsm.entity.CallRecord;
import app.simsmartgsm.modem.SerialPortHandler;
import app.simsmartgsm.repository.CallRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * ModemCallService - Service để quản lý cuộc gọi qua modem
 * Tự động xử lý: gọi đi → ghi âm → auto hangup → download recording
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModemCallService {

    private final ModemRecordingService recordingService;
    private final CallRecordRepository callRecordRepository;
    private final DeviceConfig deviceConfig;
    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, SerialPortHandler> activePorts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    /**
     * Call Session để track trạng thái cuộc gọi
     */
    @Data
    public static class CallSession {
        private String comPort;
        private String simPhone;
        private String targetNumber;
        private String orderId;
        private CallState callState;
        private RecordingState recordingState;
        private Instant startTime;
        private Instant connectTime;
        private Instant endTime;
        private String recordingFileName;
        private int maxDurationSeconds;
        private ScheduledFuture<?> hangupTask;

        public int getDurationSeconds() {
            if (connectTime == null)
                return 0;
            Instant end = endTime != null ? endTime : Instant.now();
            return (int) (end.getEpochSecond() - connectTime.getEpochSecond());
        }
    }

    /**
     * Call State enum
     */
    public enum CallState {
        IDLE, // Không có cuộc gọi
        DIALING, // Đang gọi đi
        RINGING, // Đang đổ chuông
        ACTIVE, // Cuộc gọi đang hoạt động (connected)
        INCOMING, // Có cuộc gọi đến
        ENDED // Đã kết thúc
    }

    /**
     * Recording State enum
     */
    public enum RecordingState {
        IDLE, // Không ghi âm
        RECORDING, // Đang ghi âm
        DOWNLOADING, // Đang download từ modem
        COMPLETED, // Hoàn thành
        FAILED // Lỗi
    }

    /**
     * Bắt đầu cuộc gọi - TỰ ĐỘNG XỬ LÝ TOÀN BỘ FLOW
     * Gọi đi → Ghi âm → Auto hangup → Download recording
     */
    public boolean startCall(String comPort, String phoneNumber) {
        return startCall(comPort, phoneNumber, true, 30); // Mặc định: record = true, 30s
    }

    /**
     * Bắt đầu cuộc gọi với tùy chọn
     */
    public boolean startCall(String comPort, String phoneNumber, boolean enableRecording, int maxDurationSeconds) {
        try {
            // Lấy hoặc tạo serial port handler
            SerialPortHandler portHandler = getOrCreatePort(comPort);

            if (!portHandler.isOpen()) {
                if (!portHandler.open()) {
                    log.error("Cannot open port: {}", comPort);
                    return false;
                }
            }

            // Tạo call session
            CallSession session = new CallSession();
            session.setComPort(comPort);
            session.setTargetNumber(phoneNumber);
            session.setCallState(CallState.DIALING);
            session.setRecordingState(RecordingState.IDLE);
            session.setStartTime(Instant.now());
            session.setMaxDurationSeconds(maxDurationSeconds);

            // Nếu enable recording, bắt đầu tracking
            if (enableRecording) {
                String recordFileName = "call_" + System.currentTimeMillis();
                session.setRecordingFileName(recordFileName);
                session.setRecordingState(RecordingState.RECORDING);
                recordingService.startWavDownload(comPort, recordFileName);
                log.info("🎙️ Recording enabled for call. File: {}", recordFileName);
            }

            activeCalls.put(comPort, session);

            // Gửi AT command để gọi điện
            String dialCommand = "ATD" + phoneNumber + ";";
            portHandler.sendCommand(dialCommand);

            log.info("📞 Making call from {} to {} (recording: {}, max duration: {}s)",
                    comPort, phoneNumber, enableRecording, maxDurationSeconds);

            // Bắt đầu polling call state
            startCallStatePolling(comPort);

            return true;

        } catch (Exception e) {
            log.error("Error starting call from {}", comPort, e);
            return false;
        }
    }

    /**
     * Trả lời cuộc gọi đến
     */
    public boolean answerCall(String comPort) {
        try {
            SerialPortHandler handler = activePorts.get(comPort);
            if (handler == null || !handler.isOpen()) {
                return false;
            }

            handler.sendCommand("ATA");

            CallSession session = activeCalls.get(comPort);
            if (session != null) {
                session.setCallState(CallState.ACTIVE);
                session.setConnectTime(Instant.now());
            }

            log.info("📞 Answered call on port: {}", comPort);
            return true;

        } catch (Exception e) {
            log.error("Error answering call on {}", comPort, e);
            return false;
        }
    }

    /**
     * Kết thúc cuộc gọi
     */
    public boolean endCall(String comPort) {
        try {
            SerialPortHandler handler = activePorts.get(comPort);
            if (handler != null && handler.isOpen()) {
                handler.sendCommand("ATH");
                log.info("📴 Hung up call on port: {}", comPort);
            }

            completeCall(comPort, "MANUAL_HANGUP");
            return true;

        } catch (Exception e) {
            log.error("Error ending call on {}", comPort, e);
            return false;
        }
    }

    /**
     * Bắt đầu ghi âm
     */
    public boolean startRecording(String comPort) {
        try {
            CallSession session = activeCalls.get(comPort);
            if (session == null) {
                return false;
            }

            String recordFileName = "call_" + System.currentTimeMillis();
            session.setRecordingFileName(recordFileName);
            session.setRecordingState(RecordingState.RECORDING);
            recordingService.startWavDownload(comPort, recordFileName);

            log.info("🎙️ Started recording for {}", comPort);
            broadcastCallStatus(session);
            return true;

        } catch (Exception e) {
            log.error("Error starting recording on {}", comPort, e);
            return false;
        }
    }

    /**
     * Dừng ghi âm
     */
    public boolean stopRecording(String comPort) {
        try {
            CallSession session = activeCalls.get(comPort);
            if (session == null) {
                return false;
            }

            session.setRecordingState(RecordingState.DOWNLOADING);

            // Trigger download từ modem
            downloadRecordingFromModem(comPort, session.getRecordingFileName());

            log.info("🎙️ Stopped recording for {}", comPort);
            broadcastCallStatus(session);
            return true;

        } catch (Exception e) {
            log.error("Error stopping recording on {}", comPort, e);
            return false;
        }
    }

    /**
     * Download file ghi âm
     */
    public String downloadRecording(String comPort) {
        CallSession session = activeCalls.get(comPort);
        if (session == null || session.getRecordingFileName() == null) {
            return null;
        }

        return recordingService.getRecordingPath(session.getRecordingFileName());
    }

    /**
     * Get recording file path by fileName
     */
    public String getRecordingPath(String fileName) {
        return recordingService.getRecordingPath(fileName);
    }

    /**
     * Lấy trạng thái cuộc gọi
     */
    public CallState getCallState(String comPort) {
        CallSession session = activeCalls.get(comPort);
        return session != null ? session.getCallState() : CallState.IDLE;
    }

    /**
     * Lấy trạng thái ghi âm
     */
    public RecordingState getRecordingState(String comPort) {
        CallSession session = activeCalls.get(comPort);
        return session != null ? session.getRecordingState() : RecordingState.IDLE;
    }

    /**
     * Lấy call session
     */
    public CallSession getCallStatus(String comPort) {
        return activeCalls.get(comPort);
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Lấy hoặc tạo port handler
     */
    private SerialPortHandler getOrCreatePort(String comPort) {
        return activePorts.computeIfAbsent(comPort, port -> {
            SerialPortHandler handler = new SerialPortHandler(port);

            // Đăng ký callback để xử lý data
            handler.onDataReceived((textData, rawBytes) -> {
                handleSerialData(port, textData, rawBytes);
            });

            return handler;
        });
    }

    /**
     * Xử lý data từ serial port
     */
    private void handleSerialData(String comPort, String textData, byte[] rawBytes) {
        log.debug("📥 Data from {}: {}", comPort, textData.trim());

        // Chuyển cho ModemRecordingService xử lý WAV download
        recordingService.handleSerialData(comPort, rawBytes, textData);

        CallSession session = activeCalls.get(comPort);
        if (session == null)
            return;

        // Parse call states từ modem responses (passive listening - backup)
        if (textData.contains("RING")) {
            updateCallState(session, CallState.INCOMING);
        } else if (textData.contains("NO CARRIER") || textData.contains("BUSY") || textData.contains("NO ANSWER")) {
            updateCallState(session, CallState.ENDED);
            completeCall(comPort, "COMPLETED");
        }
    }

    /**
     * Bắt đầu polling call state bằng AT+CLCC
     */
    private void startCallStatePolling(String comPort) {
        // Cancel existing polling task nếu có
        stopCallStatePolling(comPort);

        // Poll mỗi 500ms
        ScheduledFuture<?> pollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                pollCallState(comPort);
            } catch (Exception e) {
                log.error("Error polling call state for {}", comPort, e);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        pollingTasks.put(comPort, pollingTask);
        log.debug("Started call state polling for {}", comPort);
    }

    /**
     * Dừng polling call state
     */
    private void stopCallStatePolling(String comPort) {
        ScheduledFuture<?> task = pollingTasks.remove(comPort);
        if (task != null) {
            task.cancel(false);
            log.debug("Stopped call state polling for {}", comPort);
        }
    }

    /**
     * Poll call state bằng AT+CLCC
     */
    private void pollCallState(String comPort) {
        SerialPortHandler handler = activePorts.get(comPort);
        CallSession session = activeCalls.get(comPort);

        if (handler == null || session == null || !handler.isOpen()) {
            stopCallStatePolling(comPort);
            return;
        }

        // Query call status
        String response = handler.sendCommandAndWaitResponse("AT+CLCC", 1000);

        if (response.isEmpty()) {
            return;
        }

        log.debug("AT+CLCC response for {}: {}", comPort, response.trim());

        // Parse response
        CallState newState = parseClccResponse(response);

        if (newState == null) {
            // Không có cuộc gọi active -> call đã kết thúc
            if (session.getCallState() != CallState.ENDED) {
                updateCallState(session, CallState.ENDED);
                completeCall(comPort, "COMPLETED");
            }
        } else {
            updateCallState(session, newState);

            // Nếu connected lần đầu, schedule auto hangup
            if (newState == CallState.ACTIVE && session.getConnectTime() == null) {
                session.setConnectTime(Instant.now());
                scheduleAutoHangup(session);
            }
        }
    }

    /**
     * Parse AT+CLCC response để lấy call state
     */
    private CallState parseClccResponse(String response) {
        if (!response.contains("+CLCC:")) {
            return null;
        }

        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("+CLCC:")) {
                    String[] parts = line.substring(line.indexOf("+CLCC:") + 6).trim().split(",");
                    if (parts.length >= 3) {
                        int stat = Integer.parseInt(parts[2].trim());

                        // Map stat to CallState
                        return switch (stat) {
                            case 0 -> CallState.ACTIVE; // active
                            case 2 -> CallState.DIALING; // dialing (MO call)
                            case 3 -> CallState.RINGING; // alerting (remote party ringing)
                            case 4 -> CallState.INCOMING; // incoming call
                            default -> CallState.DIALING;
                        };
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing CLCC response: {}", response, e);
        }

        return null;
    }

    /**
     * Update call state và log + broadcast to FE
     */
    private void updateCallState(CallSession session, CallState newState) {
        if (session.getCallState() == newState)
            return;

        CallState oldState = session.getCallState();
        session.setCallState(newState);

        log.info("📞 Call state: {} → {} on port {}", oldState, newState, session.getComPort());

        // Broadcast status to FE via WebSocket
        broadcastCallStatus(session);
    }

    /**
     * Broadcast call status to frontend via WebSocket
     */
    private void broadcastCallStatus(CallSession session) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("comPort", session.getComPort());
            status.put("callState", session.getCallState());
            status.put("recordingState", session.getRecordingState());
            status.put("targetNumber", session.getTargetNumber());
            status.put("durationSeconds", session.getDurationSeconds());
            status.put("startTime", session.getStartTime());
            status.put("connectTime", session.getConnectTime());
            status.put("recordingFileName", session.getRecordingFileName());

            // Send to WebSocket topic
            messagingTemplate.convertAndSend("/topic/call-status", status);

            log.debug("📡 Broadcasted call status: {} - {}", session.getComPort(), session.getCallState());
        } catch (Exception e) {
            log.error("Error broadcasting call status", e);
        }
    }

    /**
     * Schedule auto hang-up
     */
    private void scheduleAutoHangup(CallSession session) {
        if (session.getMaxDurationSeconds() <= 0)
            return;

        // Cancel existing task nếu có
        if (session.getHangupTask() != null) {
            session.getHangupTask().cancel(false);
        }

        // Schedule new task
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            log.info("⏰ Auto hang-up triggered for port {} after {}s",
                    session.getComPort(), session.getMaxDurationSeconds());
            endCall(session.getComPort());
        }, session.getMaxDurationSeconds(), TimeUnit.SECONDS);

        session.setHangupTask(task);
    }

    /**
     * Hoàn thành cuộc gọi và save to database
     */
    private void completeCall(String comPort, String endReason) {
        CallSession session = activeCalls.remove(comPort);
        if (session == null)
            return;

        session.setEndTime(Instant.now());
        session.setCallState(CallState.ENDED);

        // Cancel hang-up task nếu có
        if (session.getHangupTask() != null) {
            session.getHangupTask().cancel(false);
        }

        // Stop polling
        stopCallStatePolling(comPort);

        // Nếu có recording, tự động download từ modem
        if (session.getRecordingFileName() != null && session.getRecordingState() == RecordingState.RECORDING) {
            session.setRecordingState(RecordingState.DOWNLOADING);
            broadcastCallStatus(session);
            downloadRecordingFromModem(comPort, session.getRecordingFileName());
        }

        // Save to database
        saveCallRecord(session, endReason);
    }

    /**
     * Save call record to database
     */
    private void saveCallRecord(CallSession session, String endReason) {
        try {
            CallRecord record = new CallRecord();
            record.setDeviceId(deviceConfig.getId());
            record.setDeviceName(deviceConfig.getName());
            record.setDeviceLocation(deviceConfig.getLocation());
            record.setComPort(session.getComPort());
            record.setSimPhone(session.getSimPhone());
            record.setTargetNumber(session.getTargetNumber());
            record.setOrderId(session.getOrderId());
            record.setCallState(endReason);
            record.setStartTime(session.getStartTime());
            record.setConnectTime(session.getConnectTime());
            record.setEndTime(session.getEndTime());
            record.setDurationSeconds(session.getDurationSeconds());
            record.setRecordingFileName(session.getRecordingFileName());

            if (session.getRecordingFileName() != null) {
                record.setRecordingFilePath(
                        recordingService.getRecordingPath(session.getRecordingFileName()));
            }

            callRecordRepository.save(record);
            log.info("💾 Saved call record: {} → {} ({}s)",
                    session.getSimPhone(), session.getTargetNumber(), session.getDurationSeconds());
        } catch (Exception e) {
            log.error("Error saving call record", e);
        }
    }

    /**
     * Tự động download recording từ modem storage
     */
    private void downloadRecordingFromModem(String comPort, String fileName) {
        try {
            SerialPortHandler handler = activePorts.get(comPort);
            if (handler == null || !handler.isOpen()) {
                log.warn("Cannot download recording: port {} not available", comPort);
                return;
            }

            log.info("🎙️ Downloading recording from modem: {}", fileName);

            // List files trong modem storage
            String listResponse = handler.sendCommandAndWaitResponse("AT+QFLST=\"*\"", 2000);
            log.debug("Modem file list: {}", listResponse);

            // Tìm file recording mới nhất
            String recordingFileOnModem = findLatestRecordingFile(listResponse);

            if (recordingFileOnModem == null) {
                log.warn("No recording file found on modem for {}", fileName);

                CallSession session = activeCalls.get(comPort);
                if (session != null) {
                    session.setRecordingState(RecordingState.COMPLETED);
                }
                return;
            }

            log.info("Found recording file on modem: {}", recordingFileOnModem);

            // Download file từ modem
            String downloadCommand = "AT+QFDWL=\"" + recordingFileOnModem + "\"";
            handler.sendCommand(downloadCommand);

            log.info("✅ Recording download initiated for {}", fileName);

        } catch (Exception e) {
            log.error("Error downloading recording from modem", e);

            CallSession session = activeCalls.get(comPort);
            if (session != null) {
                session.setRecordingState(RecordingState.FAILED);
            }
        }
    }

    /**
     * Tìm file recording mới nhất từ danh sách files trên modem
     */
    private String findLatestRecordingFile(String fileListResponse) {
        String[] lines = fileListResponse.split("\n");
        String latestFile = null;

        for (String line : lines) {
            if (line.contains("+QFLST:")) {
                int startQuote = line.indexOf("\"");
                int endQuote = line.indexOf("\"", startQuote + 1);

                if (startQuote != -1 && endQuote != -1) {
                    String filename = line.substring(startQuote + 1, endQuote);

                    if (filename.toLowerCase().endsWith(".amr") ||
                            filename.toLowerCase().endsWith(".wav") ||
                            filename.toLowerCase().startsWith("record")) {
                        latestFile = filename;
                    }
                }
            }
        }

        return latestFile;
    }

    /**
     * Cleanup port
     */
    public void cleanup(String comPort) {
        completeCall(comPort, "CLEANUP");
        stopCallStatePolling(comPort);

        SerialPortHandler handler = activePorts.remove(comPort);
        if (handler != null) {
            handler.close();
            recordingService.cleanupPort(comPort);
            log.info("🧹 Cleaned up port: {}", comPort);
        }
    }

    /**
     * Cleanup all ports
     */
    public void cleanupAll() {
        activePorts.forEach((port, handler) -> {
            completeCall(port, "CLEANUP");
            handler.close();
            recordingService.cleanupPort(port);
        });
        activePorts.clear();
        scheduler.shutdownNow();
        log.info("🧹 Cleaned up all ports");
    }
}
