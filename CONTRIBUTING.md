# Contributing

Thanks for helping. Bug reports, package-safety reports and pull requests are welcome.
For big changes, open an issue first.

## Package-safety updates

The safety database is `app/src/main/assets/packages.json`.
It is generated from `tools/gen_packages.py`, so edit the Python file and regenerate:

```sh
python3 tools/gen_packages.py
```

Each entry has a package name, a level (`SAFE`, `CAUTION` or `PROTECTED`), a short English and Turkish description, and sources.

Rules:

- **SAFE needs evidence.** Link at least one public source where people disabled the package on a real device without problems.
  Debloat lists for the same device model count. "It worked for me" alone does not.
- **When in doubt, use CAUTION.** Unknown packages are already shown as CAUTION.
- **PROTECTED wins.** The hard-coded guard in `core/ProtectedGuard.kt` overrides the JSON.
  Never move a package out of PROTECTED without a strong reason and a source.
- Tell us the device, firmware and Android version you tested on.

To propose a change without writing code, open a **Package safety report** issue.

## Code changes

1. Fork the repository and create a branch.
2. Keep changes small and focused.
3. Run the unit tests: `./gradlew testDebugUnitTest`.
4. Run the repository checks: `python3 tools/repo_checks.py`. They check README images and links, that both string files have the same keys, and that every permission is explained in the READMEs.
5. Open a pull request and fill in the template.

GitHub Actions builds the APK and runs the tests on every push and pull request.
You do not need a computer: you can edit files and open pull requests in the GitHub web interface.

## Releases

Before every tag, the maintainer runs the full audit in [docs/AUDIT.md](docs/AUDIT.md) and adds an entry there.
A tag `vX.Y.Z` needs `docs/release-notes/vX.Y.Z.md`; CI refuses the release if the tag, the APK version and the newest release notes do not match.

## What we will not accept

- Features that touch anything other than the package state of the connected device:
  no root tricks, flashing, bootloader changes, or deleting user data.
- Ads, analytics, trackers, or network access beyond the local TV.

By contributing, you agree that your contribution is licensed under the Apache License 2.0
and that you follow the [Code of Conduct](CODE_OF_CONDUCT.md).
