# Android trip planner

Ported from catenary-web `routing`, commit `e32f0bcd684ef508e7856243eaf29e29c860eac0`:
`TripPlanner.svelte`, `TripItineraryDetails.svelte`, and `lib/features/directions/`.
The four bundled mode SVGs come from that revision's `static/icons/` directory.

The directions FAB above geolocate opens the planner. GTFS stop, OSM station,
and OSM item details expose **As origin** and **As destination**. These actions
retain the opposite endpoint, and both endpoints can also be dragged on the map.
When a location field has focus, a map tap chooses the named GTFS stop, OSM
station or POI at that point, with plain coordinates as the fallback.

The screen reuses Android's Birch/Cypress search, station history, route badges,
and date/time picker. It posts to Edelweiss `/v1/route`, requests detailed legs,
and keeps the server's transit ranking followed by direct alternatives.
Walking pace is seconds/km; cycling is km/h; the request converts both to m/s.
Time budgets are seconds. “Now” is resolved when sending the request.
The time picker and displayed timestamps use the device timezone.

Journey cards show compact times, proportional transit/walking segments,
route badges and walking distance. Details combine successive street legs and
expand intermediate stops. Map geometry is decoded at the supplied precision;
transit shapes are trimmed to the boarding/alighting stops. Alternatives are grey,
selected transit uses route colors, and street sections are dashed. Interchange
markers appear after selecting a journey. Dedicated directions overlays are
removed on exit and previous context overlays are emptied on entry.

## Validation

`TripPlannerTest` covers the request/time contract, speed and budget units,
coordinate parsing, malformed geometry, polyline precision, full-shape trimming,
and transit/direct response decoding. Run with:

```
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

Manual device checks:

1. Open the FAB on phone and tablet layouts; verify it sits above geolocate.
2. Choose each endpoint using search, GPS, recent places, coordinates, and map
   taps on a GTFS stop, OSM station, POI, and empty ground. Swap endpoints.
3. Open each of the three detail screens and exercise both directions actions.
4. Drag both endpoint markers; verify the old response is replaced by a new query.
5. Try Now, a future departure, and arrive-by; change walking/cycling speed and
   all three budgets. Check loading, empty results, errors, and retry.
6. Select alternatives and open details; expand stops and check platforms,
   walking distances, and map paths. Check light/dark themes.
7. Leave while routing or searching, then reopen. Check that stale responses do
   not replace new choices and directions paths disappear on Back/Home.
