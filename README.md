# BensinPris

Finds the cheapest fuel near you in Norway. Norwegian and English.

- **Cheapest first.** The top card shows the cheapest known price within 5, 10, 25 or 50 km for Bensin 95, Diesel or Bensin 98, how much it saves against the middle price nearby, and a **Kjør hit** button that opens driving directions in Google Maps.
- **Every station.** All ~1,900 Norwegian fuel stations are listed, sorted by price or distance. Stations without a known price are shown too, last.
- **Honest about age.** Each price shows when it was reported, coloured green (under 2 days), amber (under 2 weeks) or grey (older).
- **Your own prices.** Tap a station to see all its prices, open it in Google Maps, or type in the price you see at the pump. Your prices stay on your phone and are used until a newer price arrives.
- **Trip tab.** Enter where you're going (from your position or any place). The app gets the driving route, shows it on an OpenStreetMap map with a price tag on each station within 1, 2 or 5 km of the road, lists them cheapest first with how far into the trip they come and the detour, and opens Google Maps with the chosen station as a stop.
- **Works offline.** A copy of all stations and prices is bundled into each build, and the app keeps the last download.
- **Updates itself** from GitHub Releases.

## Data sources

| What | From | Licence |
| --- | --- | --- |
| Stations (name, chain, address, position) | [Drivstoffpriser open data](https://drivstoffpriser.github.io/Drivstoffpriser-App/data/index.html), based on OpenStreetMap | ODbL |
| Prices reported by drivers | Same open data, `prices.json` | ODbL |
| Station prices | [drivstoffapp.no](https://www.drivstoffapp.no) town pages (schema.org data; robots.txt allows `/search`), only for towns near you, cached 3 h | Public web page |
| Typical price per chain today (shown with ≈) | [DrivstoffAppen](https://drivstoffappen.no/drivstoffpriser) public price page (robots.txt allows all), cached 6 h | Public web page |
| Prices you enter | Your phone | — |
| Driving routes (trip tab) | OSRM: [routing.openstreetmap.de](https://routing.openstreetmap.de) (FOSSGIS), with the OSRM demo server as backup | Free for light use |
| Map (trip tab) | OpenStreetMap tiles via osmdroid, cached on the phone | ODbL, © OpenStreetMap contributors |

Which price wins for a station and fuel: newest first, where a website price counts as one day old and a chain estimate as two days old. So your own price or a fresh report beats the website, the website beats the chain estimate, and today's chain estimate beats a report from months ago. Website prices more than 15 % from the chain's typical price, and 98 cheaper than 95, are dropped as likely errors.

**Google Maps prices (optional):** paste your own Google Maps Platform key (Places API (New)) in the app's settings. The app then asks Google's Nearby Search for the 20 closest fuel stations, at most every 30 minutes or after moving 1 km, and uses the prices with Google's update times. Stations Google knows that the open list lacks are added. The key stays on the phone; never commit it to this public repo. Google Maps and Search pages themselves are not scraped (Google's terms forbid it).

As of November 2026 the open price export holds about 200 prices, the newest from May 2026: Drivstoffpriser's own app now keeps live prices on a private server. No Norwegian chain publishes per-station prices openly (Denmark requires it by law; Norway does not), so fresher prices need a new source. `PriceBook` merging in `Parse.merge` takes any number of sources, newest price wins.

## Code

- `data/` — pure Kotlin: `Parse` (feeds), `Rank` (distance, sorting, summary, Maps links), `Format` (numbers and ages). Unit-tested in `FuelTest`.
- `data/Store.kt` — feeds cached on the phone, your prices, remembered choices.
- `ui/` — `MainViewModel`, `HomeScreen`, `StationSheet`, theme.
- `update/` — self-updater.

Every push to `main` downloads the latest data into the APK, runs the tests, builds a signed release and publishes it.
