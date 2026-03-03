package com.logmng.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 활동 로그 통계 서비스 (user_activity_log 기반)
 * - 일별/월별 통계, 사용자별 통계, 사용자/부서/IP 목록, CSV export
 * - 로그타입 '전체' 시: 각 로그타입(LOGIN, pb_feplog, java_fw_imglog)별 집계 합산으로 제공하여
 *   전체 수치 = 개별 로그타입 합계 가 되도록 함.
 */
@Service
public class ActivityStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(ActivityStatisticsService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 통계에 사용하는 로그타입 ID 목록 (전체 = 이 목록별 집계의 합) */
    private static final List<String> STATISTICS_LOG_TYPE_IDS = Collections.unmodifiableList(
            Arrays.asList("LOGIN", "pb_feplog", "java_fw_imglog"));

    private final DataSource dataSource;

    public ActivityStatisticsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 일별 통계 조회
     * action_type: LOGIN -> totalLogins, SEARCH/ADVANCED_SEARCH/STATS_VIEW -> totalSearches, DECRYPT -> totalDecrypts
     * 로그타입이 비어 있으면 '전체'로 간주하고, 각 로그타입별 집계를 합산하여 반환(전체 = 합계 정합성 유지).
     */
    public Map<String, Object> getDailyStatistics(String startDate, String endDate,
                                                   String logType, String userId, String department, String ip) {
        if (logType == null || logType.trim().isEmpty()) {
            return getDailyStatisticsAsSumOfLogTypes(startDate, endDate, userId, department, ip);
        }
        DailyStatisticsRaw raw = getDailyStatisticsRaw(startDate, endDate, logType, userId, department, ip);
        return buildDailyResponse(raw.dailyStats, raw.totalSearches, raw.totalDecrypts, raw.totalLogins, raw.uniqueUsers.size());
    }

    /** 로그타입 '전체': 각 통계 로그타입별 집계를 합산하여 반환 */
    private Map<String, Object> getDailyStatisticsAsSumOfLogTypes(String startDate, String endDate,
                                                                   String userId, String department, String ip) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        long totalSearches = 0, totalDecrypts = 0, totalLogins = 0;
        Set<String> uniqueUsers = new HashSet<>();

        for (String typeId : STATISTICS_LOG_TYPE_IDS) {
            DailyStatisticsRaw raw = getDailyStatisticsRaw(startDate, endDate, typeId, userId, department, ip);
            totalSearches += raw.totalSearches;
            totalDecrypts += raw.totalDecrypts;
            totalLogins += raw.totalLogins;
            uniqueUsers.addAll(raw.uniqueUsers);
            for (Map<String, Object> row : raw.dailyStats) {
                String date = (String) row.get("date");
                byDate.computeIfAbsent(date, d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", d);
                    m.put("totalSearches", 0L);
                    m.put("totalDecrypts", 0L);
                    m.put("totalLogins", 0L);
                    return m;
                });
                Map<String, Object> m = byDate.get(date);
                m.put("totalSearches", ((Number) m.get("totalSearches")).longValue() + ((Number) row.get("totalSearches")).intValue());
                m.put("totalDecrypts", ((Number) m.get("totalDecrypts")).longValue() + ((Number) row.get("totalDecrypts")).intValue());
                m.put("totalLogins", ((Number) m.get("totalLogins")).longValue() + ((Number) row.get("totalLogins")).intValue());
            }
        }

        List<Map<String, Object>> dailyStats = new ArrayList<>(byDate.values());
        dailyStats.sort(Comparator.comparing(m -> (String) m.get("date")));
        return buildDailyResponse(dailyStats, totalSearches, totalDecrypts, totalLogins, uniqueUsers.size());
    }

    /** 일별 통계 원시 결과 (한 로그타입에 대해) */
    private static class DailyStatisticsRaw {
        final List<Map<String, Object>> dailyStats;
        final long totalSearches, totalDecrypts, totalLogins;
        final Set<String> uniqueUsers;

        DailyStatisticsRaw(List<Map<String, Object>> dailyStats, long totalSearches, long totalDecrypts, long totalLogins, Set<String> uniqueUsers) {
            this.dailyStats = dailyStats;
            this.totalSearches = totalSearches;
            this.totalDecrypts = totalDecrypts;
            this.totalLogins = totalLogins;
            this.uniqueUsers = uniqueUsers;
        }
    }

    private DailyStatisticsRaw getDailyStatisticsRaw(String startDate, String endDate,
                                                     String logType, String userId, String department, String ip) {
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        long totalSearches = 0, totalDecrypts = 0, totalLogins = 0;
        Set<String> uniqueUsers = new HashSet<>();

        String sql = buildDailyMonthlyWhere(startDate, endDate, logType, userId, department, ip);
        if (sql == null) {
            return new DailyStatisticsRaw(dailyStats, totalSearches, totalDecrypts, totalLogins, uniqueUsers);
        }

        String query =
                "SELECT DATE(created_at) AS dt, " +
                "  COUNT(*) FILTER (WHERE action_type IN ('SEARCH','ADVANCED_SEARCH','STATS_VIEW')) AS searches, " +
                "  COUNT(*) FILTER (WHERE action_type = 'DECRYPT') AS decrypts, " +
                "  COUNT(*) FILTER (WHERE action_type = 'LOGIN') AS logins, " +
                "  array_agg(DISTINCT user_id) AS users " +
                "FROM user_activity_log " + sql +
                "GROUP BY DATE(created_at) ORDER BY dt";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            int idx = 1;
            if (startDate != null && !startDate.isEmpty()) {
                ps.setObject(idx++, java.sql.Date.valueOf(LocalDate.parse(startDate)));
            }
            if (endDate != null && !endDate.isEmpty()) {
                ps.setObject(idx++, java.sql.Date.valueOf(LocalDate.parse(endDate)));
            }
            if (logType != null && !logType.isEmpty()) {
                if ("LOGIN".equalsIgnoreCase(logType)) {
                    ps.setString(idx++, "LOGIN");
                } else {
                    ps.setString(idx++, "%\"logType\":\"" + logType + "\"%");
                }
            }
            if (userId != null && !userId.isEmpty()) ps.setString(idx++, userId);
            if (ip != null && !ip.isEmpty()) ps.setString(idx++, ip);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getDate("dt").toLocalDate().format(DATE_FORMAT);
                    int searches = rs.getInt("searches");
                    int decrypts = rs.getInt("decrypts");
                    int logins = rs.getInt("logins");
                    Array arr = rs.getArray("users");
                    if (arr != null) {
                        String[] users = (String[]) arr.getArray();
                        if (users != null) uniqueUsers.addAll(Arrays.asList(users));
                    }
                    totalSearches += searches;
                    totalDecrypts += decrypts;
                    totalLogins += logins;

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", date);
                    row.put("totalSearches", searches);
                    row.put("totalDecrypts", decrypts);
                    row.put("totalLogins", logins);
                    dailyStats.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("일별 통계 조회 실패: startDate={}, endDate={}, logType={}", startDate, endDate, logType, e);
            throw new RuntimeException("일별 통계 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }

        return new DailyStatisticsRaw(dailyStats, totalSearches, totalDecrypts, totalLogins, uniqueUsers);
    }

    private Map<String, Object> buildDailyResponse(List<Map<String, Object>> dailyStats,
                                                    long totalSearches, long totalDecrypts, long totalLogins, int uniqueUsers) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSearches", totalSearches);
        summary.put("totalDecrypts", totalDecrypts);
        summary.put("totalLogins", totalLogins);
        summary.put("uniqueUsers", uniqueUsers);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dailyStats", dailyStats);
        data.put("summary", summary);
        return data;
    }

    /**
     * 월별 통계: 해당 월의 일별 집계와 동일한 구조
     */
    public Map<String, Object> getMonthlyStatistics(int year, int month,
                                                      String logType, String userId, String department, String ip) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        String startStr = start.format(DATE_FORMAT);
        String endStr = end.format(DATE_FORMAT);

        Map<String, Object> data = getDailyStatistics(startStr, endStr, logType, userId, department, ip);
        data.put("year", year);
        data.put("month", month);
        data.put("monthLabel", year + "-" + String.format("%02d", month));
        return data;
    }

    /**
     * 모든 사용자별 통계 (활동 로그 기준)
     * 로그타입이 비어 있으면 '전체'로 간주하고, 각 로그타입별 집계를 사용자별로 합산하여 반환.
     */
    public List<Map<String, Object>> getAllUserStatistics(String startDate, String endDate,
                                                           String logType, String userId, String department, String ip) {
        if (logType == null || logType.trim().isEmpty()) {
            return getAllUserStatisticsAsSumOfLogTypes(startDate, endDate, userId, department, ip);
        }
        return getOneLogTypeUserStatistics(startDate, endDate, logType, userId, department, ip);
    }

    /** 로그타입 '전체': 각 통계 로그타입별 사용자 통계를 사용자별로 합산 */
    private List<Map<String, Object>> getAllUserStatisticsAsSumOfLogTypes(String startDate, String endDate,
                                                                            String userId, String department, String ip) {
        Map<String, Map<String, Object>> byUserId = new LinkedHashMap<>();
        for (String typeId : STATISTICS_LOG_TYPE_IDS) {
            List<Map<String, Object>> list = getOneLogTypeUserStatistics(startDate, endDate, typeId, userId, department, ip);
            for (Map<String, Object> row : list) {
                String uId = (String) row.get("userId");
                byUserId.computeIfAbsent(uId, id -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", id);
                    m.put("userName", row.get("userName"));
                    m.put("totalCount", 0L);
                    m.put("loginCount", 0L);
                    m.put("searchCount", 0L);
                    m.put("decryptCount", 0L);
                    return m;
                });
                Map<String, Object> m = byUserId.get(uId);
                m.put("totalCount", ((Number) m.get("totalCount")).longValue() + ((Number) row.get("totalCount")).longValue());
                m.put("loginCount", ((Number) m.get("loginCount")).longValue() + ((Number) row.get("loginCount")).longValue());
                m.put("searchCount", ((Number) m.get("searchCount")).longValue() + ((Number) row.get("searchCount")).longValue());
                m.put("decryptCount", ((Number) m.get("decryptCount")).longValue() + ((Number) row.get("decryptCount")).longValue());
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(byUserId.values());
        list.sort((a, b) -> Long.compare(((Number) b.get("totalCount")).longValue(), ((Number) a.get("totalCount")).longValue()));
        return list;
    }

    /** 단일 로그타입에 대한 사용자별 통계 */
    private List<Map<String, Object>> getOneLogTypeUserStatistics(String startDate, String endDate,
                                                                    String logType, String userId, String department, String ip) {
        String where = buildDailyMonthlyWhere(startDate, endDate, logType, userId, department, ip);
        if (where == null) {
            return new ArrayList<>();
        }

        String query =
                "SELECT user_id AS \"userId\", username AS \"userName\", " +
                "  COUNT(*) AS \"totalCount\", " +
                "  COUNT(*) FILTER (WHERE action_type = 'LOGIN') AS \"loginCount\", " +
                "  COUNT(*) FILTER (WHERE action_type IN ('SEARCH','ADVANCED_SEARCH','STATS_VIEW')) AS \"searchCount\", " +
                "  COUNT(*) FILTER (WHERE action_type = 'DECRYPT') AS \"decryptCount\" " +
                "FROM user_activity_log " + where +
                "GROUP BY user_id, username ORDER BY \"totalCount\" DESC";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            int idx = 1;
            if (startDate != null && !startDate.isEmpty()) ps.setObject(idx++, java.sql.Date.valueOf(LocalDate.parse(startDate)));
            if (endDate != null && !endDate.isEmpty()) ps.setObject(idx++, java.sql.Date.valueOf(LocalDate.parse(endDate)));
            if (logType != null && !logType.isEmpty()) {
                if ("LOGIN".equalsIgnoreCase(logType)) ps.setString(idx++, "LOGIN");
                else ps.setString(idx++, "%\"logType\":\"" + logType + "\"%");
            }
            if (userId != null && !userId.isEmpty()) ps.setString(idx++, userId);
            if (ip != null && !ip.isEmpty()) ps.setString(idx++, ip);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", rs.getString("userId"));
                    row.put("userName", rs.getString("userName"));
                    row.put("totalCount", rs.getLong("totalCount"));
                    row.put("loginCount", rs.getLong("loginCount"));
                    row.put("searchCount", rs.getLong("searchCount"));
                    row.put("decryptCount", rs.getLong("decryptCount"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("사용자별 통계 조회 실패: startDate={}, endDate={}, logType={}", startDate, endDate, logType, e);
            throw new RuntimeException("사용자별 통계 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Map<String, String>> getUsers() {
        return getUsers(null);
    }

    /** When userIdFilter is not null, return only that user (for scope=self). */
    public List<Map<String, String>> getUsers(String userIdFilter) {
        String sql;
        if (userIdFilter != null && !userIdFilter.isEmpty()) {
            sql = "SELECT DISTINCT user_id AS \"userId\", username AS \"userName\" FROM user_activity_log WHERE user_id = ? ORDER BY user_id";
            return runListQueryWithParam(sql, "userId", "userName", userIdFilter);
        }
        sql = "SELECT DISTINCT user_id AS \"userId\", username AS \"userName\" FROM user_activity_log ORDER BY user_id";
        return runListQuery(sql, "userId", "userName");
    }

    public List<String> getDepartments() {
        // user_activity_log에 department 컬럼 없음
        return new ArrayList<>();
    }

    public List<String> getIps() {
        return getIps(null);
    }

    /** When userIdFilter is not null, return only IPs for that user (for scope=self). */
    public List<String> getIps(String userIdFilter) {
        String sql;
        if (userIdFilter != null && !userIdFilter.isEmpty()) {
            sql = "SELECT DISTINCT ip_address FROM user_activity_log WHERE user_id = ? AND ip_address IS NOT NULL AND ip_address != '' ORDER BY ip_address";
            List<String> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userIdFilter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getString("ip_address"));
                    }
                }
            } catch (SQLException e) {
                log.error("IP 목록 조회 실패", e);
                throw new RuntimeException("IP 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
            }
            return list;
        }
        sql = "SELECT DISTINCT ip_address FROM user_activity_log WHERE ip_address IS NOT NULL AND ip_address != '' ORDER BY ip_address";
        List<String> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("ip_address"));
            }
        } catch (SQLException e) {
            log.error("IP 목록 조회 실패", e);
            throw new RuntimeException("IP 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    private List<Map<String, String>> runListQueryWithParam(String sql, String key1, String key2, String param) {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put(key1, rs.getString(key1));
                    row.put(key2, rs.getString(key2));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("목록 조회 실패: {}", sql, e);
            throw new RuntimeException("목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    private List<Map<String, String>> runListQuery(String sql, String key1, String key2) {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put(key1, rs.getString(key1));
                row.put(key2, rs.getString(key2));
                list.add(row);
            }
        } catch (SQLException e) {
            log.error("목록 조회 실패: {}", sql, e);
            throw new RuntimeException("목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * WHERE 절과 파라미터 순서: startDate, endDate, (optional) logType, userId, department, ip
     * logType이 LOGIN이면 action_type = 'LOGIN'; 그 외에는 action_detail::text LIKE '%"logType":"xxx"%'
     */
    private String buildDailyMonthlyWhere(String startDate, String endDate,
                                           String logType, String userId, String department, String ip) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1 ");
        if (startDate != null && !startDate.isEmpty()) {
            sb.append(" AND created_at >= ?::date ");
        }
        if (endDate != null && !endDate.isEmpty()) {
            sb.append(" AND created_at <= ?::date + INTERVAL '1 day' ");
        }
        if (logType != null && !logType.isEmpty()) {
            if ("LOGIN".equalsIgnoreCase(logType)) {
                sb.append(" AND action_type = ? ");
            } else {
                sb.append(" AND action_detail::text LIKE ? ");
            }
        }
        if (userId != null && !userId.isEmpty()) sb.append(" AND user_id = ? ");
        // department: user_activity_log에 컬럼 없음, 추후 확장 시 추가
        if (ip != null && !ip.isEmpty()) sb.append(" AND ip_address = ? ");
        return sb.toString();
    }

    /**
     * CSV export (일별 또는 월별)
     */
    public byte[] exportCsv(String type, String startDate, String endDate, Integer year, Integer month,
                            String logType, String userId, String department, String ip) {
        Map<String, Object> data;
        if ("daily".equalsIgnoreCase(type)) {
            data = getDailyStatistics(startDate, endDate, logType, userId, department, ip);
        } else {
            if (year == null || month == null) {
                throw new IllegalArgumentException("월별 export 시 year, month 필요");
            }
            data = getMonthlyStatistics(year, month, logType, userId, department, ip);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dailyStats = (List<Map<String, Object>>) data.get("dailyStats");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = data.get("summary") != null ? (Map<String, Object>) data.get("summary") : null;

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // UTF-8 BOM
        csv.append("날짜,검색 횟수,복호화 횟수,로그인 횟수\r\n");
        if (dailyStats != null) {
            for (Map<String, Object> row : dailyStats) {
                csv.append(row.get("date")).append(",")
                   .append(row.getOrDefault("totalSearches", 0)).append(",")
                   .append(row.getOrDefault("totalDecrypts", 0)).append(",")
                   .append(row.getOrDefault("totalLogins", 0)).append("\r\n");
            }
        }
        if (summary != null) {
            csv.append("요약\r\n");
            csv.append("전체 검색 횟수,").append(summary.getOrDefault("totalSearches", 0)).append("\r\n");
            csv.append("전체 복호화 횟수,").append(summary.getOrDefault("totalDecrypts", 0)).append("\r\n");
            csv.append("전체 로그인 횟수,").append(summary.getOrDefault("totalLogins", 0)).append("\r\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
