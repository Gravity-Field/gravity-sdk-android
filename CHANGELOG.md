# Changelog

## Unreleased

* Fix parsing of `#RRGGBB` colors; `#RRGGBBAA` keeps alpha in the last pair. Invalid colors give no color instead of a wrong one

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
