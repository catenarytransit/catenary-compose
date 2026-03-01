import json

with open('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/app/src/main/assets/switzerland.geojson') as f:
    d = json.load(f)

# Simplification function
def simplify_points(points, tolerance=0.1):
    # Just take every Nth point to reduce size drastically, it's a huge file (7MB)
    # We don't need high precision for UI zone checks
    step = max(1, len(points) // 200) # Keep max ~200 points
    return points[::step]

with open('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/swiss_points.txt', 'w') as out:
    for i, feat in enumerate(d['features']):
        coords = feat['geometry']['coordinates'][0]
        simplified = simplify_points(coords)
        out.write(f'    private val P{i+1}_SWISS = listOf(\n')
        lines = []
        for c in simplified:
            lines.append(f'        LatLng({c[1]}, {c[0]})')
        out.write(',\n'.join(lines))
        out.write('\n    )\n\n')
