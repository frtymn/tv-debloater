## What does this change?

## Why?

## Checklist

- [ ] `./gradlew testDebugUnitTest` and `python3 tools/repo_checks.py` pass (or the Actions build is green).
- [ ] README.md and README.tr.md, and both string files, are updated together.
- [ ] Every SAFE package has 2 sources, or 1 source plus `verifiedOn` (`python3 tools/db_build.py check` passes).
- [ ] No package removed from the protected list in `core/ProtectedGuard.kt`.
- [ ] No new permissions, ads or analytics, and no network access besides the TV and the package-database check.
- [ ] Tested on a device (model, Android version): 
