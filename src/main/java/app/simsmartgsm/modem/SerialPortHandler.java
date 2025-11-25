package app.simsmartgsm.modem;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * SerialPortHandler - Xử lý serial port TRỰC TIẾP
 * Giống C# System.IO.Ports.SerialPort
 * HOÀN TOÀN ĐỘC LẬP, không dùng code cũ
 */
@Slf4j
public class SerialPortHandler {

    // Buffer pool để reuse buffers, giảm GC pressure
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    private static final ThreadLocal<byte[]> BUFFER_POOL = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    private final String portName;
    private SerialPort serialPort;
    private BiConsumer<String, byte[]> dataReceivedCallback;

    // Line buffer cho protocol parsing (AT commands)
    private final StringBuilder lineBuffer = new StringBuilder(512);

    public SerialPortHandler(String portName) {
        this.portName = portName;
    }

    /**
     * Mở serial port (giống C# sp.Open())
     */
    public boolean open() {
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setComPortParameters(115200, 8, 1, 0); // Baud rate, data bits, stop bits, parity
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

            if (serialPort.openPort()) {
                log.info("✅ Opened serial port: {}", portName);

                // Enable hardware flow control (RTS/DTR cho GSM modems)
                serialPort.setRTS();
                serialPort.setDTR();

                // Setup data received listener (giống C# SerialPort_DataReceived)
                setupDataListener();
                return true;
            } else {
                log.error("❌ Failed to open port: {}", portName);
                return false;
            }
        } catch (Exception e) {
            log.error("Error opening port: {}", portName, e);
            return false;
        }
    }

    /**
     * Setup listener để nhận data (giống C# SerialPort_DataReceived event)
     */
    private void setupDataListener() {
        serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;

                try {
                    int available = serialPort.bytesAvailable();
                    if (available <= 0)
                        return;

                    // Reuse pooled buffer thay vì tạo mới mỗi lần
                    byte[] buffer = BUFFER_POOL.get();
                    int toRead = Math.min(available, BUFFER_SIZE);
                    int numRead = serialPort.readBytes(buffer, toRead);

                    if (numRead > 0 && dataReceivedCallback != null) {
                        // Copy bytes for callback (QUAN TRỌNG: Giữ nguyên raw bytes cho binary data)
                        byte[] callbackData = new byte[numRead];
                        System.arraycopy(buffer, 0, callbackData, 0, numRead);

                        // Convert to string (for text-based protocols like AT commands)
                        // NOTE: Binary data (WAV) sẽ có garbage characters, nhưng callback
                        // vẫn nhận được RAW bytes chính xác qua callbackData
                        String textData = new String(buffer, 0, numRead, StandardCharsets.US_ASCII);

                        dataReceivedCallback.accept(textData, callbackData);
                    }
                } catch (Exception e) {
                    log.error("Error in data listener for {}: {}", portName, e.getMessage());
                }
            }
        });
    }

    /**
     * Đăng ký callback khi nhận data (giống C# event handler)
     */
    public void onDataReceived(BiConsumer<String, byte[]> callback) {
        this.dataReceivedCallback = callback;
    }

    /**
     * Đăng ký callback với line-based processing (tự động parse theo \r\n)
     * Phù hợp cho AT command protocol
     * OPTIONAL: Dùng thay cho onDataReceived() nếu muốn xử lý theo dòng
     */
    public void onLineReceived(java.util.function.Consumer<String> lineCallback) {
        this.dataReceivedCallback = (text, bytes) -> {
            synchronized (lineBuffer) {
                lineBuffer.append(text);

                String data = lineBuffer.toString();
                int lineEnd;

                while ((lineEnd = data.indexOf("\r\n")) != -1) {
                    String line = data.substring(0, lineEnd);
                    data = data.substring(lineEnd + 2);

                    if (!line.isEmpty()) {
                        lineCallback.accept(line);
                    }
                }

                // Keep remaining data in buffer
                lineBuffer.setLength(0);
                lineBuffer.append(data);
            }
        };
    }

    /**
     * Gửi AT command (giống C# sp.Write())
     */
    public boolean sendCommand(String command) {
        try {
            String fullCommand = command + "\r";
            byte[] bytes = fullCommand.getBytes(StandardCharsets.US_ASCII);
            int written = serialPort.writeBytes(bytes, bytes.length);
            log.debug("Sent command to {}: {}", portName, command);
            return written > 0;
        } catch (Exception e) {
            log.error("Error sending command to {}: {}", portName, command, e);
            return false;
        }
    }

    /**
     * Đóng port (giống C# sp.Close())
     */
    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            log.info("Closed serial port: {}", portName);
        }
    }

    /**
     * Check port đang mở không
     */
    public boolean isOpen() {
        return serialPort != null && serialPort.isOpen();
    }

    public String getPortName() {
        return portName;
    }

    /**
     * Gửi AT command và đợi response (blocking)
     * Dùng để query thông tin như AT+CLCC
     */
    public String sendCommandAndWaitResponse(String command, int timeoutMs) {
        try {
            // Clear buffer trước (reuse pooled buffer)
            byte[] clearBuffer = BUFFER_POOL.get();
            while (serialPort.bytesAvailable() > 0) {
                int toRead = Math.min(serialPort.bytesAvailable(), BUFFER_SIZE);
                serialPort.readBytes(clearBuffer, toRead);
            }

            // Gửi command (KHÔNG THAY ĐỔI)
            String fullCommand = command + "\r";
            byte[] bytes = fullCommand.getBytes(StandardCharsets.US_ASCII);
            serialPort.writeBytes(bytes, bytes.length);
            log.debug("Sent query command to {}: {}", portName, command);

            // Đợi response với pooled buffer
            StringBuilder response = new StringBuilder(256);
            long startTime = System.currentTimeMillis();
            byte[] buffer = BUFFER_POOL.get();

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                int available = serialPort.bytesAvailable();
                if (available > 0) {
                    int toRead = Math.min(available, BUFFER_SIZE);
                    int numRead = serialPort.readBytes(buffer, toRead);

                    if (numRead > 0) {
                        String data = new String(buffer, 0, numRead, StandardCharsets.US_ASCII);
                        response.append(data);

                        // Nếu nhận được OK hoặc ERROR thì kết thúc
                        String responseStr = response.toString();
                        if (responseStr.contains("OK") || responseStr.contains("ERROR")) {
                            break;
                        }
                    }
                }

                // Smarter wait - park instead of sleep (giảm CPU usage)
                java.util.concurrent.locks.LockSupport.parkNanos(5_000_000); // 5ms
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error sending query command to {}: {}", portName, command, e);
            return "";
        }
    }
}
