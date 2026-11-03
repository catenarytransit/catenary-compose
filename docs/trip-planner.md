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

## Compact planner and connection cards

The floating input card has borderless, single-line fields with 4 dp inner
padding and 48 dp minimum touch targets. A pearl string joins the endpoints;
the swap control overlays the divider between them. The third row contains
the existing date/time picker, departure/arrival mode label, and speed options button.
On narrow screens the time controls scroll horizontally while options stay visible.
Speed options open a scrollable dialog, preserving the current values on dismissal.

Journey previews use neutral cards with an outline on the selected alternative.
Details follow the web connection-card layout: separate origin/destination cards,
solid route-colored rails in transit cards, expandable intermediate stops, platform
labels on the right, and outlined walking/cycling cards with dotted rails. Rail
height follows each row, including wrapped stop names and expanded stops.

For device review, check long endpoint names, large system fonts, dark/light
mode, narrow screens, the swap control, Now/future/arrive-by searches, popup
scrolling and dismissal, expanded stop lists, and metric/imperial distances.

## Summary navigation and travel-date mode

Tap anywhere on a result card to select the route and open its details. The floating
endpoint card becomes the selected journey timeline summary while details are open;
Back or All journeys restores the endpoint fields. There are no Show details or
Find journeys buttons; endpoint and option changes trigger routing automatically.

The shared TimeSelectorButton accepts optional arriveBy/onArriveByChange values.
When supplied, the time dialog shows a rounded Departure/Arrival sliding selector.
Mode and wheel changes are staged until confirmation; Cancel discards them. Arrival
clears Now, while choosing Now selects Departure. Existing callers omit these
parameters and keep their existing time-only picker.

Current-location selections carry an explicit flag, allowing either endpoint to
show a blue location icon even after swapping. Editing or dragging to a new place
clears that identity. Both map themes query poi_named, poi_r1, poi_r7 and poi_transit
for POI taps, including restaurant labels; transit features retain click priority.

Device checks: tap result cards and return from details; confirm that the summary
replaces the endpoint card and occurs only once. Select My location for each endpoint,
swap, edit and drag it. Apply and cancel both time modes, including Arrival then Now.
Open a restaurant and other named POIs in both map themes, then select those POIs
as routing endpoints while a field has focus.
