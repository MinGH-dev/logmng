# Frontend (Create React App)

## Local UI verification

The canonical browser URL for full-stack verification matches `docs/contract.md`: **http://localhost:3001** (Docker static server serving the built app against the API).

## Development server

Running `npm start` in this folder starts the CRA dev server on **port 3002** (via `PORT=3002` in the `start` script). That avoids Create React App’s default **3000**, and reduces accidental clashes when Docker is already serving the UI on **3001**.

## `dev-services.sh` frontend

When you start the frontend via `scripts/dev-services.sh`, it sets `PORT` from **`FRONTEND_PORT`** (default **3002**, same as `npm start`). **Do not run host CRA on port 3001** — that port is reserved for the Docker Compose static bundle (`http://localhost:3001`). Override only if you know you are not using Docker on 3001.
