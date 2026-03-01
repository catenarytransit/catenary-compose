import json

with open('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/app/src/main/assets/eurostyle-ui-zone.geojson') as f:
    d = json.load(f)

with open('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/euro_points.txt', 'w') as out:
    for i, feat in enumerate(d['features']):
        coords = feat['geometry']['coordinates'][0]
        out.write(f'    private val P{i+1} = listOf(\n')
        lines = []
        for c in coords:
            lines.append(f'        LatLng({c[1]}, {c[0]})')
        out.write(',\n'.join(lines))
        out.write('\n    )\n\n')
