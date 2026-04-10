package com.logmng.util;

import com.logmng.constants.ScreenConstants;

import java.util.List;

/**
 * Resolves effective screen id for {@code listContext} on GET /api/search-history and GET /api/search-history/{id}.
 * Per docs/api-definition.md §6.1.2, docs/contract.md (listContext 스코프 해석).
 */
public final class SearchHistoryListContextHelper {

    private SearchHistoryListContextHelper() {
    }

    /**
     * @param listContextQuery raw query param (may be null/blank)
     * @param allowedScreenIds   non-null allowed list from auth (may be empty)
     * @return {@link ScreenConstants#SEARCH_HISTORY} or {@link ScreenConstants#PENDING_APPROVALS}
     * @throws ListContextResolutionException when value is invalid or caller may not use the requested context
     */
    public static String resolveEffectiveScreenId(String listContextQuery, List<String> allowedScreenIds)
            throws ListContextResolutionException {
        boolean hasSh = allowedScreenIds != null && allowedScreenIds.contains(ScreenConstants.SEARCH_HISTORY);
        boolean hasPa = allowedScreenIds != null && allowedScreenIds.contains(ScreenConstants.PENDING_APPROVALS);

        if (listContextQuery != null && !listContextQuery.isBlank()) {
            String v = listContextQuery.trim();
            if (ScreenConstants.SEARCH_HISTORY.equals(v)) {
                if (!hasSh) {
                    throw new ListContextResolutionException("listContext search-history not allowed for user");
                }
                return ScreenConstants.SEARCH_HISTORY;
            }
            if (ScreenConstants.PENDING_APPROVALS.equals(v)) {
                if (!hasPa) {
                    throw new ListContextResolutionException("listContext pending-approvals not allowed for user");
                }
                return ScreenConstants.PENDING_APPROVALS;
            }
            throw new ListContextResolutionException("invalid listContext value");
        }
        if (hasSh) {
            return ScreenConstants.SEARCH_HISTORY;
        }
        if (hasPa) {
            return ScreenConstants.PENDING_APPROVALS;
        }
        throw new ListContextResolutionException("no search-history or pending-approvals screen");
    }

    /** Checked exception so callers map to 403 / 400 without generic RuntimeException. */
    public static final class ListContextResolutionException extends Exception {
        public ListContextResolutionException(String message) {
            super(message);
        }
    }
}
