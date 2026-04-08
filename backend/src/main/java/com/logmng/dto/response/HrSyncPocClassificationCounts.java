package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Classification buckets for POST /api/hr-sync/poc/preview (integers ≥ 0).
 */
public class HrSyncPocClassificationCounts {

    @JsonProperty("TRANSFER")
    private int transfer;

    @JsonProperty("NEW_HIRE")
    private int newHire;

    @JsonProperty("RESIGNED")
    private int resigned;

    @JsonProperty("UNCHANGED")
    private int unchanged;

    @JsonProperty("PROFILE_UPDATE_NON_SECURITY")
    private int profileUpdateNonSecurity;

    @JsonProperty("CONFLICT")
    private int conflict;

    @JsonProperty("ORPHAN")
    private int orphan;

    public HrSyncPocClassificationCounts() {
    }

    public static HrSyncPocClassificationCounts allZeros() {
        return new HrSyncPocClassificationCounts();
    }

    /**
     * PoC stub: put read-only ext_employee row count into UNCHANGED; rest zero.
     */
    public static HrSyncPocClassificationCounts stubUnchangedOnly(long extEmployeeCount) {
        HrSyncPocClassificationCounts c = new HrSyncPocClassificationCounts();
        c.unchanged = (int) Math.min(Math.max(extEmployeeCount, 0), Integer.MAX_VALUE);
        return c;
    }

    public int getTransfer() {
        return transfer;
    }

    public void setTransfer(int transfer) {
        this.transfer = transfer;
    }

    public int getNewHire() {
        return newHire;
    }

    public void setNewHire(int newHire) {
        this.newHire = newHire;
    }

    public int getResigned() {
        return resigned;
    }

    public void setResigned(int resigned) {
        this.resigned = resigned;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(int unchanged) {
        this.unchanged = unchanged;
    }

    public int getProfileUpdateNonSecurity() {
        return profileUpdateNonSecurity;
    }

    public void setProfileUpdateNonSecurity(int profileUpdateNonSecurity) {
        this.profileUpdateNonSecurity = profileUpdateNonSecurity;
    }

    public int getConflict() {
        return conflict;
    }

    public void setConflict(int conflict) {
        this.conflict = conflict;
    }

    public int getOrphan() {
        return orphan;
    }

    public void setOrphan(int orphan) {
        this.orphan = orphan;
    }
}
