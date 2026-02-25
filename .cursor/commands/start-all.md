# Start DB, backend, and frontend

From **project root (dev)** run:

```bash
./scripts/dev-services.sh all start
```

Order: DB (PostgreSQL 5432) → backend (9200) → frontend (3001). DB uses Homebrew `postgresql@16`. Backend jar is built if missing.
