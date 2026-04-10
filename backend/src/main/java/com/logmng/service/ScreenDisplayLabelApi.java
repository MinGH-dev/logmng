package com.logmng.service;

import com.logmng.dto.request.ScreenDisplayLabelsPutRequest;
import com.logmng.dto.response.ScreenDisplayLabelItemResponse;

import java.util.List;

/**
 * Screen display labels (GET list / PUT upsert). Implemented by {@link ScreenDisplayLabelService}.
 */
public interface ScreenDisplayLabelApi {

    List<ScreenDisplayLabelItemResponse> listForViewer(boolean systemAdmin);

    List<ScreenDisplayLabelItemResponse> replaceAll(ScreenDisplayLabelsPutRequest body, long actorAppUserId);
}
