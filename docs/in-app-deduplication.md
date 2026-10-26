# In-app presentation and visible impression delivery (EGO-248, EGO-272)

Automatic `trackView` / `triggerEvent` presentation reserves a campaign before `/choose`.
`InAppPresentationGate` atomically covers content retrieval, delay, preparation, presentation,
and the gap before WRIMP acknowledgement. Requests capture screen, user and SDK generations;
stale responses cannot mount a new window. The public presentation lock remains independent.

For bottom sheets, modal/dialog and fullscreen, `visible_impression` means **the window content
actually appeared on screen**, without a minimum viewing duration. A content draw must intersect
the window while it has focus and its lifecycle is resumed. The callback runs after the draw
traversal. Creating a component, `/choose`, content loading, zero-size/off-screen content and
rendering only a scrim cannot count. Opening translation is checked through content bounds in
the window. `VisibilityDetector` and its existing 1-second/50% policy are unchanged for inline,
product cards and snackbar; snackbar does not use the new modal detector.

Each modal opening has one `ModalImpressionSession`, including all `OPEN_STEP` transitions.
Its detector and explicit close handlers share an atomic fired flag. Swipe, X, Back, dismiss/
“Not now” buttons and `dismissInApp` persist WRIMP before closing if a content frame has been
drawn, including when the detector's posted callback has not run yet. Recomposition, repeated
signals and later steps cannot create another impression for that opening. The next step is
prepared and mounted with the same session before dismissing the old step. Navigation-driven
removal and backgrounding never call this explicit-close fallback. Backgrounding removes the
modal; removal before appearance releases its reservation without WRIMP. A step replacement
keeps the reservation while another window of the same opening remains attached.

## Durable delivery

Modal delivery reuses `VisibleImpressionDelivery`; there is no separate lock or delivery engine.
Before sending, it commits the original WRIMP URLs and user snapshot to an SDK-private JSON file
under `noBackupFilesDir/gravity-wrimp`. A temporary file is flushed with `fsync` and atomically
renamed. Files are partitioned by SHA-256 of API key and section. Campaign/user pending entries
feed the existing gate directly, including after restart or gate reset. The gate cannot expire
an already visible opening while its WRIMP remains unacknowledged. An unconfirmed presentation
still has the existing 60-second safety timeout; removal before visibility releases it earlier.

The delivery scope is independent of Compose, window lifetime and SDK `dispose()`.
The original tracking URL encodes the original backend event/user context; replay sends this
URL unchanged with the API key of its section. The explicit `User` snapshot is also persisted.
A later `setUser` never rewrites or reattributes an older event: queued events continue with
their original URLs, including for users no longer active. Local campaign protection is keyed
by explicit custom user ID or anonymous backend UID, independently of the current session.
Other users and other sections can present independently. Reinitializing the SDK transfers
work through the same store; returning to a section reloads/resumes its queue.

Each URL gets up to three immediate attempts (250 ms and 1 s pauses). Network failures and
HTTP 408/429/5xx remain queued; connectivity restoration, SDK initialization and a 30-second
SDK timer resume them. `ACCESS_NETWORK_STATE` permits observation on Android API 21+; the timer
also provides a fallback when an observer cannot be registered. Permanent HTTP failures or
malformed URLs are retained as nonretryable pending entries, without claiming acknowledgement;
they require investigation of the original URL/server response. No failed response is treated
as a server frequency-cap write. Successful URLs are removed individually from the durable
entry; only all-2xx delivery removes its campaign protection. Crash recovery can resend a URL
if the process died between HTTP success and the durable update; exactly-once server analytics
cannot be guaranteed by the SDK.

Acknowledgement and queue removal occur under the gate's lock. Responses to requests started
before acknowledgement cannot reopen the campaign, including when the event was restored from
disk without a UI reservation. Fresh requests afterward use server frequency caps, preserving
“no limit” and “every page view”; there is no session-long local ban after success. Every new
opening gets its own delivery key, so even a repeated URL can be sent for an allowed new opening.
The existing 15-minute URL cache is retained only for nonmodal/legacy delivery calls.

The SDK emits the existing `ContentVisibleImpressionEvent` callback after persistence and sends
the existing backend `VISIBLE_IMPRESSION` event. Missing URLs and disk failures are logged;
failed persistence can be retried by a later detector or explicit-close signal. The SDK cannot
guarantee delivery when the application cannot write its private storage.

## Verification

`./gradlew :gravity_sdk:testDebugUnitTest :gravity_sdk:assembleDebug` runs module JUnit tests
and builds the AAR. Set `ANDROID_HOME` to the local Android SDK if not configured in Gradle.
Tests cover 100–400 ms close-controller signals, duplicate signals, no appearance/background,
step sharing, reservation races, offline recovery, partial success, real store reload, original
user/URL preservation, user and section isolation, and fresh requests after acknowledgement.
Ktor MockEngine tests cover HTTP 2xx, 408/429/5xx and permanent-error transport classification.
The JVM close tests exercise the common controller contract; they do not simulate Android gestures.

Before release, verify in a test app against a non-production section:

1. Close each of bottom sheet, dialog and fullscreen via swipe where supported, X, Back and
   “Not now” at 100–400 ms during opening animation. Confirm exactly one original WRIMP.
2. Inspect the first visible content frame (not just the scrim); background, navigate away or
   cancel before it. Confirm no false WRIMP and that an unshown campaign can be selected again.
3. Disable networking after appearance, close/destroy UI, navigate rapidly and restart the app.
   Confirm protection survives, restore connectivity and confirm delivery of the original URL.
4. Change user/session while delivery is pending. Confirm the old URL retains its original
   context and does not suppress another user's campaign. Repeat across sections.
5. Delay HTTP acknowledgements and `/choose`, issue parallel requests and navigate across
   screens. Confirm stale responses cannot reopen until a fresh request after acknowledgement.
6. Open several steps; verify one WRIMP for the opening and all steps/close controls still work.
7. Check monthly caps against the backend, plus allowed “no limit” / “every page view” repeats;
   independently check unchanged inline, product tracking and snackbar behavior.

Public SDK signatures, backend event names, tracking URLs and campaign configuration are unchanged.
No Gateway/Redis/server API changes are needed. This Android change is released independently of
other SDK platforms; their verification is outside this repository.
