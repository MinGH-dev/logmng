package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-screen function capability. Per spec §4.4, docs/requirements/20250303-screen-function-availability.md, 20260306.
 * read: always present. write/approve/decrypt: present only for screens that support them. decrypt only for main.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenFunctionCapability {

    private boolean read;
    private Boolean write;
    private Boolean approve;
    private Boolean decrypt;

    public ScreenFunctionCapability() {
    }

    public ScreenFunctionCapability(boolean read) {
        this.read = read;
    }

    public ScreenFunctionCapability(boolean read, Boolean write, Boolean approve) {
        this.read = read;
        this.write = write;
        this.approve = approve;
    }

    public ScreenFunctionCapability(boolean read, Boolean write, Boolean approve, Boolean decrypt) {
        this.read = read;
        this.write = write;
        this.approve = approve;
        this.decrypt = decrypt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Boolean getWrite() {
        return write;
    }

    public void setWrite(Boolean write) {
        this.write = write;
    }

    public Boolean getApprove() {
        return approve;
    }

    public void setApprove(Boolean approve) {
        this.approve = approve;
    }

    public Boolean getDecrypt() {
        return decrypt;
    }

    public void setDecrypt(Boolean decrypt) {
        this.decrypt = decrypt;
    }
}
