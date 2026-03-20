# Frontend deploy module (this folder)

- **Purpose**: Static UI + JDK-only HTTP server — **not** `npm start` (no dev server, no CRA proxy).
- **Expected files after packaging**:
  - `logmng-static-server-1.0.0.jar` (gitignored)
  - `www/` — production build (`index.html`, `static/`, …); contents gitignored except `www/.gitkeep`
- **Create them**: from repo root, `./scripts/package-airgap-bin.sh`
- **Run**: `./run.sh` (default `PORT=3001`)
