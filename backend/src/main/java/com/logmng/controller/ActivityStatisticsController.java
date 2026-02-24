package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.service.ActivityStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 활동 로그 통계 API (contract: 9200 백엔드)
 * - 일별/월별 통계, 사용자별 통계, 사용자/부서/IP 목록, CSV export
 */
@RestController
@RequestMapping("/api/statistics")
public class ActivityStatisticsController {

    private static final Logger log = LoggerFactory.getLogger(ActivityStatisticsController.class);

    private final ActivityStatisticsService activityStatisticsService;

    public ActivityStatisticsController(ActivityStatisticsService activityStatisticsService) {
        this.activityStatisticsService = activityStatisticsService;
    }

    @GetMapping("/activity/daily")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDaily(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip) {
        log.debug("일별 통계 조회: startDate={}, endDate={}", startDate, endDate);
        Map<String, Object> data = activityStatisticsService.getDailyStatistics(
                startDate, endDate, logType, userId, department, ip);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/activity/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip) {
        log.debug("월별 통계 조회: year={}, month={}", year, month);
        int y = year != null ? year : java.time.LocalDate.now().getYear();
        int m = month != null ? month : java.time.LocalDate.now().getMonthValue();
        Map<String, Object> data = activityStatisticsService.getMonthlyStatistics(
                y, m, logType, userId, department, ip);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/activity/users/all")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllUserStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip) {
        log.debug("전체 사용자별 통계: startDate={}, endDate={}", startDate, endDate);
        List<Map<String, Object>> data = activityStatisticsService.getAllUserStatistics(
                startDate, endDate, logType, userId, department, ip);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getUsers() {
        List<Map<String, String>> data = activityStatisticsService.getUsers();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<String>>> getDepartments() {
        List<String> data = activityStatisticsService.getDepartments();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/ips")
    public ResponseEntity<ApiResponse<List<String>>> getIps() {
        List<String> data = activityStatisticsService.getIps();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping(value = "/activity/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportStatistics(
            @RequestParam String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip) {
        log.debug("통계 export: type={}, startDate={}, endDate={}", type, startDate, endDate);
        byte[] body = activityStatisticsService.exportCsv(
                type, startDate, endDate, year, month, logType, userId, department, ip);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "activity_statistics_" + type + ".csv");
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
