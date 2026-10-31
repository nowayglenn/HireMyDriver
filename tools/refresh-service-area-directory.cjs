// Public geographic data only. Run from the Android project root when refreshing the reference directory.
// No location, user data, Firebase credentials or API keys are sent.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const base = 'https://psgc.cloud/api/';
async function get(path) {
  const response = await fetch(base + path, {signal: AbortSignal.timeout(20000)});
  if (!response.ok) throw Error(`${path}: HTTP ${response.status}`);
  return response.json();
}
function name(raw) {
  let value = raw.trim();
  // The public source currently contains some UTF-8 text encoded as Latin-1.
  if (/[ÃÂ]/.test(value)) value = Buffer.from(value, 'latin1').toString('utf8');
  assert(!value.includes('\ufffd'), 'Invalid geographic name encoding');
  return value;
}
(async () => {
  const [provinces, regions, places] = await Promise.all([get('provinces'), get('regions'), get('cities-municipalities')]);
  const index = new Map(places.filter(x => ['City', 'Mun'].includes(x.type)).map(x => [x.code, x]));
  const grouped = new Map();
  let next = 0;
  await Promise.all(Array.from({length: 4}, async () => {
    while (next < provinces.length) {
      const province = provinces[next++];
      for (const item of await get(`provinces/${province.code}/cities-municipalities`)) {
        if (!index.has(item.code)) continue;
        // Province-free cities must not inherit an unrelated province from the source endpoint.
        const parent = item.code.slice(0, 2) === province.code.slice(0, 2) ? province
          : regions.find(r => r.code.slice(0, 2) === item.code.slice(0, 2));
        assert(parent, `Missing parent for ${item.code}`);
        const row = {code: item.code, name: name(index.get(item.code).name), provinceCode: parent.code,
          province: name(parent.name).replace(/\s*\([^)]*\)/g, '').trim()};
        assert(!row.name.includes(',') && !row.province.includes(','), 'Existing area storage uses comma separators');
        const old = grouped.get(row.code);
        assert(!old || old.provinceCode === row.provinceCode, `Ambiguous parent for ${row.code}`);
        grouped.set(row.code, row);
      }
    }
  }));
  assert(index.size >= 1600 && grouped.size === index.size, 'Incomplete city/municipality directory');
  const rows = [...grouped.values()].sort((a, b) => a.code.localeCompare(b.code));
  const target = 'app/src/main/assets/service_areas.json';
  fs.writeFileSync(target + '.tmp', JSON.stringify(rows));
  fs.renameSync(target + '.tmp', target);
  console.log(`Saved ${rows.length} cities/municipalities from ${base}`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
