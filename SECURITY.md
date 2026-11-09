# Security policy

## Supported versions

Only the latest release gets fixes. TV Debloater is beta software.

## Reporting a vulnerability

Please report security problems privately. Do not open a public issue.

1. Open the repository's **Security** tab.
2. Click **Report a vulnerability**.
3. Describe the problem, the app version, and the steps to reproduce it.

This creates a private GitHub security advisory that only the maintainer can see.
You should get a first answer within 7 days.

## Scope

In scope:

- The app sending a command for a package it should refuse (for example a protected package).
- Commands that differ from what the confirmation dialog showed.
- The app talking to anything other than the TV address you entered (or, during "Scan network", the ADB port of addresses in your own Wi-Fi subnet).
  The only internet access is the package-database check, a plain HTTPS GET to `raw.githubusercontent.com`. Any other host, plain http, or a followed redirect is in scope.
- A downloaded package database that makes a guarded package selectable, gets past the size, hash or validation checks, or replaces the cached copy with a broken one.
- A database update that sends any command to the TV.
- Leaks of the app's ADB private key (stored in app-private storage).

Out of scope:

- A package in the safety database turning out to be needed on your firmware.
  This is a safety-database problem, not a security issue.
  Please use the "Package safety report" or "Package result report" issue form instead.
- Wrong labels in a published database update that the guard still blocks correctly.
