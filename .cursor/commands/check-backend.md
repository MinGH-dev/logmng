# Check backend health

Run the following in the terminal to verify (1) backend is up and (2) DB connection, then summarize.

**1. Backend health** (default port 9200, `backend/src/main/resources/application.yml` server.port)

```bash
curl -s http://localhost:9200/api/health
```

- **200 OK** and JSON (`status`, `message`, etc.) → backend is up.
- Connection failure → backend not running. Start: `/start-backend` or `./scripts/dev-services.sh backend start` (see `docs/QUICK_START.md`).

**2. DB connection** (same port, GET /api/db/test)

```bash
curl -s http://localhost:9200/api/db/test
```

- **200 OK** and JSON **`data.connected` === true** → DB connected. Optionally summarize `databaseProductName`, `pb_send_table_exists`, etc.
- **`data.connected` === false** or failure → DB down or misconfigured. Check datasource in `application.yml` and PostgreSQL.

**3. Summary**

- **Backend**: up or not.
- **DB**: connected or not; if not, one-line possible cause.
