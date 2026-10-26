# Changelog

## Unreleased

- Add the normal `android.permission.ACCESS_NETWORK_STATE` manifest permission for automatic WRIMP delivery resume; no runtime permission prompt is needed.
- Recover unreadable WRIMP queue files without crashing initialization, accept future JSON fields, and bound pending queues to 24 hours / 512 openings per API key and section.
- Drop permanent WRIMP delivery failures and release their local protection without acknowledging a view; retain the original 60-second expiry for legacy/snackbar delivery.
- Preserve counted modals across background/resume; close unconfirmed windows through normal close callbacks without creating an impression. Check sheet placement after window frames even when its cached content does not redraw.
- Honor `OPEN_STEP.closeOnClick` on both successful and skipped preparation. Modal tracking deliberately uses `VISIBLE_IMPRESSION` URLs even when `onVisibleImpression.action` names a different action; legacy/nonmodal selection is unchanged.
- Durable queue loading and commits use synchronous private-storage I/O to guarantee persistence before send/close; applications with strict main-thread disk policies must account for this.

- Track modal, bottom sheet and fullscreen visible impressions on the first drawn content frame while the app is active, without a minimum viewing duration; explicit fast dismissal and step transitions share one impression per opening.
- Persist modal WRIMP URLs and original user context before sending, retain campaign protection until HTTP 2xx, and resume transient failures after connectivity returns or SDK restart. Inline, product and snackbar visibility thresholds are unchanged.

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
