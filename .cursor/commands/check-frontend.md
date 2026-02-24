# Check frontend health

Run the following to verify the frontend (default port 3001, PORT in frontend/.env) is up.

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

- **200** or **304** (2xx) → running.
- Empty or error → not running. Start: `cd dev/frontend && npm start` (see `docs/QUICK_START.md`).
