# Restart DB, frontend, and backend

From **project root (dev)** run:

```bash
./scripts/dev-services.sh all restart
```

Order: stop backend and frontend → stop DB → start DB → start backend → start frontend.
