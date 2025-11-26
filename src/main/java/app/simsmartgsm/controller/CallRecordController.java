package app.simsmartgsm.controller;

import app.simsmartgsm.entity.CallRecord;
import app.simsmartgsm.repository.CallRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller cho Call Records
 */
@RestController
@RequestMapping("/api/call-records")
@RequiredArgsConstructor
@Slf4j
public class CallRecordController {

    private final CallRecordRepository callRecordRepository;

    /**
     * Lấy danh sách call records gần đây
     * GET /api/call-records/recent?limit=50
     */
    @GetMapping("/recent")
    public List<CallRecord> getRecentCallRecords(
            @RequestParam(defaultValue = "50") int limit) {

        log.info("📞 Getting recent {} call records", limit);

        try {
            PageRequest pageRequest = PageRequest.of(
                    0,
                    limit,
                    Sort.by(Sort.Direction.DESC, "startTime"));

            return callRecordRepository.findAll(pageRequest).getContent();

        } catch (Exception e) {
            log.error("Error getting recent call records", e);
            return List.of();
        }
    }

    /**
     * Lấy call records theo COM port
     * GET /api/call-records/by-port?comPort=COM75&limit=20
     */
    @GetMapping("/by-port")
    public List<CallRecord> getCallRecordsByPort(
            @RequestParam String comPort,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("📞 Getting call records for port: {}", comPort);

        try {
            PageRequest pageRequest = PageRequest.of(
                    0,
                    limit,
                    Sort.by(Sort.Direction.DESC, "startTime"));

            return callRecordRepository.findByComPort(comPort, pageRequest).getContent();

        } catch (Exception e) {
            log.error("Error getting call records for port {}", comPort, e);
            return List.of();
        }
    }
}
