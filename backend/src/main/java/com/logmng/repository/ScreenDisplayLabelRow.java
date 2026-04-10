package com.logmng.repository;

/**
 * Row for {@code screen_display_label}.
 */
public class ScreenDisplayLabelRow {
    private String screenId;
    private String labelUser;
    private String labelAdmin;
    /** MENU_TREE top-level group id; null = client default. */
    private String parentGroupId;
    /** Sibling order within group; null = client default. */
    private Integer sortOrder;
    private java.sql.Timestamp updatedAt;
    private Long updatedBy;

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

    public java.sql.Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.sql.Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
