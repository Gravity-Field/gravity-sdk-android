# Changelog

## Unreleased

- Prevent duplicate presentations of an attached campaign after reservation expiry or visible-impression acknowledgement; allow showing again after dismissal and preserve step transitions.

- Replace the current modal, bottom-sheet, fullscreen or snackbar step with the next `open_step` step regardless of `closeOnClick`, as on Flutter, instead of stacking it; the replaced step reports no close, and the step that is finally closed reports exactly one.

- Keep appeared modal content mounted across backgrounding even when its visible-impression URL is absent or cannot be queued.

- Track one close after permanent host removal of appeared in-app overlays or native dialogs; keep removals before appearance silent and avoid counting restored windows as closed.

- Keep modal, bottom-sheet, fullscreen and snackbar content open across Activity recreation for configuration changes such as rotation, remounting the same presentation in the new Activity without new impression, visible-impression, product-visibility or close events; the campaign stays attached until its real close, which is tracked once.

- Send close tracking once when modal, bottom-sheet or fullscreen close controls share the configured onClose action, including appeared content without a visible-impression URL.
- Send close tracking once when modal, bottom-sheet or fullscreen close controls share the configured onClose action.
- Treat non-finite dimensions as absent when parsing size, padding, margin and corner radius, preventing invalid Compose layout.
- Treat missing campaign priority as zero in automatic presentation, matching NoShow ordering and preserving server order for ties.

- Return the chosen campaign’s non-negative `delayTime` in NoShow responses and load callbacks without waiting; parse decimal priority/delay values exactly with Unicode whitespace, normalize negative load-event constructor delays, and demonstrate app-owned delayed rendering.
- Return the chosen campaign’s non-negative `delayTime` in NoShow responses and load callbacks without waiting; keep raw JSON priority/delay numbers at double precision and parse decimal strings exactly with Unicode whitespace, normalize negative load-event constructor delays, and demonstrate app-owned delayed rendering.

- Keep HTTP debug logs to method, sanitized endpoint and status, without request/response bodies, header values or exception text; redact each complete free-text Authorization value line, including Proxy-Authorization and x-authorization, case-insensitively before length limits and duplicate fingerprinting, and sanitize bounded error-report URLs, including nested and generic-scheme URLs, escaped URLs, password-like ports, adjacent URLs and truncated credentials; malformed spaced userinfo is limited to one following token to preserve diagnostic prose. HTTP/parsing diagnostics omit response bodies and exception stacks. Backend reports admit at most ten attempts per rolling minute before identity or payload work, suppress in-flight and successfully delivered duplicates by sanitized text, and allow retries after failed sends within the remaining budget; successful deduplication lasts sixty seconds from completion. Reports use the current server UID or shared device ID, with server/custom/no-session precedence, without waiting for network session initialization.
- Treat unsuccessful HTTP content responses as failures in every choose API, without publishing their user identity or sending content-loaded callbacks. Successful empty responses remain valid.
- Reject unsuccessful `/visit` and `/event` responses before accepting server identity or processing campaigns; diagnostics keep only the method, HTTP status and endpoint.

- Add server identity APIs: `getUserId`, process-wide `setUserIdListener`, `resetUser`, and `restoreUserId`. Restore trims whitespace and rejects blank IDs; reset and restore clear the session and custom user. Storage failures are reported to the caller; reset keeps the in-memory UID and listener cleared even if removal fails.
- Share pending visible-impression protection among users with neither custom ID nor server UID: protect the campaign for 24 hours, while delivery remains queued for up to seven days. Legacy anonymous queue identities remain readable.
- Preserve the original identity of raised analytics across user changes, including cold starts, while rejecting obsolete content responses. Concurrent requests share session initialization; user reset preserves pending visible-impression delivery; offline visit/event delivery is not provided.
- Reload in-flight inline content for the current user while keeping displayed content. Temporary View detachment preserves the request; removed placements wait for attachment before retrying and release retained composition with the host lifecycle.
- Preserve `selector`, `group`, and `campaignId` as exact JSON strings in content requests, including numeric-looking and empty identifiers. Migration: pass identifiers without JSON pre-quoting; pre-quoted values such as `"\"slot\""` are now sent literally, and numeric-looking strings such as `"101"` are sent as strings.
- Add optional recommendation `rules` (`RtRule`, `RtRuleCondition`, `RtRuleArgument`) to `getContentBySelector`, `getContentByCampaignId`, `getContentByGroupSelector` and their WithDetails variants, to `GravityInlineCompose`, and via `setRules` on `GravityInlineView` and `GravityInlineListView`. Rules are sent in each request's `data[].rules` of `POST /v2/choose` in the Flutter SDK shape and omitted when null; existing calls are unchanged. Changing inline rules reloads the placement and never reuses content cached for another rules selection.
- Identity storage is configured in `initialize(context)`; the `multiplatform-settings-no-arg` dependency is removed. Before initialization, `getUserId` waits for Context, and reset/restore accept the in-memory identity immediately while awaiting queued persistence; storage failures propagate to the caller. Reading stored identity does not notify the listener; server responses and restore do. Reset notifies only when a previous UID existed or an identity was already published; before initialization, an unknown UID waits for storage before notifying. The example app demonstrates saving, resetting, and restoring a server UID.
- Preserve `isFetchContentOnTrack` and `staleContentTimeoutMillis` settings before and across SDK initialization; negative timeouts clamp to zero.
- Ignore unknown actions when matching content and button tracking events, so unrelated future events cannot be sent accidentally.
- Add suspend `trackViewNoShow` and `triggerEventNoShow`: send tracking, then return typed content and the original JSON from the highest-priority campaign with root content. Failed or empty candidates fall back to the next campaign. These APIs need no Activity and never open UI or send client impression events automatically; host options are forwarded unchanged. Server-side implicit impressions are controlled by the host’s `isImplicitImpression` option.
- NoShow honors the current fetch setting and stale-content timeout, rejects results after identity or SDK changes, and emits load callbacks and onLoad tracking only for the accepted result. Presentation locks do not block fetching. The example app demonstrates custom rendering, explicit impressions, and fetch error handling.
- Count snackbar impressions after actual appearance, persist qualified visible impressions through the modal WRIMP path, and avoid duplicate close tracking while preserving dismissal and user-change reservation handling. Attached snackbars keep counting across presentation locks or new screens, and recheck visibility when focus returns. Focus or lifecycle loss immediately restarts the uninterrupted visible-impression interval. Programmatic dismissal reports successful removal before appearance without impression or close events, and reports one close after appearance.
- Colour enabled button labels like iOS: `textStyle.color` when set, otherwise the primary label colour (black, white in dark mode) instead of Material's white onPrimary; a top-level `textColor` still does not apply to buttons.
- Avoid duplicate grid product visibility events after recomposition and column changes.
- Render a product row or grid with no products (`products` or `slots` absent, or `slots: []`; a container without a type is a row) as nothing, without height, margin, padding or background. Grids no longer keep a placeholder height while products are absent.
- Lay out product rows like iOS and Flutter: `size.height` is the outer box including padding, the background fills that box with margins outside it, and products are clipped to it instead of overlapping neighbouring elements. The example product card now fits the height it is given.
- Limit tall product grid sheets below the status bar without adding gaps to short sheets.
- Read `gridSettings` with legacy `gridColumns` fallback: 2 columns and spacing 8 by default, valid columns 1–12, invalid or negative spacing falls back to 8. Grid cards remain square regardless of `size`; only `padding` affects the grid, matching iOS and Flutter.
- Use the host product builder for grid cards; send visible impressions once per identified slot after at least half the card is visible in its clipped viewport.

## 1.2.0

- Add `getContentBySelectorWithDetails` and `getContentByCampaignIdWithDetails`: the model plus the raw JSON of the same response
- Add `CampaignContent.rawVariables` and key access on `Variables` for keys with no typed counterpart
- Add `customProps` and `eventTime` to all trigger events; `CustomEvent` also gets `cuid`, `cuidType` and `cart`
- Events without `eventTime` are sent with the time `triggerEvent` was called
- Unset optional fields of standard events (`currency`, `cart`, `hashedEmail`, `cuid`, `cuidType`) are omitted instead of sent as `null`
- Track modal, bottom sheet and fullscreen visible impressions on the first drawn content frame while the app is active, without a minimum viewing duration; explicit fast dismissal and step transitions share one impression per opening. Inline, product and snackbar visibility thresholds are unchanged.
- Set local protection immediately in memory, then persist modal visible-impression delivery URLs and campaign/user protection identity on a serial IO dispatcher before sending. Queue loading, fsync, atomic rename and batched eviction run off the main thread.
- Protect a shown campaign until HTTP 2xx or permanent drop, for at most 24 hours from the show across restarts. Retain and retry the event independently for up to 7 days; reaching 24 hours does not drop it. Queues hold at most 512 openings per API key and section.
- Resume transient failures on connectivity restoration and SDK restart; reinitialization transfers pending work and wakeups. Late responses cannot release a newer opening after the old protection expires. Permanent 4xx/invalid URLs release protection without acknowledging a view; legacy/snackbar retain their 60-second safety expiry.
- Persist only visible-impression delivery metadata and protection identity; omit unused User snapshots and host attributes, and scrub the legacy user field on queue load.
- Recover unreadable visible-impression delivery files without crashing initialization, accept future JSON fields, and clean sibling files only after the 7-day delivery TTL. Users without either custom ID or backend UID do not share anonymous pending protection.
- Preserve counted modals across background/resume; abandon unconfirmed windows without impression or close analytics. Bound the cached-layer animation probe to 120 frames; subsequent real draws or focus changes can still confirm visibility.
- Honor `OPEN_STEP.closeOnClick` on both successful and skipped preparation. Modal tracking deliberately uses `VISIBLE_IMPRESSION` URLs even when `onVisibleImpression.action` names a different action; legacy/nonmodal selection is unchanged.
- Add the normal `android.permission.ACCESS_NETWORK_STATE` manifest permission for automatic visible-impression delivery resume; no runtime permission prompt is needed.

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
