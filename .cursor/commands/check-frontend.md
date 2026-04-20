# Check frontend health

**Host CRA** (default port **3002**):

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3002
```

**Docker Compose UI** (contract verification port **3001**):

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

- **200** or **304** (2xx) → running for that target.
- Host dev server: `cd frontend && npm start` (port 3002). Do not bind **3001** on the host — use Docker for `http://localhost:3001` (see `docs/contract.md`).
