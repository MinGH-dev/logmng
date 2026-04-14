# Backend deploy module (this folder)

- **Purpose**: Air-gapped / production-style run — **not** for day-to-day dev (`backend/target` + `scripts/dev-services.sh` stay the dev path).
- **Expected file after packaging**: `logmng-backend-1.0.2.jar` (Spring Boot fat JAR, gitignored).
- **Create it**: from repo root, `./scripts/package-airgap-bin.sh`
- **Run**: `./run.sh` (same directory as the JAR).
