# Check DB connection

With the backend running, verify **DB connection**. (If backend is not up, run `/start-backend` first.)

**1. Backend up?**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health
```
- If not 200: start the backend and try again.

**2. DB test** (GET /api/db/test, port 9200)
```bash
curl -s http://localhost:9200/api/db/test
```

- **200** and JSON **`data.connected` === true** → DB connected. Optionally summarize `data.databaseProductName`, `data.databaseProductVersion`, `data.pb_send_table_exists`.
- **`data.connected` === false** or error → DB (PostgreSQL, etc.) down or misconfigured. Check datasource in `application.yml` and DB server.

Summarize: **up or failed**; if failed, one-line cause (e.g. backend down / DB connection failed).

**Output format (one line):** `DB: connected` or `DB: failed — cause`
