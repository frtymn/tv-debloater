# TV Debloater package database

This branch holds the package safety database that the TV Debloater app downloads.
It changes without a new APK release. Do not edit this branch by hand: the **Package database** workflow on `main` publishes it.

## Files

- `db/manifest.json`: small index the app reads first. It has `schemaVersion`, `dbVersion`, `publishedAt`, `minAppVersion`, the file name, its `size` in bytes and its `sha256`.
- `db/packages.json`: the database itself, the same format as `db/packages.json` on `main`.
- `db/packages.schema.json`: JSON Schema of the database.

## How the app uses it

1. At most every 6 hours (if automatic updates are on), or when you tap **Check for database updates**, the app fetches `manifest.json` over HTTPS from `raw.githubusercontent.com` only, with the stored ETag.
2. If `dbVersion` is newer and `schemaVersion` and `minAppVersion` fit the app, it downloads `packages.json` and checks the size and SHA-256.
3. It validates every field and rejects the whole file if anything is malformed. Entries that conflict with the hard-coded guard are dropped.
4. It stores the new file atomically and keeps the previous one. The bundled copy is the last fallback.

A database update only changes the labels in the app. It never sends anything to the TV.

## Publishing

On `main`: edit `db/packages.json`, wait for the checks to go green, then run **Actions > Package database > Run workflow** with **publish** set to `true`.
See [CONTRIBUTING.md](https://github.com/frtymn/tv-debloater/blob/main/CONTRIBUTING.md#package-database) for the review rules and how to handle "Package result report" issues.
