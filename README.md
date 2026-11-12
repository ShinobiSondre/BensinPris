# BensinPris

Finds the cheapest fuel near you in Norway. Norwegian and English.

- **Cheapest first.** The top card shows the cheapest known price within 5, 10, 25 or 50 km for Bensin 95, Diesel or Bensin 98, how much it saves against the middle price nearby, and a **Kjør hit** button that opens driving directions in Google Maps.
- **Every station.** All ~1,900 Norwegian fuel stations are listed, sorted by price or distance. Stations without a known price are shown too, last.
- **Honest about age.** Each price shows when it was reported, coloured green (under 2 days), amber (under 2 weeks) or grey (older).
- **Your own prices.** Tap a station to see all its prices, open it in Google Maps, or type in the price you see at the pump. Your prices stay on your phone and are used until a newer price arrives.
- **Works offline.** A copy of all stations and prices is bundled into each build, and the app keeps the last download.
- **Updates itself** from GitHub Releases.

## Data sources

| What | From | Licence |
| --- | --- | --- |
| Stations (name, chain, address, position) | [Drivstoffpriser open data](https://drivstoffpriser.github.io/Drivstoffpriser-App/data/index.html), based on OpenStreetMap | ODbL |
| Prices reported by drivers | Same open data, `prices.json` | ODbL |
| Prices you enter | Your phone | — |

As of November 2026 the open price export holds about 200 prices, the newest from May 2026: Drivstoffpriser's own app now keeps live prices on a private server. No Norwegian chain publishes per-station prices openly (Denmark requires it by law; Norway does not), so fresher prices need a new source. `PriceBook` merging in `Parse.merge` takes any number of sources, newest price wins.

## Code

- `data/` — pure Kotlin: `Parse` (feeds), `Rank` (distance, sorting, summary, Maps links), `Format` (numbers and ages). Unit-tested in `FuelTest`.
- `data/Store.kt` — feeds cached on the phone, your prices, remembered choices.
- `ui/` — `MainViewModel`, `HomeScreen`, `StationSheet`, theme.
- `update/` — self-updater.

Every push to `main` downloads the latest data into the APK, runs the tests, builds a signed release and publishes it.
