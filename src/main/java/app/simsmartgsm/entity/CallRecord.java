package app.simsmartgsm.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "call_records")
public class CallRecord {

    @Id
    private String id;

    private String orderId;
    private Long customerId;

    private String deviceId; // NEW: ID của máy (để phân biệt giữa các máy)
    private String deviceName; // Tên thiết bị
    private String deviceLocation; // NEW: Vị trí máy

    private String simPhone;
    private String fromNumber;
    private String targetNumber; // NEW: Số điện thoại được gọi

    private String comPort;

    private String countryCode;
    private String serviceCode;

    private String status;
    private String callState; // NEW: COMPLETED, AUTO_HANGUP, MANUAL_HANGUP, etc.

    private String recordFile;
    private String recordingFileName; // NEW: Tên file ghi âm
    private String recordingFilePath; // NEW: Đường dẫn file ghi âm

    private Instant callStartTime;
    private Instant callEndTime;
    private Instant startTime; // NEW: Thời gian bắt đầu gọi
    private Instant connectTime; // NEW: Thời gian nhấc máy
    private Instant endTime; // NEW: Thời gian kết thúc
    private Integer durationSeconds; // NEW: Thời lượng cuộc gọi (giây)

    private Instant expireAt;
    private Instant createdAt;
    private Instant updatedAt;
}
