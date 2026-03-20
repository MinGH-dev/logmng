package com.logmng.service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Stub AppUserResolver for controller tests. Maps numeric id to username for API→DB resolution.
 */
public class StubAppUserResolver extends AppUserResolver {

    private final Map<Long, String> idToUsername = new HashMap<>();
    private final Map<String, Long> usernameToId = new HashMap<>();

    public StubAppUserResolver(DataSource dataSource) {
        super(dataSource);
    }

    /** Map id 20260002 → "otherUser" for tests that send numeric userId. */
    public static StubAppUserResolver withOtherUser() {
        StubAppUserResolver r = new StubAppUserResolver(null);
        r.idToUsername.put(20260002L, "otherUser");
        r.usernameToId.put("otherUser", 20260002L);
        return r;
    }

    public void map(Long id, String username) {
        idToUsername.put(id, username);
        usernameToId.put(username, id);
    }

    @Override
    public String getUsernameById(Long id) {
        return idToUsername.get(id);
    }

    @Override
    public Long getIdByUsername(String username) {
        return usernameToId.get(username);
    }
}
