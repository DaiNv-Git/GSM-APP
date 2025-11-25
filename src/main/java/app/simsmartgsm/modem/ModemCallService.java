package app.simsmartgsm.modem;

import app.simsmartgsm.config.DeviceConfig;
import app.simsmartgsm.entity.CallRecord;
import app.simsmartgsm.repository.CallRecordRepository;
import app.simsmartgsm.service.ModemRecordingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.*;

/**
 * ModemCallService - Service HOÀN TOÀN MỚI để gọi điện qua modem
 * Giống C# Main.cs logic
 * KHÔNG dùng ComManager, CallService hay bất kỳ service cũ nào
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModemCallService {

    private final ModemRecordingService recordingService;
    private final CallRecordRepository callRecordRepository;
    private final DeviceConfig deviceConfig;
    private final ConcurrentHashMap<String, SerialPortHandler> activePorts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    /**
     * Call Session để track trạng thái cuộc gọi
     */
    @Data
    public static class CallSession {
        private String comPort;
        private String simPhone;
        private String targetNumber;
        private String orderId;
        private CallState state;
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

    public enum CallState {
        DIALING, // Đang gọi
        RINGING, // Đang đổ chuông
        CONNECTED, // Đã nhấc máy
        ENDED // Đã kết thúc
    }

    /**
     * Thực hiện cuộc gọi với auto hang-up timer
     */
    public String makeCall(
            String comPort,
            String simPhone,
            String targetNumber,
            boolean enableRecording,
            String orderId,
            int maxDurationSeconds) {
        try {
            // Lấy hoặc tạo serial port handler
            SerialPortHandler portHandler = getOrCreatePort(comPort);

            if (!portHandler.isOpen()) {
                if (!portHandler.open()) {
                    throw new RuntimeException("Cannot open port: " + comPort);
                }
            }

            // Tạo call session
            CallSession session = new CallSession();
            session.setComPort(comPort);
            session.setSimPhone(simPhone);
            session.setTargetNumber(targetNumber);
            session.setOrderId(orderId);
            session.setState(CallState.DIALING);
            session.setStartTime(Instant.now());
            session.setMaxDurationSeconds(maxDurationSeconds);

            // Nếu enable recording, bắt đầu tracking
            String recordFileName = null;
            if (enableRecording) {
                recordFileName = "call_" + System.currentTimeMillis();
                session.setRecordingFileName(recordFileName);
                recordingService.startWavDownload(comPort, recordFileName);
                log.info("🎙️ Recording enabled for call. File: {}", recordFileName);
            }

            activeCalls.put(comPort, session);

            // Gửi AT command để gọi điện
            String dialCommand = "ATD" + targetNumber + ";";
            portHandler.sendCommand(dialCommand);

            log.info("📞 Making call from {} to {} (max duration: {}s)",
                    simPhone, targetNumber, maxDurationSeconds);

            return recordFileName;

        } catch (Exception e) {
            log.error("Error making call from {}", comPort, e);
            throw new RuntimeException("Failed to make call: " + e.getMessage());
        }
    }

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
     * Xử lý data từ serial port và update call state
     */
    private void handleSerialData(String comPort, String textData, byte[] rawBytes) {
        log.debug("📥 Data from {}: {}", comPort, textData.trim());

        // Chuyển cho ModemRecordingService xử lý WAV download
        recordingService.handleSerialData(comPort, rawBytes, textData);

        CallSession session = activeCalls.get(comPort);
        if (session == null)
            return;

        // Parse call states từ modem responses
        if (textData.contains("^ORIG") || textData.contains("DIALING")) {
            updateCallState(session, CallState.DIALING);
        } else if (textData.contains("^CONF") || textData.contains("ALERTING")) {
            updateCallState(session, CallState.RINGING);
        } else if (textData.contains("^CONN") || textData.contains("CONNECT")) {
            updateCallState(session, CallState.CONNECTED);
            scheduleAutoHangup(session);
        } else if (textData.contains("NO CARRIER") || textData.contains("BUSY") || textData.contains("NO ANSWER")) {
            updateCallState(session, CallState.ENDED);
            endCall(comPort, "COMPLETED");
        }
    }

    /**
     * Update call state và log
     */
    private void updateCallState(CallSession session, CallState newState) {
        if (session.getState() == newState)
            return;

        CallState oldState = session.getState();
        session.setState(newState);

        if (newState == CallState.CONNECTED && session.getConnectTime() == null) {
            session.setConnectTime(Instant.now());
            log.info("✅ Call connected on port: {} (answered after {}s)",
                    session.getComPort(),
                    (session.getConnectTime().getEpochSecond() - session.getStartTime().getEpochSecond()));
        }

        log.info("📞 Call state: {} → {} on port {}", oldState, newState, session.getComPort());
    }

    /**
     * Schedule auto hang-up nếu có max duration
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
            hangup(session.getComPort());
            endCall(session.getComPort(), "AUTO_HANGUP");
        }, session.getMaxDurationSeconds(), TimeUnit.SECONDS);

        session.setHangupTask(task);
    }

    /**
     * End call và save to database
     */
    private void endCall(String comPort, String endReason) {
        CallSession session = activeCalls.remove(comPort);
        if (session == null)
            return;

        session.setEndTime(Instant.now());
        session.setState(CallState.ENDED);

        // Cancel hang-up task nếu có
        if (session.getHangupTask() != null) {
            session.getHangupTask().cancel(false);
        }

        // Save to database
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
     * Get call status
     */
    public CallSession getCallStatus(String comPort) {
        return activeCalls.get(comPort);
    }

    /**
     * Gửi SMS qua modem
     */
    public void sendSms(
            String comPort,
            String targetPhone,
            String message,
            String orderId) {
        try {
            SerialPortHandler portHandler = getOrCreatePort(comPort);

            if (!portHandler.isOpen()) {
                if (!portHandler.open()) {
                    throw new RuntimeException("Cannot open port: " + comPort);
                }
            }

            // Set SMS text mode
            portHandler.sendCommand("AT+CMGF=1");
            Thread.sleep(300);

            // Set recipient number
            portHandler.sendCommand("AT+CMGS=\"" + targetPhone + "\"");
            Thread.sleep(500);

            // Send message content + Ctrl+Z
            String smsCommand = message + (char) 26;
            portHandler.sendCommand(smsCommand);

            log.info("📨 SMS sent from {} to {}: {}", comPort, targetPhone, message);

        } catch (Exception e) {
            log.error("Error sending SMS from {}", comPort, e);
            throw new RuntimeException("Failed to send SMS: " + e.getMessage());
        }
    }

    /**
     * Kết thúc cuộc gọi
     */
    public void hangup(String comPort) {
        SerialPortHandler handler = activePorts.get(comPort);
        if (handler != null && handler.isOpen()) {
            handler.sendCommand("ATH");
            log.info("📴 Hung up call on port: {}", comPort);
        }
        endCall(comPort, "MANUAL_HANGUP");
    }

    /**
     * Check port status
     */
    public boolean isPortActive(String comPort) {
        SerialPortHandler handler = activePorts.get(comPort);
        return handler != null && handler.isOpen();
    }

    /**
     * Cleanup port
     */
    public void cleanup(String comPort) {
        // End any active call
        endCall(comPort, "CLEANUP");

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
            endCall(port, "CLEANUP");
            handler.close();
            recordingService.cleanupPort(port);
        });
        activePorts.clear();
        scheduler.shutdownNow();
        log.info("🧹 Cleaned up all ports");
    }
}
