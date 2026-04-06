package com.logmng.dto.request;

import java.util.List;

/**
 * Body for PUT /api/screen-display-labels.
 */
public class ScreenDisplayLabelsPutRequest {

    private List<ScreenDisplayLabelItemRequest> labels;

    public List<ScreenDisplayLabelItemRequest> getLabels() {
        return labels;
    }

    public void setLabels(List<ScreenDisplayLabelItemRequest> labels) {
        this.labels = labels;
    }
}
