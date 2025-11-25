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

    private final String portName;
    private SerialPort serialPort;
    private BiConsumer<String, byte[]> dataReceivedCallback;

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

                byte[] buffer = new byte[serialPort.bytesAvailable()];
                int numRead = serialPort.readBytes(buffer, buffer.length);

                if (numRead > 0 && dataReceivedCallback != null) {
                    String textData = new String(buffer, 0, numRead, StandardCharsets.US_ASCII);
                    dataReceivedCallback.accept(textData, buffer);
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
}
