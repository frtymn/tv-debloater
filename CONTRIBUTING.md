# Contributing

Thanks for helping. Bug reports, package-safety reports and pull requests are welcome.
For big changes, open an issue first.

## Package database

The package safety database is `db/packages.json`. It is the only source:

- The APK bundles a byte-identical copy (Gradle copies it at build time, and CI compares the copy inside the APK).
- The app can also download newer versions from the `db` branch without a new APK release. See [db/README.md](db/README.md).

Each entry has:

- a package name and a level (`SAFE`, `CAUTION` or `PROTECTED`);
- a short English and Turkish label and description;
- sources (https links);
- optional `verifiedOn` (model, Android version, date), `devices` and `notes`.

The format is described in `db/packages.schema.json`.
`python3 tools/db_build.py check` validates it, and CI runs the same check on every push and pull request.

Rules:

- **SAFE needs evidence.** Every SAFE entry needs at least 2 public sources, or 1 source plus a `verifiedOn` record from a real device.
  Debloat lists for the same device model count. "It worked for me" alone does not.
- **When in doubt, use CAUTION.** Unknown packages are already shown as CAUTION.
- **PROTECTED wins.** The hard-coded guard in `core/ProtectedGuard.kt` overrides the database, bundled or downloaded.
  The check refuses any entry that marks a guarded package as SAFE or CAUTION.
  Never move a package out of PROTECTED without a strong reason and a source.
- `aggressive: true` is only allowed on CAUTION entries.

To propose a change without writing code, open a **Package safety report** issue.
To tell us how a package behaved on your TV, use **Report result** next to the package in the app, or open a **Package result report** issue.

### Reviewing result reports (maintainers)

1. Open issues with the `package-result` label.
2. Check that the report names the package, the device model and Android version, and says what happened after a reboot.
3. One clean report on a real device can become a `verifiedOn` record.
   A report of something breaking moves the package to CAUTION (or PROTECTED for core functions) and goes into `notes`.
4. Edit `db/packages.json`, for example in the GitHub web editor, and let CI check it.

### Publishing a database update (maintainers, works from a phone)

1. Make sure the change is on `main` and the **Package database** workflow is green.
2. Open **Actions > Package database > Run workflow**, choose `main`, set **publish** to `true` and run it.
3. The workflow raises `dbVersion`, writes `db/packages.json` and `db/manifest.json` (with size and SHA-256) to the `db` branch, and commits the new `dbVersion` back to `main`.
4. Apps with automatic updates turned on pick it up within 6 hours. You can also tap **Check for database updates** in Settings.

A database update never changes anything on a TV. It only changes the labels the app shows.

## Code changes

1. Fork the repository and create a branch.
2. Keep changes small and focused.
3. Run the unit tests: `./gradlew testDebugUnitTest`.
4. Run the repository checks: `python3 tools/repo_checks.py`. They check README images and links, that both string files have the same keys, that every permission is explained in the READMEs, and the package database (`tools/db_build.py check`).
5. Open a pull request and fill in the template.

GitHub Actions builds the APK and runs the tests on every push and pull request.
You do not need a computer: you can edit files and open pull requests in the GitHub web interface.

## Releases

Before every tag, the maintainer runs the full audit in [docs/AUDIT.md](docs/AUDIT.md) and adds an entry there.
A tag `vX.Y.Z` needs `docs/release-notes/vX.Y.Z.md`; CI refuses the release if the tag, the APK version and the newest release notes do not match.

## What we will not accept

- Features that touch anything other than the package state of the connected device:
  no root tricks, flashing, bootloader changes, or deleting user data.
- Ads, analytics or trackers.
- Network access beyond the local TV, except the package-database check against `raw.githubusercontent.com`.

By contributing, you agree that your contribution is licensed under the Apache License 2.0
and that you follow the [Code of Conduct](CODE_OF_CONDUCT.md).
