package app.simsmartgsm.modem;

import app.simsmartgsm.service.ModemRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

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
    private final ConcurrentHashMap<String, SerialPortHandler> activePorts = new ConcurrentHashMap<>();

    /**
     * Thực hiện cuộc gọi (giống C# makeCall)
     */
    public String makeCall(
            String comPort,
            String simPhone,
            String targetNumber,
            boolean enableRecording,
            String orderId) {
        try {
            // Lấy hoặc tạo serial port handler
            SerialPortHandler portHandler = getOrCreatePort(comPort);

            if (!portHandler.isOpen()) {
                if (!portHandler.open()) {
                    throw new RuntimeException("Cannot open port: " + comPort);
                }
            }

            // Nếu enable recording, bắt đầu tracking
            String recordFileName = null;
            if (enableRecording) {
                recordFileName = "call_" + System.currentTimeMillis();
                recordingService.startWavDownload(comPort, recordFileName);
                log.info("🎙️ Recording enabled for call. File: {}", recordFileName);
            }

            // Gửi AT command để gọi điện (giống C# ATD command)
            String dialCommand = "ATD" + targetNumber + ";";
            portHandler.sendCommand(dialCommand);

            log.info("📞 Making call from {} to {}", simPhone, targetNumber);

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

            // Đăng ký callback để xử lý data (giống C# SerialPort_DataReceived)
            handler.onDataReceived((textData, rawBytes) -> {
                handleSerialData(port, textData, rawBytes);
            });

            return handler;
        });
    }

    /**
     * Xử lý data từ serial port (giống C# HandleReceivedLine)
     */
    private void handleSerialData(String comPort, String textData, byte[] rawBytes) {
        log.debug("📥 Data from {}: {}", comPort, textData.trim());

        // Chuyển cho ModemRecordingService xử lý WAV download
        // (Detect RIFF, accumulate bytes, save on +QFDWL:)
        recordingService.handleSerialData(comPort, rawBytes, textData);

        // Log call status
        if (textData.contains("^DSCI:")) {
            log.info("📞 Call status update: {}", textData.trim());
        }

        // Call connected
        if (textData.contains("CONNECT") || textData.contains("OK")) {
            log.info("✅ Call connected on port: {}", comPort);
        }

        // Call ended
        if (textData.contains("NO CARRIER") || textData.contains("BUSY")) {
            log.info("📴 Call ended on port: {}", comPort);
        }
    }

    /**
     * Gửi SMS qua modem (giống C# sendSMS)
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

            // Set SMS text mode (AT+CMGF=1)
            portHandler.sendCommand("AT+CMGF=1");
            Thread.sleep(300);

            // Set recipient number (AT+CMGS="+84...")
            portHandler.sendCommand("AT+CMGS=\"" + targetPhone + "\"");
            Thread.sleep(500);

            // Send message content + Ctrl+Z (0x1A)
            String smsCommand = message + (char) 26; // Ctrl+Z to send
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
            // Send ATH command to hang up
            handler.sendCommand("ATH");
            log.info("📴 Hung up call on port: {}", comPort);
        }
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
            handler.close();
            recordingService.cleanupPort(port);
        });
        activePorts.clear();
        log.info("🧹 Cleaned up all ports");
    }
}
