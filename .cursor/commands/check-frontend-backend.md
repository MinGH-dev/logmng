# Check frontend and backend health

Run these commands in order and summarize whether frontend and backend are up.

## 1. Backend (default port 9200, application.yml server.port)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health
```

- **200** → up. Optionally run `curl -s http://localhost:9200/api/health` for body.
- Failure → backend not running. See `docs/QUICK_START.md` for how to start.

## 2. Frontend

**Docker Compose UI (contract verification, port 3001):**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

**Host CRA via `dev-services.sh` / `npm start` (default port 3002):**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3002
```

- **2xx** on the URL you expect → that stack is up. **Do not run host CRA on 3001** (reserved for Docker). See `docs/contract.md`.

## 3. Summary

Per service: **up or not**; if not, one-line command to start.
