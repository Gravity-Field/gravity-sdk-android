# Changelog

## Unreleased

* Add `customProps` and `eventTime` to all trigger events; `CustomEvent` also gets `cuid`, `cuidType` and `cart`
* Events without `eventTime` are sent with the time `triggerEvent` was called
* Unset optional fields of standard events (`currency`, `cart`, `hashedEmail`, `cuid`, `cuidType`) are omitted instead of sent as `null`

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
