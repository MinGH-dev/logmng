# Start frontend

From **project root (dev)** run:

```bash
./scripts/dev-services.sh frontend start
```

- Port: **3002** by default (`FRONTEND_PORT` in `scripts/dev-services.sh`; matches `frontend/.env.development`). **3001** is reserved for Docker Compose UI — do not run host CRA on 3001. If already running, script reports "already running".
