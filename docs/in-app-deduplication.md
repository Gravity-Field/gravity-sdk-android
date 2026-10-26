# In-app presentation and visible impression delivery (EGO-248, EGO-272)

Automatic `trackView` / `triggerEvent` presentation reserves a campaign before `/choose`.
`InAppPresentationGate` atomically covers content retrieval, delay, preparation, presentation,
and the gap before WRIMP acknowledgement. Requests capture screen, user and SDK generations;
stale responses cannot mount a new window. The public presentation lock remains independent.

For bottom sheets, modal/dialog and fullscreen, `visible_impression` means **the window content
actually appeared on screen**, without a minimum viewing duration. A content draw must intersect
the window while it has focus and its lifecycle is resumed. The callback runs after the draw
traversal. Creating a component, `/choose`, content loading, zero-size/off-screen content and
rendering only a scrim cannot count. After at least one content draw, a window-frame callback
checks the latest bounds, lifecycle and focus for at most 120 frames, stopping on confirmation or
abandonment. Later real draws and focus changes can still confirm content without continuous
vsync polling. This also handles a cached graphics layer sliding into view without redrawing its text/images. `VisibilityDetector` and its existing 1-second/50% policy are unchanged for inline,
product cards and snackbar; snackbar does not use the new modal detector.

Each modal opening has one `ModalImpressionSession`, including all `OPEN_STEP` transitions.
Its detector and explicit close handlers share an atomic fired flag. Swipe, X, Back, dismiss/
“Not now” buttons and `dismissInApp` enqueue WRIMP and set memory protection before closing if a content frame has been
drawn, including when the detector's posted callback has not run yet. Recomposition, repeated
signals and later steps cannot create another impression for that opening. The next step is
prepared and mounted with the same session; the old step closes only when `closeOnClick` is true
(including when preparation of the next step fails). Navigation-driven removal and backgrounding
never call the explicit-close fallback. Counted windows remain open across background/resume,
including return from system settings. Unconfirmed windows close through the common cleanup path
with reservation release and no close engagement or host `ContentCloseEvent`; late visibility callbacks are cancelled.
A step replacement
keeps the reservation while another window of the same opening remains attached.

## Durable delivery

Modal delivery reuses `VisibleImpressionDelivery`; there is no separate lock or delivery engine.
Before sending, it commits the original WRIMP URLs and user snapshot to an SDK-private JSON file
under `noBackupFilesDir/gravity-wrimp`. A temporary file is flushed with `fsync` and atomically
renamed. Files are partitioned by SHA-256 of API key and section. Campaign/user pending entries
feed the existing gate directly, including after restart or gate reset. Local protection expires
24 hours after the persisted show timestamp, or on delivery 2xx / permanent drop, whichever is
first. The event remains queued independently until its 7-day delivery TTL: at 24 hours the gate
allows another opening, while the original WRIMP still retries. This also applies to “no limit” /
“every page view” campaigns. At 7 days, the event is discarded without server acknowledgement.
The queue holds at most 512 openings per
API key/section; overflow discards the oldest pending opening and releases its protection.
Unconfirmed presentations and legacy/snackbar visibility retain the original 60-second safety
timeout. Removal before appearance releases a reservation earlier. Corrupt/schema-incompatible
files are logged, quarantined as `.corrupt` (or deleted if renaming fails), and replaced by an
empty queue, so queue recovery cannot crash SDK initialization. Unknown JSON fields are accepted.
Old API-key/section files and quarantine files whose last modification is at least 7 days old
are cleaned during background queue loading.

Loading, JSON encoding, fsync, rename and eviction run on a shared serial IO dispatcher.
Before scheduling persistence, the SDK stages the event and its protection synchronously in the
store's memory snapshot. UI readers never wait on its disk lock. On startup, the automatic
campaign response handler awaits snapshot loading on the IO dispatcher before reserving;
other gate callers conservatively reject reservations until that snapshot is available. Queue removal
is committed to disk first, then published under the gate lock together with acknowledgement;
no disk operations hold the gate lock. Expired/capacity evictions are batched into one commit.

The delivery scope is independent of Compose, window lifetime and SDK `dispose()`.
The ConnectivityManager callback and SDK delivery runtime remain alive after `dispose()` to
finish queued work; the next `initialize()` unregisters the old callback and replaces that runtime.
Pending persistence is process-scoped and survives both operations.
The original tracking URL encodes the original backend event/user context; replay sends this
URL unchanged with the API key of its section. The explicit `User` snapshot is also persisted.
A later `setUser` never rewrites or reattributes an older event: queued events continue with
their original URLs, including for users no longer active. Local campaign protection is keyed
by explicit custom user ID or anonymous backend UID, independently of the current session.
If neither is known, the original URL is still queued, but no shared unidentified-user delivery
protection is installed. Other users and other sections can present independently. Reinitializing the SDK transfers
work through the same store; returning to a section reloads/resumes its queue.

Each URL gets up to three immediate attempts (250 ms and 1 s pauses). Network failures and
HTTP 408/429/5xx remain queued; connectivity restoration, SDK initialization and a 30-second
SDK timer resume them. The timer runs only while work remains; an empty queue waits for an
explicit enqueue/connectivity signal. `ACCESS_NETWORK_STATE` is a normal manifest permission
merged into the host app; it permits observation on Android API 21+ without a runtime permission
prompt. The timer also provides a fallback when an observer cannot be registered.
Permanent HTTP failures (other 4xx) or malformed URLs drop the entire opening's queue entry and
release its local reservation without retry or acknowledgement. Previously terminal entries
from older SDK files are also dropped. Successful URLs are removed individually; all-2xx
delivery removes campaign protection with an acknowledgement watermark. No failed/expired/dropped
entry is treated as a server frequency-cap write. New entries added during a drain are processed
in the same pass, and SDK instances sharing a file also share its drain lock. Crash recovery can resend a URL
if the process died between HTTP success and the durable update; exactly-once server analytics
cannot be guaranteed by the SDK.

Acknowledgement and publication of queue removal occur under the gate's lock, after disk commit.
The queued event ID prevents an old ACK/drop from releasing a newer opening after 24-hour expiry. Responses to requests started
before acknowledgement cannot reopen the campaign, including when the event was restored from
disk without a UI reservation. Fresh requests afterward use server frequency caps, preserving
“no limit” and “every page view”; there is no session-long local ban after success. Every new
opening gets its own delivery key, so even a repeated URL can be sent for an allowed new opening.
The existing 15-minute URL cache is retained only for nonmodal/legacy delivery calls.

The SDK emits the existing `ContentVisibleImpressionEvent` callback after persistence and sends
the existing backend `VISIBLE_IMPRESSION` event. Modal formats deliberately select the WRIMP
URLs regardless of a custom `variables.onVisibleImpression.action`: a different action cannot
establish the frequency cap required for a shown modal. Legacy/nonmodal action selection stays
unchanged. Missing WRIMP URLs and disk failures are logged. A failed asynchronous write keeps
the staged event and memory protection for subsequent runtime retries; no HTTP request or
visible-impression callback occurs until persistence succeeds. The SDK cannot guarantee delivery
when private storage remains unwritable, or if the process dies during the brief handoff before
the first durable commit. Closing the UI does not cancel that commit or delivery.

## Verification

`./gradlew :gravity_sdk:testDebugUnitTest :gravity_sdk:assembleDebug` runs module JUnit tests
and builds the AAR. Set `ANDROID_HOME` to the local Android SDK if not configured in Gradle.
Tests cover 100–400 ms close-controller signals, duplicate signals, no appearance/background,
step sharing, reservation races, offline recovery, partial success, real store reload, original
user/URL preservation, user and section isolation, and fresh requests after acknowledgement.
Ktor MockEngine tests cover HTTP 2xx, 408/429/5xx and permanent-error transport classification.
The JVM close tests exercise the common controller contract; they do not simulate Android gestures.
`ModalVisibilityWiringTest` adds device Compose checks for cached-layer movement, the text-only
Material sheet opening animation, zero-size content, SDK fullscreen stop/resume, actual X/Back/
Not-now/swipe dismissal routes and `OPEN_STEP.closeOnClick` when preparation or the target step
is skipped/missing. Dialog-specific cases cover first appearance and Back/X/Not-now; additional
cases cover an unshown window stopping and enqueue through an old SDK after reinitialization.
JVM tests also cover 24-hour protection vs 7-day delivery, async staging and write failure,
identity/section selection on completion, and late ACK/drop isolation from newer openings.
Run with `./gradlew :gravity_sdk:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=ai.gravityfield.gravity_sdk.ModalVisibilityWiringTest`
on a connected emulator/device. Test compilation alone does not establish device test success.

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
