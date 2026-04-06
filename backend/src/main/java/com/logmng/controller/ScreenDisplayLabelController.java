package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.dto.request.ScreenDisplayLabelsPutRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenDisplayLabelItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.ScreenDisplayLabelApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET/PUT /api/screen-display-labels. Spec: specs/menu-display-labels.spec.yaml.
 */
@RestController
@RequestMapping("/api/screen-display-labels")
public class ScreenDisplayLabelController {

    private static final Logger log = LoggerFactory.getLogger(ScreenDisplayLabelController.class);

    private final ScreenDisplayLabelApi screenDisplayLabelApi;
    private final AuthService authService;
    private final DecryptApproverService decryptApproverService;
    private final AppUserResolver appUserResolver;

    public ScreenDisplayLabelController(ScreenDisplayLabelApi screenDisplayLabelApi,
                                        AuthService authService,
                                        DecryptApproverService decryptApproverService,
                                        AppUserResolver appUserResolver) {
        this.screenDisplayLabelApi = screenDisplayLabelApi;
        this.authService = authService;
        this.decryptApproverService = decryptApproverService;
        this.appUserResolver = appUserResolver;
    }

    private static boolean isSystemAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object v = session.getAttribute("isSystemAdmin");
        return Boolean.TRUE.equals(v);
    }

    private void requireSystemAdmin(HttpServletRequest request) {
        if (authService.getCurrentUserInfo(request) == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!decryptApproverService.isAdmin(isSystemAdmin(request))) {
            log.info("screen-display-labels write denied: not system admin");
            throw CustomException.forbidden("시스템 관리자만 화면 표시 라벨을 변경할 수 있습니다.", "FORBIDDEN");
        }
    }

    private long requireActorAppUserId(LoginResponse user) {
        Long uid = user.getUserId();
        if (uid != null) {
            return uid;
        }
        Long byName = appUserResolver.getIdByUsername(user.getUsername());
        if (byName != null) {
            return byName;
        }
        throw CustomException.badRequest("현재 사용자 ID를 확인할 수 없습니다.", "INVALID_INPUT");
    }

    /**
     * GET — authenticated; non-admin responses omit labelAdmin.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScreenDisplayLabelItemResponse>>> get(HttpServletRequest request) {
        LoginResponse user = authService.getCurrentUserInfo(request);
        if (user == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        boolean sysAdmin = Boolean.TRUE.equals(user.getIsSystemAdmin());
        List<ScreenDisplayLabelItemResponse> data = screenDisplayLabelApi.listForViewer(sysAdmin);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * PUT — system admin only; upsert labels. Canonical write method (PATCH not mapped).
     */
    @PutMapping
    @ActivityLog(actionType = "SCREEN_DISPLAY_LABELS_UPDATE", description = "화면 표시 라벨 저장", includeParams = false, includeResponse = false)
    public ResponseEntity<ApiResponse<List<ScreenDisplayLabelItemResponse>>> put(
            @RequestBody ScreenDisplayLabelsPutRequest body,
            HttpServletRequest request) {
        requireSystemAdmin(request);
        LoginResponse user = authService.getCurrentUserInfo(request);
        long actorId = requireActorAppUserId(user);
        List<ScreenDisplayLabelItemResponse> data = screenDisplayLabelApi.replaceAll(body, actorId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
