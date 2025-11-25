package app.simsmartgsm.modem;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Service để scan ports và lấy thông tin SIM
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortScanService {

    /**
     * Scan tất cả COM ports và lấy thông tin SIM
     */
    public List<PortInfo> scanAllPorts() {
        log.info("🔍 Bắt đầu scan COM ports...");

        SerialPort[] ports = SerialPort.getCommPorts();
        List<PortInfo> portInfoList = new CopyOnWriteArrayList<>();

        if (ports.length == 0) {
            log.warn("Không tìm thấy COM port nào");
            return portInfoList;
        }

        // Sử dụng ThreadPool để scan nhiều port cùng lúc
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(ports.length, 10));
        List<Future<PortInfo>> futures = new ArrayList<>();

        for (SerialPort port : ports) {
            Future<PortInfo> future = executor.submit(() -> {
                try {
                    return scanSinglePort(port.getSystemPortName());
                } catch (Exception e) {
                    log.error("Error scanning port: {}", port.getSystemPortName(), e);
                    return null;
                }
            });
            futures.add(future);
        }

        // Collect results
        for (Future<PortInfo> future : futures) {
            try {
                PortInfo info = future.get(10, TimeUnit.SECONDS);
                if (info != null && info.isAvailable()) {
                    portInfoList.add(info);
                }
            } catch (TimeoutException e) {
                log.warn("Timeout scanning port");
            } catch (Exception e) {
                log.error("Error getting scan result", e);
            }
        }

        executor.shutdown();

        log.info("✅ Scan hoàn tất. Tìm thấy {} port khả dụng", portInfoList.size());
        return portInfoList;
    }

    /**
     * Scan một port và lấy thông tin SIM
     */
    private PortInfo scanSinglePort(String portName) {
        PortInfo info = new PortInfo();
        info.setComPort(portName);

        SerialPort serialPort = null;
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setComPortParameters(115200, 8, 1, 0);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 2000, 0);

            if (!serialPort.openPort()) {
                info.setAvailable(false);
                info.setStatus("Không thể mở port");
                return info;
            }

            Thread.sleep(500); // Đợi port stable

            info.setAvailable(true);
            info.setStatus("Active");

            // Lấy số điện thoại SIM
            String phoneNumber = getPhoneNumber(serialPort);
            info.setPhoneNumber(phoneNumber != null ? phoneNumber : "N/A");

            // Lấy nhà mạng
            String carrier = getCarrier(serialPort);
            info.setCarrier(carrier != null ? carrier : "N/A");

            // Lấy IMEI
            String imei = getIMEI(serialPort);
            info.setImei(imei != null ? imei : "N/A");

            // Lấy signal strength
            String signal = getSignalStrength(serialPort);
            info.setSignalStrength(signal != null ? signal : "N/A");

            log.info("📱 Port {}: Phone={}, Carrier={}", portName, phoneNumber, carrier);

        } catch (Exception e) {
            log.error("Error scanning port {}", portName, e);
            info.setAvailable(false);
            info.setStatus("Error: " + e.getMessage());
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }

        return info;
    }

    /**
     * Lấy số điện thoại SIM qua AT+CNUM
     */
    private String getPhoneNumber(SerialPort port) {
        try {
            String response = sendATCommand(port, "AT+CNUM");

            // Parse response: +CNUM: "","+84901234567",145
            if (response != null && response.contains("+CNUM:")) {
                String[] parts = response.split("\"");
                if (parts.length >= 4) {
                    return parts[3].replace("+84", "0"); // Convert +84 to 0
                }
            }
        } catch (Exception e) {
            log.debug("Không lấy được phone number từ {}", port.getSystemPortName());
        }
        return null;
    }

    /**
     * Lấy tên nhà mạng qua AT+COPS?
     */
    private String getCarrier(SerialPort port) {
        try {
            String response = sendATCommand(port, "AT+COPS?");

            // Parse response: +COPS: 0,0,"VIETTEL",7
            if (response != null && response.contains("+COPS:")) {
                String[] parts = response.split("\"");
                if (parts.length >= 2) {
                    return parts[1]; // VIETTEL, VINAPHONE, MOBIFONE, etc
                }
            }
        } catch (Exception e) {
            log.debug("Không lấy được carrier từ {}", port.getSystemPortName());
        }
        return null;
    }

    /**
     * Lấy IMEI qua AT+GSN or AT+CGSN
     */
    private String getIMEI(SerialPort port) {
        try {
            String response = sendATCommand(port, "AT+CGSN");

            if (response != null) {
                // IMEI thường là 15 chữ số
                String[] lines = response.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.matches("\\d{15}")) {
                        return line;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Không lấy được IMEI từ {}", port.getSystemPortName());
        }
        return null;
    }

    /**
     * Lấy signal strength qua AT+CSQ
     */
    private String getSignalStrength(SerialPort port) {
        try {
            String response = sendATCommand(port, "AT+CSQ");

            // Parse response: +CSQ: 25,99
            if (response != null && response.contains("+CSQ:")) {
                String[] parts = response.split(":");
                if (parts.length >= 2) {
                    String[] values = parts[1].trim().split(",");
                    if (values.length >= 1) {
                        int rssi = Integer.parseInt(values[0].trim());
                        return rssi + " (" + getSignalQuality(rssi) + ")";
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Không lấy được signal từ {}", port.getSystemPortName());
        }
        return null;
    }

    /**
     * Convert RSSI to quality
     */
    private String getSignalQuality(int rssi) {
        if (rssi >= 20)
            return "Excellent";
        if (rssi >= 15)
            return "Good";
        if (rssi >= 10)
            return "Fair";
        if (rssi >= 5)
            return "Poor";
        return "No Signal";
    }

    /**
     * Gửi AT command và đợi response
     */
    private String sendATCommand(SerialPort port, String command) throws Exception {
        // Clear input buffer
        while (port.bytesAvailable() > 0) {
            port.readBytes(new byte[port.bytesAvailable()], port.bytesAvailable());
        }

        // Send command
        byte[] cmdBytes = (command + "\r").getBytes();
        port.writeBytes(cmdBytes, cmdBytes.length);

        // Wait and read response
        Thread.sleep(500);

        StringBuilder response = new StringBuilder();
        long timeout = System.currentTimeMillis() + 2000;

        while (System.currentTimeMillis() < timeout) {
            if (port.bytesAvailable() > 0) {
                byte[] buffer = new byte[port.bytesAvailable()];
                port.readBytes(buffer, buffer.length);
                response.append(new String(buffer));

                if (response.toString().contains("OK") || response.toString().contains("ERROR")) {
                    break;
                }
            }
            Thread.sleep(50);
        }

        return response.toString();
    }

    /**
     * Class chứa thông tin port
     */
    public static class PortInfo {
        private String comPort;
        private String phoneNumber;
        private String carrier;
        private String imei;
        private String signalStrength;
        private boolean available;
        private String status;

        // Getters and Setters
        public String getComPort() {
            return comPort;
        }

        public void setComPort(String comPort) {
            this.comPort = comPort;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getCarrier() {
            return carrier;
        }

        public void setCarrier(String carrier) {
            this.carrier = carrier;
        }

        public String getImei() {
            return imei;
        }

        public void setImei(String imei) {
            this.imei = imei;
        }

        public String getSignalStrength() {
            return signalStrength;
        }

        public void setSignalStrength(String signalStrength) {
            this.signalStrength = signalStrength;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
