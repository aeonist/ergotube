# Security

ErgoTube holds a Google account session on the phone, so security reports matter here.

## Reporting

Please **do not open a public issue**. Report privately through [GitHub security advisories](https://github.com/aeonist/ergotube/security/advisories/new). Describe what an attacker could do and how to reproduce it. You will get an answer within a few days.

Never include your own cookies, tokens or account data in a report.

## What is in scope

- Account cookies leaving the phone for any host other than `youtube.com` and `googlevideo.com`.
- Cookies, tokens or personal data written to logs, backups or shared storage.
- Weaknesses in how the session is encrypted (Android Keystore) or rotated.
- Account actions that bypass the confirmation of careful mode.
- APKs on the Releases page that do not match the source or the project's signing key.

## Supported versions

Only the latest build on the [Releases](https://github.com/aeonist/ergotube/releases) page gets fixes.
