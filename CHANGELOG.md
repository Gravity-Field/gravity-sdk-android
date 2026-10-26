# Changelog

## Unreleased

- Track modal, bottom sheet and fullscreen visible impressions on the first drawn content frame while the app is active, without a minimum viewing duration; explicit fast dismissal and step transitions share one impression per opening. Inline, product and snackbar visibility thresholds are unchanged.
- Set local protection immediately in memory, then persist modal WRIMP URLs and campaign/user protection identity on a serial IO dispatcher before sending. Queue loading, fsync, atomic rename and batched eviction run off the main thread.
- Protect a shown campaign until HTTP 2xx or permanent drop, for at most 24 hours from the show across restarts. Retain and retry the event independently for up to 7 days; reaching 24 hours does not drop it. Queues hold at most 512 openings per API key and section.
- Resume transient failures on connectivity restoration and SDK restart; reinitialization transfers pending work and wakeups. Late responses cannot release a newer opening after the old protection expires. Permanent 4xx/invalid URLs release protection without acknowledging a view; legacy/snackbar retain their 60-second safety expiry.
- Persist only WRIMP delivery metadata and protection identity; omit unused User snapshots and host attributes, and scrub the legacy user field on queue load.
- Recover unreadable WRIMP files without crashing initialization, accept future JSON fields, and clean sibling files only after the 7-day delivery TTL. Users without either custom ID or backend UID do not share anonymous pending protection.
- Preserve counted modals across background/resume; abandon unconfirmed windows without impression or close analytics. Bound the cached-layer animation probe to 120 frames; subsequent real draws or focus changes can still confirm visibility.
- Honor `OPEN_STEP.closeOnClick` on both successful and skipped preparation. Modal tracking deliberately uses `VISIBLE_IMPRESSION` URLs even when `onVisibleImpression.action` names a different action; legacy/nonmodal selection is unchanged.
- Add the normal `android.permission.ACCESS_NETWORK_STATE` manifest permission for automatic WRIMP delivery resume; no runtime permission prompt is needed.

## 1.1.0

- Send content requests (`getContentBySelector`, `getContentByCampaignId`, `getContentByGroupSelector`) on behalf of the user set via `setUser`
- Render button borders from outlineColor and preserve disabled/pressed styling.
- Fix parsing of `#RRGGBB` colors; `#RRGGBBAA` keeps alpha in the last pair. Invalid colors give no color instead of a wrong one
- Prevent duplicate automatic in-app presentations while a campaign is being prepared, displayed, or awaiting visible-impression acknowledgement.
- Retry transient visible-impression delivery failures; acknowledge visibility only after successful delivery.
- Combine asynchronous preparation with presentation reservations, rejecting stale screens, users, cancelled tasks, and presentation locks before mounting UI.
- Load demo credentials from local configuration instead of committing them.
- Add `dismissInApp(contentId, completion)` for targeted dismissal from the existing ProductViewBuilder; navigate after dismissal and track close once.
- Add optional asynchronous in-app preparation, deadline and per-content skip.
- Preserve original product slots and tracking when filtering review prompts.
- Revalidate presentation after preparation and invalidate pending work on user changes or presentation lock.
- Add a purchase-review integration example and regression tests.

## 1.0.4

* Bump Compose BOM to 2024.09.00 to fix ModalBottomSheet crash
* Fix padding and margin in GravityText
* Fix GravityImage layout
* Fix ProductView layout
* Fix ItemsRow height condition

## 1.0.3

* Resolve SDK version from a string resource at runtime to avoid a stale version being baked into the published AAR

## 1.0.2

* Clear presentation lock listener on `dispose`

## 1.0.1

* Add presentation lock to suppress campaign display

## 1.0.0

* Initial release
