# Repository audit log

A full audit runs after every change and before every tag. Newest entry first.
Keep this file under 80 lines: summarize older entries into one line each.

Checklist: versions, both READMEs, images, strings, docs and templates, permissions,
package database, dependencies and Actions, tests, secrets, leftovers, repo metadata.
`tools/repo_checks.py` (CI job `repo-checks`) automates the README, strings, permission,
database and tag/version checks.

## 2026-11-09 · main after v0.1.0 (no new tag)

Checked: everything on the list. Versions (tag v0.1.0 = APK versionName 0.1.0, Settings shows
BuildConfig.VERSION_NAME, dev builds are 0.1.0-dev.N), README.md/README.tr.md (13 images each),
133 string keys, SECURITY/CONTRIBUTING/templates, manifest, packages.json (98 entries, all with
sources, all 5 source URLs answer 200), Actions versions, gitleaks on the full history, .gitignore.

Fixed:
- Build was red on the last two commits: the gitleaks download got an HTTP 504. The download now retries.
- Removed the unused `ACCESS_WIFI_STATE` permission (nothing in the code uses WifiManager).
- Privacy claim was too narrow: "only talks to the TV address you enter" ignored the subnet scan.
  Fixed in both READMEs, the in-app About text (EN/TR) and SECURITY.md.
- Added "Privacy and permissions" and "Updates" sections to both READMEs.
- Turkish "Undo batch" button said "Tümünü geri al" (undo all). Now "İşlemi geri al"; README.tr updated.
- Troubleshooting now uses the real button name "Scan network for port 5555" in both languages.
- Removed unused strings `ok`, `close`, `next`; `app_name` marked translatable="false".
- Added `tools/repo_checks.py` and the `repo-checks` CI job; the build job now also checks that
  the APK versionName matches the tag. CONTRIBUTING and the PR template mention both.
- Re-rendered the screenshots, hero and social preview images (Turkish history screen changed).

Left open:
- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21 and Compose BOM 2024.12.01 are behind current releases
  (Actions shows "Gradle 8.9 is out of date"). The build is green and Roborazzi is pinned to this
  Kotlin version, so an upgrade needs its own change with full screenshot and e2e runs.
- GitHub Actions: `ubuntu-latest` moves to Ubuntu 26 (notice in the runs). Nothing to change yet.
- Old auto releases v1.0.2 to v1.0.8 still exist. The maintainer deletes them by hand.
- The released v0.1.0 APK still declares ACCESS_WIFI_STATE; the fix ships with the next tag.
- Not tested on a physical Mi TV Stick.

## 2026-11-09 · v0.1.0

First public-release preparation: README rewrite, screenshots, community files, gitleaks scan
(no findings), first release v0.1.0.
