# Start DB (PostgreSQL)

From **project root (dev)** run:

```bash
./scripts/dev-services.sh db start
```

- Homebrew `postgresql@16`, port 5432. If already running, "already running". If `brew` is missing, script may suggest manual steps (see `backend/DB_SETUP_GUIDE.md`).
