package com.logmng.controller;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.ActivityStatisticsService;
import com.logmng.service.AuthService;
import com.logmng.util.ScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 활동 로그 통계 API (contract: 9200 백엔드)
 * - 일별/월별 통계, 사용자별 통계, 사용자/부서/IP 목록, CSV export
 * - Scope enforcement: is_system_admin=false and scope='self' → override userId with current user, ignore department/ip.
 */
@RestController
@RequestMapping("/api/statistics")
public class ActivityStatisticsController {

    private static final Logger log = LoggerFactory.getLogger(ActivityStatisticsController.class);

    private final ActivityStatisticsService activityStatisticsService;
    private final AuthService authService;

    public ActivityStatisticsController(ActivityStatisticsService activityStatisticsService, AuthService authService) {
        this.activityStatisticsService = activityStatisticsService;
        this.authService = authService;
    }

    /** Apply scope: when scope='self', override userId with current user, ignore department/ip. Uses AuthService for DB-backed scope. */
    private String[] applyScopeForStatistics(HttpServletRequest request, String userId, String department, String ip) {
        LoginResponse userInfo = authService.getCurrentUserInfo(request);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.STATISTICS, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        if ("all".equals(scope)) {
            return new String[]{userId, department, ip};
        }
        String currentUser = userInfo.getUsername();
        if (currentUser == null || currentUser.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        return new String[]{currentUser, null, null};
    }

    @GetMapping("/activity/daily")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDaily(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip,
            HttpServletRequest request) {
        log.debug("일별 통계 조회: startDate={}, endDate={}", startDate, endDate);
        String[] applied = applyScopeForStatistics(request, userId, department, ip);
        Map<String, Object> data = activityStatisticsService.getDailyStatistics(
                startDate, endDate, logType, applied[0], applied[1], applied[2]);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/activity/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip,
            HttpServletRequest request) {
        log.debug("월별 통계 조회: year={}, month={}", year, month);
        int y = year != null ? year : java.time.LocalDate.now().getYear();
        int m = month != null ? month : java.time.LocalDate.now().getMonthValue();
        String[] applied = applyScopeForStatistics(request, userId, department, ip);
        Map<String, Object> data = activityStatisticsService.getMonthlyStatistics(
                y, m, logType, applied[0], applied[1], applied[2]);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/activity/users/all")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllUserStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ip,
            HttpServletRequest request) {
        log.debug("전체 사용자별 통계: startDate={}, endDate={}", startDate, endDate);
        String[] applied = applyScopeForStatistics(request, userId, department, ip);
        List<Map<String, Object>> data = activityStatisticsService.getAllUserStatistics(
                startDate, endDate, logType, applied[0], applied[1], applied[2]);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getUsers(HttpServletRequest request) {
        LoginResponse userInfo = authService.getCurrentUserInfo(request);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.STATISTICS, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String userIdFilter = null;
        if ("self".equals(scope)) {
            userIdFilter = userInfo.getUsername();
            if (userIdFilter == null || userIdFilter.isBlank()) {
                throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
            }
        }
        List<Map<String, String>> data = activityStatisticsService.getUsers(userIdFilter);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<String>>> getDepartments() {
        List<String> data = activityStatisticsService.getDepartments();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/ips")
    public ResponseEntity<ApiResponse<List<String>>> getIps(HttpServletRequest request) {
        LoginResponse userInfo = authService.getCurrentUserInfo(request);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.STATISTICS, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String userIdFilter = null;
        if ("self".equals(scope)) {
            userIdFilter = userInfo.getUsername();
            if (userIdFilter == null || userIdFilter.isBlank()) {
                throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
            }
        }
        List<String> data = activityStatisticsService.getIps(userIdFilter);
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
            @RequestParam(required = false) String ip,
            HttpServletRequest request) {
        log.debug("통계 export: type={}, startDate={}, endDate={}", type, startDate, endDate);
        String[] applied = applyScopeForStatistics(request, userId, department, ip);
        byte[] body = activityStatisticsService.exportCsv(
                type, startDate, endDate, year, month, logType, applied[0], applied[1], applied[2]);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "activity_statistics_" + type + ".csv");
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
