# Android SDK 1.1.0 release

This branch combines EGO-247 (review preparation and targeted dismissal) and EGO-248 (automatic presentation deduplication and confirmed visible-impression delivery). It extends the existing initialization API without requiring existing clients to enable preparation. Backend endpoints and payload schemas are unchanged.

## Integrated changes (2026-10-23)

This aggregate MR includes !3 (review preparation and dismissal), !4 (presentation reservations and visible impressions), !5 (button outline and state styling), !6 (RGB/RGBA parsing), and !9 (the EGO-246 user identity prerequisite and wire tests). The exact !3 head includes !5 and !6. Merge this MR once; the included source MRs do not need separate merges. MR !8 (custom event properties), !10 (headless raw JSON/variables), and the Kotlin 1.9.25 demo branch remain separate.

At inspection, GitLab marked !3, !5, and !6 merged, but `main` pointed to `d60aadf67afd85dc8d87a9f56bd3f0b3b1ee2dbc` and did not contain those heads. This branch restores them by ancestry. Before publication, confirm why the merged commits are absent from `main` and ensure the final merge persists. The cause of the branch discrepancy has not been established.

Conflict resolution retains the EGO-248 synchronized user snapshot instead of introducing a second identity lock or reading the latest user for an earlier automatic request. Skipped/failed preparation releases the reservation; successful preparation still rechecks screen, user revision and presentation lock. User wire tests reset through SDK initialization rather than assigning the now read-only internal user property. Backend request and event contracts are unchanged.

## Validation

- The demo APK builds with `:app:assembleDebug`.
- All 50 SDK JVM regression tests pass with `:gravity_sdk:testDebugUnitTest`.
- An independent Android project compiles against the packaged 1.1.0 AAR, including the existing initialization call, the preparation overload, and targeted dismissal.
- All 16 versioned Maven artifact checksums are verified. Packaged button/color/SDK sources match the final source tree. The 1.1.0 package is regenerated with the integrated fixes.
- The instrumentation test APK builds with `:gravity_sdk:assembleDebugAndroidTest`. All five `CustomUserWireTest` cases pass on an Android API 36 emulator through `adb shell am instrument` (all choose variants, switching users, anonymous session preservation, visit/event identity). The library test APK targets API 21 and was installed on the test emulator with `--bypass-low-target-sdk-block`.
- Gradle connected-test execution in offline mode lacks the UTP host plugin; direct AndroidJUnitRunner execution above uses the same compiled tests. Full campaign animation/device UI QA remains a release check.
- The 1.1.0 Maven package builds successfully with `:gravity_sdk:publishReleasePublicationToLocalRepository`; the output belongs in `gravity_sdk/repository/ai/gravityfield/gravity-sdk/1.1.0`.

The Maven publication contains the library only. Demo `BuildConfig` settings are not part of the SDK AAR.

## Maintainer publication steps

1. Review and merge this aggregate release MR into `main`. It includes both feature MR heads; there is no need to merge the original feature branches separately afterward. The current agent account is Developer and cannot merge the protected `main` branch.
2. After validation succeeds, create an annotated `1.1.0` tag on the merged `main` commit. Never reuse or move a published version tag.
3. Verify that the public repository `https://github.com/Gravity-Field/gravity-sdk-android.git` contains the merged code and the `1.1.0` tag. Confirm the exact merged revision and tag in both remotes; synchronization configuration and its publication trigger are not visible to the current account.
4. Verify the client installation using the published version, then distribute release notes. Client app teams must update the dependency, rebuild, and ship an app release.

To tag the reviewed merge commit from a checkout with `origin` pointing to GitLab:

```sh
git fetch origin main
git tag -a 1.1.0 origin/main -m "Gravity SDK 1.1.0"
git push origin refs/tags/1.1.0
```

Do not tag this unmerged branch as the official release. If `main` advances with unrelated changes, validate the intended release commit before tagging.

## Client integration

Existing integrations can keep using the existing `initialize` signature. EGO-248 behavior applies after upgrading the SDK. To enable reviews, implement the client's batch eligibility check, connect `inAppPreparation` and the review product builder, route the action to the client's review screen, and update the local review status only after a successful submission. Demo status storage is an example, not a server review API.

Demo credentials are configured locally. They are not defaults for client applications and are not needed to build the SDK library. Rotate any formerly committed active API key: rewritten branch history does not erase old MR snapshots, other branches, or existing clones.

## Rollback

Client teams can pin the previous published version (`1.0.4`) and release their application again. Preserve published tags and Maven artifacts. For a source rollback, revert the release changes through a new MR and publish a new version instead of replacing `1.1.0`. No backend rollout or data migration is required by these SDK changes.
