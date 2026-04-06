package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One screen label in PUT /api/screen-display-labels. Spec: specs/menu-display-labels.spec.yaml §3.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenDisplayLabelItemRequest {

    private String screenId;
    private String labelUser;
    private String labelAdmin;
    private String parentGroupId;
    private Integer sortOrder;

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
