package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Item in GET /api/screen-display-labels data[]. Non-admin clients omit {@link #labelAdmin} (null, NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenDisplayLabelItemResponse {

    private String screenId;
    private String labelUser;
    private String labelAdmin;
    private String parentGroupId;
    private Integer sortOrder;

    public ScreenDisplayLabelItemResponse() {
    }

    public ScreenDisplayLabelItemResponse(String screenId, String labelUser, String labelAdmin) {
        this(screenId, labelUser, labelAdmin, null, null);
    }

    public ScreenDisplayLabelItemResponse(String screenId, String labelUser, String labelAdmin,
                                          String parentGroupId, Integer sortOrder) {
        this.screenId = screenId;
        this.labelUser = labelUser;
        this.labelAdmin = labelAdmin;
        this.parentGroupId = parentGroupId;
        this.sortOrder = sortOrder;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public String getLabelUser() {
        return labelUser;
    }

    public void setLabelUser(String labelUser) {
        this.labelUser = labelUser;
    }

    public String getLabelAdmin() {
        return labelAdmin;
    }

    public void setLabelAdmin(String labelAdmin) {
        this.labelAdmin = labelAdmin;
    }

    public String getParentGroupId() {
        return parentGroupId;
    }

    public void setParentGroupId(String parentGroupId) {
        this.parentGroupId = parentGroupId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
