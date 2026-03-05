# Moving PostgreSQL, Cursor, and Git to T7 External Storage

This guide describes how to place PostgreSQL data, Cursor application data, and Git repositories on the T7 external drive (`/Volumes/T7`).

---

## 1. Git — already on T7

This project workspace is **already under T7**: `/Volumes/T7/dev/logmng_frontend/dev`.

- **No move needed** for this repo.
- For other repos: clone or move them under e.g. `/Volumes/T7/dev/` or `/Volumes/T7/repos/`.
- Git binary (Xcode/Homebrew) stays on the Mac; only repo directories live on T7.

---

## 2. PostgreSQL — data directory on T7

Homebrew PostgreSQL keeps data in `/opt/homebrew/var/postgresql@16` (Apple Silicon). To use T7 instead:

### 2.1 Choose a data path on T7

Example: `/Volumes/T7/pgdata` (or `/Volumes/T7/var/postgresql@16`).

### 2.2 Stop PostgreSQL

```bash
brew services stop postgresql@16
```

### 2.3 Move existing data (if you want to keep current DBs)

```bash
# Create target directory
mkdir -p /Volumes/T7/pgdata
chmod 700 /Volumes/T7/pgdata

# Copy existing data (replace with your actual Homebrew var path if different)
cp -a /opt/homebrew/var/postgresql@16/* /Volumes/T7/pgdata/

# Optional: keep a backup of the original, then remove to free space
# sudo rm -rf /opt/homebrew/var/postgresql@16/*
```

If you prefer a **fresh** database on T7 (no existing data to keep):

```bash
mkdir -p /Volumes/T7/pgdata
chmod 700 /Volumes/T7/pgdata
/opt/homebrew/opt/postgresql@16/bin/initdb -D /Volumes/T7/pgdata
```

### 2.4 Run PostgreSQL with data on T7

**Option A — Override data directory when starting (no Homebrew services)**

Create a small wrapper or use a launch agent that runs:

```bash
/opt/homebrew/opt/postgresql@16/bin/pg_ctl -D /Volumes/T7/pgdata -l /Volumes/T7/pgdata/logfile start
```

Stop with:

```bash
/opt/homebrew/opt/postgresql@16/bin/pg_ctl -D /Volumes/T7/pgdata stop
```

**Option B — Keep using `brew services` with custom data dir**

Override the plist that Homebrew uses so the process uses `-D /Volumes/T7/pgdata`:

1. Find the plist:  
   `ls /opt/homebrew/opt/postgresql@16/*.plist` or  
   `~/Library/LaunchAgents/homebrew.mxcl.postgresql@16.plist`
2. Copy it to `~/Library/LaunchAgents/` if it’s not there.
3. Edit the plist: in the `<array>` of program arguments, ensure the first argument after the postgres binary is `-D` and the next is `/Volumes/T7/pgdata` (replace the default `-D` path).
4. Reload and start:

   ```bash
   launchctl unload ~/Library/LaunchAgents/homebrew.mxcl.postgresql@16.plist
   launchctl load ~/Library/LaunchAgents/homebrew.mxcl.postgresql@16.plist
   ```

**Option C — Symlink (simplest, but entire DB lives on T7)**

```bash
brew services stop postgresql@16
# Backup then remove original data
mv /opt/homebrew/var/postgresql@16 /opt/homebrew/var/postgresql@16.bak
# Point to T7
ln -s /Volumes/T7/pgdata /opt/homebrew/var/postgresql@16
# If fresh: initdb in /Volumes/T7/pgdata first; if migrated: you already copied data into /Volumes/T7/pgdata
brew services start postgresql@16
```

### 2.5 After first run with T7

- If you used a **fresh** `initdb`, create DB and user again (see `backend/DB_SETUP_GUIDE.md`) and run `backend/src/main/resources/db/setup.sh` (with `DB_SUPERUSER` set if needed).
- Ensure T7 is mounted before starting PostgreSQL; otherwise the data directory will be missing and Postgres will not start.

---

## 3. Cursor — application data on T7

Cursor stores user data under `~/Library/Application Support/Cursor`. To move it to T7:

### 3.1 Quit Cursor completely

Close all windows and ensure Cursor is not running (check Activity Monitor if needed).

### 3.2 Copy data to T7

```bash
mkdir -p /Volumes/T7/cursor-data
cp -a ~/Library/Application\ Support/Cursor/* /Volumes/T7/cursor-data/
```

### 3.3 Replace with a symlink

```bash
# Backup and remove original
mv ~/Library/Application\ Support/Cursor ~/Library/Application\ Support/Cursor.bak

# Symlink to T7
ln -s /Volumes/T7/cursor-data ~/Library/Application\ Support/Cursor
```

### 3.4 Notes

- **T7 must be mounted** before opening Cursor, or the app may not find settings/extensions.
- Updates and cache will be written to T7. If you use the drive on another Mac, use the same macOS username or adjust permissions.

---

## 4. Checklist

| Item            | Location / Action                                      |
|-----------------|--------------------------------------------------------|
| Git (this repo) | Already on T7: `/Volumes/T7/dev/logmng_frontend/dev`   |
| PostgreSQL data | Move or symlink to e.g. `/Volumes/T7/pgdata`           |
| Cursor data     | Copy to `/Volumes/T7/cursor-data` and symlink          |

---

## 5. Optional: start PostgreSQL only when T7 is mounted

If you use a custom script or LaunchAgent to start Postgres with `-D /Volumes/T7/pgdata`, run that script only after T7 is mounted (e.g. from a login item or a small script that checks `/Volumes/T7` and then starts `pg_ctl`). Do not rely on `brew services start postgresql@16` at login if the default data dir is on T7 and the drive is not yet mounted.
