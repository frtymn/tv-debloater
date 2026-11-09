# Repository audit log

A full audit runs after every change and before every tag. Newest entry first.
Keep this file under 80 lines: summarize older entries into one line each.

Checklist: versions, both READMEs, images, strings, docs and templates, permissions,
package database, dependencies and Actions, tests, secrets, leftovers, repo metadata.
`tools/repo_checks.py` (CI job `repo-checks`) automates the README, strings, permission,
database and tag/version checks.

## 2026-11-09 · v0.1.1

Checked: the full list. Tag v0.1.1 = VERSION_NAME = docs/release-notes/v0.1.1.md (newest);
dev builds are now 0.1.2-dev.N. Both READMEs (13 images each, same structure), 133 string keys,
SECURITY/CONTRIBUTING/templates, manifest (INTERNET, ACCESS_NETWORK_STATE), packages.json
(98 entries, sources answer 200), Actions (all on the latest major), gitleaks (no findings).

Fixed:
- Signing: the CI wrote the fixed key to ~/.android/debug.keystore, but Gradle ignored it and made a
  new key per build (certificate dates = build time). v0.1.0 has such a one-off key. Gradle now reads
  the key from DEBUG_KEYSTORE_FILE, and CI fails if the APK certificate is not the repository key.
- README "Updates" and the release notes no longer claim v0.1.0 shares the key; they explain the
  one-time reinstall (export history, uninstall, install, import).
- About text (EN/TR) used "Scan"/"Tara", which is not a button name; reworded.
- Added ManifestPermissionsTest: the permission set must match the README privacy section.
- Bug report template, build.yml comment and README examples point to v0.1.1.

Left open:
- Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01 still behind; build green,
  upgrade needs its own change (Roborazzi is pinned to this Kotlin).
- Old auto releases v1.0.2 to v1.0.8 still exist (maintainer deletes them by hand).
- Not tested on a physical Mi TV Stick.

## Older entries

- 2026-11-09, main after v0.1.0: fixed a red build (gitleaks download 504, now retried), removed the
  unused ACCESS_WIFI_STATE permission and strings ok/close/next, corrected the privacy wording
  (subnet scan), fixed the Turkish "Undo batch" label, added Privacy/Updates sections,
  tools/repo_checks.py and the repo-checks CI job.
- 2026-11-09, v0.1.0: public-release preparation, community files, gitleaks (no findings).
