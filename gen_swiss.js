const fs = require('fs');

const data = JSON.parse(fs.readFileSync('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/app/src/main/assets/switzerland.geojson', 'utf8'));

function simplifyPoints(points, tolerance = 0.1) {
    const step = Math.max(1, Math.floor(points.length / 200));
    return points.filter((_, i) => i % step === 0);
}

let out = '';
data.features.forEach((feat, i) => {
    // some geojson can be MultiPolygon, but let's assume Polygon for now or handle both
    let coords = feat.geometry.coordinates;
    if (feat.geometry.type === 'MultiPolygon') {
        coords = coords[0][0]; // Outer ring of first polygon
    } else {
        coords = coords[0]; // Outer ring
    }
    
    const simplified = simplifyPoints(coords);
    out += `    private val P${i+1}_SWISS = listOf(\n`;
    const lines = simplified.map(c => `        LatLng(${c[1]}, ${c[0]})`);
    out += lines.join(',\n');
    out += '\n    )\n\n';
});

fs.writeFileSync('c:/Users/Kyler/AndroidStudioProjects/CatenaryCompose/swiss_points.txt', out);
