package com.logmng.util;

/**
 * Default initial password for locally provisioned (non-directory) app users.
 * Stored in {@code app_user.password_hash} as plaintext in dev/local mode to match {@link com.logmng.service.AuthService#loginLocal}.
 */
public final class LocalUserInitialPassword {

    /** Product default for new local users (req 20260408-my-page-local-password-and-profile). */
    public static final String PLAINTEXT = "user123";

    private LocalUserInitialPassword() {
    }

    /**
     * Value written to {@code password_hash} for new local-identity rows (v2 direct create, etc.).
     */
    public static String storedValueForNewLocalUser() {
        return PLAINTEXT;
    }
}
