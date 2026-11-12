# BensinPris

Finds the cheapest fuel near you in Norway, in Norwegian and English.

- **Cheapest first.** Stations within 5, 10 or 25 km, sorted by price for Bensin 95, Bensin 98 or Diesel. Stations with no price are listed last.
- **Several sources.** Station locations come from OpenStreetMap (Overpass API, free, no key). Prices come from community reports published as open data by drivstoffpriser (refreshed every 12 hours, free to use). Each price is matched to the nearest station within 60 m.
- **Open in Google Maps.** Each station has a button that starts driving directions to it.

## Status

Not yet verified against the live price file. The field names in `CommunityPrices` (`app/src/main/java/com/bensinpris/app/data/Sources.kt`) are assumptions. Check them against `prices.json` before relying on prices.

## Building

Every push to `main` builds a signed APK and publishes it under Releases. The keystore is `keystore/bensinpris.jks`, which must be created once and kept, so each release can update the one before it.
