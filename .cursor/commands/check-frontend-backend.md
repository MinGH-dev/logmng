# Check frontend and backend health

Run these commands in order and summarize whether frontend and backend are up.

## 1. Backend (default port 9200, application.yml server.port)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health
```

- **200** → up. Optionally run `curl -s http://localhost:9200/api/health` for body.
- Failure → backend not running. See `docs/QUICK_START.md` for how to start.

## 2. Frontend (default port 3001, frontend/.env PORT)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

- **200** (or 304, 2xx) → up.
- Failure → frontend not running. See `docs/QUICK_START.md`.

## 3. Summary

Per service: **up or not**; if not, one-line command to start.
