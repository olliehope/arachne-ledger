# Publishing Arachne Ledger

The repository is prepared for GitHub. Nothing has been uploaded or published automatically from this workspace.

Use GitHub for the source, issues and downloadable releases. Modrinth can provide a launcher listing, subject to its project rules and review.

## Before choosing Modrinth

This project has been developed substantially through AI-generated code. Modrinth's current policy excludes projects created primarily through prompting and testing from public listings, requires the appropriate AI disclosure, and says such projects can usually be unlisted. Disclosure alone does not establish public-listing eligibility. Based on this project's development so far, start with GitHub and ask Modrinth about an unlisted project if desired. [Official policy](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai)

## 1. Put the project on GitHub

The repository root is the directory containing `build.gradle`, `src`, `.github` and this README, **not** the surrounding workspace containing `work` and `outputs`.

Create an empty GitHub repository named `arachne-ledger`. Keep GitHub's options to add a README, license and gitignore disabled, because these files already exist here. Choose public if you want others to read the source.

From PowerShell in the project directory:

```powershell
# Only needed when starting from the downloaded source ZIP rather than the prepared local repository.
git init -b main

git add .
git update-index --chmod=+x gradlew
git commit -m "Prepare Arachne Ledger 1.8"
git remote add origin https://github.com/YOUR_USERNAME/arachne-ledger.git
git push -u origin main
```

Replace `YOUR_USERNAME` with your actual GitHub account. If Git asks for an author identity, configure your own name and email; a GitHub noreply email is suitable if you want to keep your email private. Authenticate through GitHub's normal login flow rather than putting a token in the remote URL.

GitHub Desktop is also suitable: add the local repository, commit its files, then use **Publish repository**. The release tags below can still be created in PowerShell.

Once a real URL exists, add the source and issue URLs under `contact.sources` and `contact.issues` in `src/main/resources/fabric.mod.json`. Keep `LICENSE` in the repository and release archive. The current license is MIT.

## 2. Let GitHub check changes

The **Build and check** workflow runs on pushes to `main`, pull requests, and manual runs. It installs Java 25, builds on Windows and Linux, runs every regression suite, checks release metadata, and retains downloadable build artifacts for 14 days.

Its Actions token has read-only repository access. It does not need any manually created secret. Check the Actions tab after the first push; these hosted workflows have been prepared locally but cannot be exercised until the repository exists on GitHub.

Protect `main` with pull requests and successful `Build and check` jobs when the repository starts accepting contributions. Update pinned GitHub Actions through the included monthly Dependabot configuration.

## 3. Prepare a version locally

For each release:

1. Change `version` in `gradle.properties`. The release checks accept versions such as `1.8` and `1.8.1`; preserve the exact chosen string in filenames, metadata, and tags. Use a patch such as `1.8.1` for fixes, a minor version such as `1.9` for added features, and a major version for a deliberate breaking change. Keep save migration and backups even across major releases.
2. Add a matching section such as `## [1.8.1]` to `CHANGELOG.md`. Describe completed changes and any migration or known limitation.
3. Update the README's install filename/version and any changed behavior. Dependency versions also live in `gradle.properties`; advertise only Minecraft versions that were actually built and tested.
4. Run the release task with JDK 25:

```powershell
.\gradlew.bat clean prepareRelease
```

On Linux/macOS, use `bash ./gradlew clean prepareRelease`. The first build downloads its dependencies. `--offline` is only useful after they are cached.

The `build/release` directory contains:

| File | Purpose |
| --- | --- |
| `arachne-ledger-<version>.jar` | The installable client mod |
| `arachne-ledger-<version>-source.zip` | Complete source, Gradle wrapper, documentation and workflows |
| `notes.md` | Release notes extracted from the matching changelog section |
| `SHA256SUMS` | Checksums for the two downloadable archives |

The task checks the JAR's version, Minecraft target and client-only metadata. It rejects bundled game classes and the local preview fixture. Local game data, logs, settings, credentials and build caches are excluded from the source archive.

Before a public stable release, test a normal farming session in Hypixel: Sanctuary entry/exit, your own placements, another player's completed summon, spawn/AFK timing, qualifying and skipped kills, rewards versus inventory, world changes, and an actual price refresh. Compare marked purse gains against Scavenger income and menu exclusions, and check NPC/salvage values without rewriting earlier entries. For 1.8, verify both Bazaar modes/manual overrides and graph options across scopes and HUD/dashboard views, including text labels, known-spawn markers, and projection warmup. Local checks do not replace this. See [CONTRIBUTING](../CONTRIBUTING.md) for the acceptance scenarios.

## 4. Create the GitHub release

After committing the release changes and pushing `main`, create and push the matching tag. For this prepared version:

```powershell
git tag -a v1.8 -m "Arachne Ledger 1.8"
git push origin v1.8
```

The **Prepare GitHub release** workflow rebuilds and checks the tagged commit. The tag must equal `v` plus the exact version in `gradle.properties`; `v1.8.1` or `v1.8.0` against a `1.8` build fails before any release is created.

When successful, it creates a **draft prerelease** with the installable JAR, full source ZIP, checksums and changelog notes. Open GitHub's Releases page, inspect the draft, and publish it when ready. Keep the prerelease label for the initial beta; remove it only when you intend a stable release. A tag alone does not publish this draft. This uses GitHub's built-in Actions token with write access restricted to the release job. [GitHub release documentation](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

If a build fails, fix the underlying issue and use a new version/tag. If assets were uploaded before a transient failure, inspect the existing draft before rerunning: the workflow deliberately does not overwrite an existing release. Do not move tags that users have already downloaded.

## 5. Add a Modrinth project if eligible

Resolve the listing eligibility described above first. Create a **mod** project, choose an available slug, set its source and issue links to GitHub, and use the MIT license already supplied with the source. Explain that this is an independent Hypixel SkyBlock client tracker, with estimated item values and optional public Bazaar requests. It does not collect analytics or upload your ledger.

For a version, use these settings:

| Setting | Value for this build |
| --- | --- |
| Primary file | `arachne-ledger-1.8.jar` |
| Version number | `1.8` |
| Release channel | Beta until live acceptance testing is complete |
| Loader | Fabric |
| Game version | Minecraft Java `26.1.2` |
| Environment | Client only; no server installation |
| Required dependency | Fabric API |
| Description requirements | Java 25 and Fabric Loader 0.19.5 or newer |
| Changelog | The corresponding `build/release/notes.md` text |

Use the same JAR as the GitHub release. Keep the full source ZIP on GitHub and link the repository; it is not the installable mod. Modrinth supports a dedicated sources JAR as an optional additional file. [Version metadata](https://docs.modrinth.com/api/operations/createversion/), [additional files](https://support.modrinth.com/en/articles/8793363-additional-files)

Use actual screenshots of the mod and clearly label any fixture/sample values. A project icon and final public screenshots have not been created for this release preparation. Complete the site's disclosure and review process; review time is variable. [Review information](https://support.modrinth.com/en/articles/8793355-project-review-times)

The first upload is manual. After a project exists and its visibility is resolved, an optional Modrinth publishing job can use a scoped publishing token stored in GitHub Actions secrets. That future job should publish only tagged builds, declare Fabric API as required, refuse duplicate versions, and never expose a token to pull requests. No Modrinth token or project ID is needed for the current GitHub workflow.

## Release status

Version 1.8's automated checks and client-verification status are recorded in the README. The local Java price service successfully refreshed both Bazaar caches; GitHub-hosted execution and live Hypixel gameplay remain separate checks. Existing entries retain their recorded values and saved manual overrides; Bazaar mode changes affect future values unless the session is explicitly repriced, and graph preferences change display only. No external repository or release has been created by the preparation work.
