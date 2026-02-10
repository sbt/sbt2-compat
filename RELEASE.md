# Release Procedure

## Prerequisites

The following secrets must be configured in the GitHub repository (or organization):

- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` -- Sonatype OSSRH credentials
- `PGP_SECRET` / `PGP_PASSPHRASE` -- base64-encoded PGP key and its passphrase

These are used by the `Release` GitHub Actions workflow to sign and publish artifacts.

## How to release

1. Ensure `main` is in the state you want to release (all PRs merged, CI green).

2. Create and push a git tag with the format `vX.Y.Z`:

       git tag v0.1.0
       git push upstream v0.1.0

3. The `Release` GitHub Actions workflow triggers automatically on the tag push.
   It runs `sbt ci-release` which:
   - Derives the version from the git tag (via sbt-dynver)
   - Cross-compiles for Scala 2.12 (sbt 1) and Scala 3 (sbt 2)
   - Signs artifacts with PGP
   - Publishes to Sonatype and promotes to Maven Central

4. The workflow also publishes a GitHub Release with auto-generated notes
   (assembled by release-drafter from merged PR titles).

5. Artifacts typically appear on Maven Central within 10--30 minutes after
   the workflow completes.

## Tag format

- Release tags: `v0.1.0`, `v1.0.0`, `v2.3.1` (semver prefixed with `v`)
- sbt-dynver derives the version by stripping the `v` prefix, so tag `v0.1.0`
  produces version `0.1.0`.

## Release drafter

- As PRs are merged into `main`, the Release Drafter workflow automatically
  maintains a draft GitHub Release with categorized change notes.
- Label PRs with `feature`/`enhancement`, `bug`/`fix`, or `docs` to
  categorize them in the release notes.
- Label PRs with `major`, `minor`, or `patch` to influence the suggested
  next version (defaults to `patch`).

## Snapshot versions

- Commits on `main` that are not tagged get a snapshot-style version from
  sbt-dynver (e.g. `0.1.0+3-abcdef12-SNAPSHOT`). These are not published
  automatically; use `sbt +publishLocal` for local testing.
