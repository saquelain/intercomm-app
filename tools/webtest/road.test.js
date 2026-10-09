// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// On the road ahead: the Fuel / Food / Mechanic finder (only places ahead, left / right, Navigate, Stop here),
// low fuel (the pump ahead, 2 km and 500 m, a passed pump skipped, searching again, ending by itself at a pump,
// Got fuel, what the other riders hear), Google Places with its fall-back to OpenStreetMap, rain alerts, and the
// two Settings switches. OpenStreetMap (Overpass), Google and Open-Meteo are all stand-ins here.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'http://127.0.0.1:8765/';

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const VIEW = { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) };
  const errors = [];
  let fails = 0;
  const check = (label, cond) => { if (!cond) fails++; console.log((cond ? 'PASS ' : 'FAIL ') + label); };
  const pass = (b) => JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';

  // What the stand-in services answer, and what they were asked.
  const osm = { fuel: [], food: [], mech: [], fail: 0, asked: [] };
  const meteo = { answer: null, asked: [] };
  const kindOf = (q) => (q.includes('"fuel"') ? 'fuel' : q.includes('restaurant') ? 'food' : 'mech');

  async function newCtx(googleKey) {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: VIEW, deviceScaleFactor: 2 });
    // The page's Google key only works on the real site: no key (OpenStreetMap), or a stand-in key with a stand-in Google.
    await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
      const res = await r.fetch();
      r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '" + (googleKey || '') + "';") });
    });
    await ctx.route(/maps\.googleapis\.com\/maps\/api\/js/, (r) => {
      const cb = new URL(r.request().url()).searchParams.get('callback');
      r.fulfill({ contentType: 'application/javascript', body: `
        window.__gReq = [];
        const near = (p, c, radius) => { const k = 111320, dy = (p.lat - c.lat) * k, dx = (p.lng - c.lng) * k * Math.cos(c.lat * Math.PI / 180); return Math.hypot(dx, dy) <= radius; };
        window.google = { maps: { importLibrary: async (name) => {
          if (name !== 'places') throw new Error('no ' + name);
          return { SearchNearbyRankPreference: { DISTANCE: 'DISTANCE', POPULARITY: 'POPULARITY' }, Place: { searchNearby: async (req) => {
            window.__gReq.push(JSON.parse(JSON.stringify(req)));
            if (window.__gFail) throw new Error('Places API (New) has not been used in this project');
            const c = req.locationRestriction.center, list = (window.__gPlaces || []).filter((p) => near(p, c, req.locationRestriction.radius) && (p.types || ['gas_station']).some((t) => req.includedTypes.includes(t)));
            return { places: list.map((p) => ({ id: p.id, displayName: p.name, location: { lat: () => p.lat, lng: () => p.lng } })) };
          } } };
        } } };
        window[${JSON.stringify(cb)}]();` });
    });
    await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
    await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
    await ctx.route(/overpass|maps\.mail\.ru/, (r) => {
      const q = decodeURIComponent((r.request().postData() || '').replace(/^data=/, '').replace(/\+/g, ' '));
      osm.asked.push({ url: r.request().url(), q });
      if (osm.fail > 0) { osm.fail--; return r.fulfill({ status: 504, body: 'busy' }); }
      r.fulfill({ contentType: 'application/json', body: JSON.stringify({ elements: osm[kindOf(q)] }) });
    });
    await ctx.route(/api\.open-meteo\.com/, (r) => {
      const u = new URL(r.request().url());
      meteo.asked.push(u);
      r.fulfill({ contentType: 'application/json', body: JSON.stringify(meteo.answer(u)) });
    });
    await ctx.route(/nominatim\.openstreetmap\.org/, (r) => r.fulfill({ contentType: 'application/json', body: '[]' }));
    await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
      const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
      return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
    });
    await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
    await ctx.addInitScript(() => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      window.__vib = [];
      navigator.vibrate = (p) => { window.__vib.push(p); return true; };
      navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: window.__fix || { latitude: 18.5, longitude: 73.85 } });
      window.__watchers = [];
      navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
      navigator.geolocation.clearWatch = () => {};
      window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
      window.confirm = () => true;
      window.open = () => null;
      window.__shift = 0;
      const now = Date.now.bind(Date);
      Date.now = () => now() + window.__shift;
    });
    return ctx;
  }
  async function page(ctx, name, id, settings, code = 'RDAHED') {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await p.addInitScript(([i, s]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', s);
    }, [id, JSON.stringify(settings || {})]);
    await p.goto(BASE + 'ride/?code=' + code);
    await p.fill('#nameIn', name);
    await p.click('#joinBtn');
    await p.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return p;
  }
  const spoken = (p) => p.evaluate(() => window.__spoken.slice());
  const said = async (p, text) => (await spoken(p)).filter((t) => t === text).length;
  const waitSaid = async (p, text, ms = 5000) => {
    try { await p.waitForFunction((t) => window.__spoken.includes(t), text, { timeout: ms }); return true; } catch (e) { return false; }
  };
  const move = (p, lat, lon, speed = 15, heading = 0, secs = 0) =>
    p.evaluate(([la, lo, s, h, d]) => { window.__shift += d * 1000; window.__move({ latitude: la, longitude: lo, accuracy: 8, speed: s, heading: h }); }, [lat, lon, speed, heading, secs]);
  const setLook = async (p, look) => {
    await p.click('#ride [data-open-settings]');
    await p.click('[data-choice="look"][data-value="' + look + '"]');
    await p.click('#settingsClose');
    await p.waitForTimeout(150);
  };
  const shot = async (p, file, sel) => {
    if (sel) await p.evaluate((s) => document.querySelector(s).scrollIntoView({ block: 'center' }), sel);
    await p.waitForTimeout(350);
    await p.screenshot({ path: OUT + file });
  };
  const node = (id, lat, lon, tags) => ({ type: 'node', id, lat, lon, tags });

  // ================= Finder: only places ahead, left / right, Navigate, Stop here =================
  {
    osm.fuel = [
      node(1, 18.4950, 73.8500, { name: 'Behind Pump' }), // 560 m behind: closer than most, but behind
      node(2, 18.5030, 73.8496, { name: 'Left Pump' }), // 330 m ahead, 42 m to the left
      { type: 'way', id: 3, center: { lat: 18.5300, lon: 73.8505 }, tags: { brand: 'HP' } }, // 3.3 km ahead, named by its brand
      node(4, 18.5005, 73.8560, { name: 'Side Pump' }), // 630 m off to the right side: not on my way
      node(5, 18.5100, 73.8530, { operator: 'Right Pump' }), // 1.2 km ahead, 320 m to the right
    ];
    osm.food = []; osm.mech = [node(9, 18.52, 73.85, { name: 'Raju Garage' })];
    const ctx = await newCtx();
    const a = await page(ctx, 'Asha', 'web-a', { map: true });
    const b = await page(ctx, 'Bilal', 'web-b', { map: true });
    await a.waitForTimeout(300);
    check('Card "On the road ahead" with Fuel, Food, Mechanic and Low fuel', (await a.textContent('#roadCard')).replace(/\s+/g, ' ').includes('On the road aheadFuelFoodMechanicLow fuel'));
    await move(a, 18.5000, 73.8500);
    await move(b, 18.4990, 73.8500, 0, NaN);
    await a.waitForTimeout(200);
    await shot(a, 'road1_card_classic.png', '#roadCard');
    await a.click('[data-find="fuel"]');
    await a.waitForSelector('#findSheet:not(.hidden) .fplace', { timeout: 5000 });
    const q = osm.asked[osm.asked.length - 1];
    check('OpenStreetMap asked for fuel 20 km around me', q.url.includes('overpass-api.de') && q.q.includes('node["amenity"="fuel"](around:20000,18.50000,73.85000)') &&
      q.q.includes('way["amenity"="fuel"](around:20000,18.50000,73.85000)') && q.q.startsWith('[out:json][timeout:20];') && q.q.endsWith('out center tags 300;'));
    const rows = await a.$$eval('#findSheet .fplace', (els) => els.map((e) => ({ n: e.querySelector('.n').textContent, dd: e.querySelector('.dd').textContent, nav: e.querySelector('a').href, stop: !!e.querySelector('[data-find-stop]') })));
    check('Title: Petrol pumps ahead', (await a.textContent('#findSheet h1')) === 'Petrol pumps ahead');
    check('Only the 3 pumps ahead, best first (behind and off to the side left out)', JSON.stringify(rows.map((r) => r.n)) === '["Left Pump","Right Pump","HP"]');
    check('Left / right: "340 m ahead · on your left", "1.2 km ahead · on your right", far one without a side',
      rows[0].dd === '340 m ahead · on your left' && rows[1].dd === '1.2 km ahead · on your right' && rows[2].dd === '3.3 km ahead');
    check('Navigate: Google Maps directions to the pump', rows[0].nav === 'https://www.google.com/maps/dir/?api=1&destination=18.503,73.8496&travelmode=driving');
    check('"Stop here" on each (Group map on)', rows.every((r) => r.stop));
    check('Says where the search came from', (await a.textContent('#findSheet')).includes('Search by OpenStreetMap.'));
    await shot(a, 'road2_fuel_sheet_classic.png');
    await a.click('[data-find-stop="1"]');
    await a.waitForTimeout(400);
    check('Stop here: the sheet closes and a regroup point "Fuel stop" is set', await a.isHidden('#findSheet') && (await a.textContent('#mapCard')).includes('Regroup: Fuel stop'));
    check('Bilal hears the regroup point', (await spoken(b)).some((t) => t.startsWith('Asha set a regroup point: Fuel stop')));
    check('Bilal gets it at the pump', (await b.textContent('#mapCard')).includes('Regroup: Fuel stop'));
    // Food: nothing near.
    await a.click('[data-find="food"]');
    await a.waitForFunction(() => /Nothing found/.test(document.getElementById('findSheetIn').textContent), null, { timeout: 5000 });
    check('Food: "Nothing found within 10 km ahead."', (await a.textContent('#findSheetIn')).includes('Nothing found within 10 km ahead.'));
    check('Food search: restaurants, fast food, cafés, food courts within 10 km', osm.asked[osm.asked.length - 1].q.includes('node["amenity"~"^(restaurant|fast_food|cafe|food_court)$"](around:10000,'));
    await a.click('#findClose');
    // Mechanic: all three OpenStreetMap servers down → error; then the first one down → the second answers.
    osm.fail = 3;
    let before = osm.asked.length;
    await a.click('[data-find="mech"]');
    await a.waitForFunction(() => /Couldn't search/.test(document.getElementById('findSheetIn').textContent), null, { timeout: 8000 });
    check("All servers down: \"Couldn't search. Check your internet.\" after trying all three", (await a.textContent('#findSheetIn')).includes("Couldn't search. Check your internet.") &&
      osm.asked.slice(before).map((x) => new URL(x.url).host).join() === 'overpass-api.de,overpass.kumi.systems,maps.mail.ru');
    osm.fail = 1; before = osm.asked.length;
    await a.click('#findClose');
    await a.click('[data-find="mech"]');
    await a.waitForSelector('#findSheet .fplace', { timeout: 8000 });
    check('First server down: the second one answers (Raju Garage, 2.2 km ahead)', osm.asked.length - before === 2 && (await a.textContent('#findSheet .fplace')).includes('Raju Garage') &&
      (await a.textContent('#findSheet .fplace .dd')) === '2.2 km ahead');
    check('Mechanic search: car and bike repair, tyres', osm.asked[osm.asked.length - 1].q.includes('["shop"~"^(car_repair|motorcycle|motorcycle_repair|tyres)$"]'));
    await a.click('#findClose');
    // Bilal isn't moving and has no destination: nearest in any direction.
    await b.click('[data-find="fuel"]');
    await b.waitForSelector('#findSheet .fplace', { timeout: 5000 });
    const brows = await b.$$eval('#findSheet .fplace .dd', (els) => els.map((e) => e.textContent));
    check('No direction: "Petrol pumps nearby", nearest first in any direction', (await b.textContent('#findSheet h1')) === 'Petrol pumps nearby' &&
      (await b.textContent('#findSheet .fplace .n')) === 'Behind Pump' && brows[0] === '440 m away · nearest' && brows.length === 5);
    await b.click('#findClose');
    // A shared destination gives the direction when standing still.
    await b.click('[data-open-dest]');
    await b.fill('#destQuery', '18.6000, 73.8500');
    await b.click('#destSearch');
    await b.click('[data-place="0"]');
    await b.waitForTimeout(300);
    await b.click('[data-find="fuel"]');
    await b.waitForSelector('#findSheet .fplace', { timeout: 5000 });
    check('Standing still with a destination: the places towards it', (await b.textContent('#findSheet h1')) === 'Petrol pumps ahead' &&
      JSON.stringify(await b.$$eval('#findSheet .fplace .n', (els) => els.map((e) => e.textContent))) === '["Left Pump","Right Pump","HP"]');
    await b.click('#findClose');
    // The looks.
    for (const look of ['glass', 'neu']) {
      await setLook(a, look);
      await shot(a, 'road1_card_' + look + '.png', '#roadCard');
      await a.click('[data-find="fuel"]');
      await a.waitForSelector('#findSheet .fplace', { timeout: 5000 });
      await shot(a, 'road2_fuel_sheet_' + look + '.png');
      await a.click('#findClose');
    }
    const over = await a.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    check('Nothing wider than the screen', !over);
    await ctx.close();
  }

  // ================= Low fuel =================
  {
    osm.fuel = [
      node(11, 18.5300, 73.8505, { name: 'Indian Oil' }), // 3.3 km ahead, 52 m to the right
      node(12, 18.5550, 73.8500, { name: 'HP Petrol' }), // 6.1 km ahead
      node(13, 18.4950, 73.8500, { name: 'Behind Pump' }),
    ];
    const ctx = await newCtx();
    const a = await page(ctx, 'Asha', 'web-a', {});
    const b = await page(ctx, 'Bilal', 'web-b', {});
    await a.waitForTimeout(300);
    await move(a, 18.5000, 73.8500);
    await a.waitForTimeout(200);
    let before = osm.asked.length;
    await a.click('#lowFuel');
    check('Low fuel: "Looking for petrol ahead"', await waitSaid(a, 'Looking for petrol ahead'));
    check('Says the best pump ahead: "Nearest petrol pump ahead: Indian Oil, 3.3 kilometres"', await waitSaid(a, 'Nearest petrol pump ahead: Indian Oil, 3.3 kilometres'));
    check('One search', osm.asked.length - before === 1);
    check('Bilal hears "Asha is low on fuel"', await waitSaid(b, 'Asha is low on fuel'));
    await b.waitForTimeout(200);
    check("Bilal sees a fuel tag on Asha's row", !!(await b.$('[data-rider="web-a"] .tag[aria-label="Low on fuel"]')));
    await a.waitForTimeout(200);
    const panel = (await a.textContent('#roadCard')).replace(/\s+/g, ' ');
    check('Panel: Low fuel · Indian Oil · 3.3 km ahead, Navigate and Got fuel', panel.includes('Low fuel') && panel.includes('Indian Oil') && panel.includes('3.3 km ahead') &&
      panel.includes('Navigate') && panel.includes('Got fuel') && !(await a.$('#lowFuel')));
    check('Panel Navigate goes to Indian Oil', (await a.getAttribute('#roadCard a.btn.primary', 'href')).includes('destination=18.53,73.8505'));
    await shot(a, 'road3_lowfuel_classic.png', '#roadCard');
    await move(a, 18.5140, 73.8500, 15, 0, 60);
    check('1.8 km before: "Petrol pump in 2 kilometres"', await waitSaid(a, 'Petrol pump in 2 kilometres'));
    await move(a, 18.5260, 73.8500, 15, 0, 60);
    check('450 m before: "Petrol pump in 500 metres, on your right"', await waitSaid(a, 'Petrol pump in 500 metres, on your right'));
    check('With a buzz', (await a.evaluate(() => JSON.stringify(window.__vib))).includes('[150,80,150]'));
    check('Each said once', (await said(a, 'Petrol pump in 2 kilometres')) === 1 && (await said(a, 'Nearest petrol pump ahead: Indian Oil, 3.3 kilometres')) === 1);
    // Rides past it: the next one.
    await move(a, 18.5335, 73.8500, 15, 0, 30);
    check('Passed it: "Next petrol pump ahead: HP Petrol, 2.4 kilometres"', await waitSaid(a, 'Next petrol pump ahead: HP Petrol, 2.4 kilometres'));
    await a.waitForTimeout(200);
    check('Panel shows HP Petrol now', (await a.textContent('#roadCard')).includes('HP Petrol'));
    await move(a, 18.5400, 73.8500, 15, 0, 20);
    check('Then "Petrol pump in 2 kilometres" again for HP', await waitSaid(a, 'Petrol pump in 2 kilometres') && (await said(a, 'Petrol pump in 2 kilometres')) === 2);
    await move(a, 18.5510, 73.8500, 15, 0, 60);
    check('"Petrol pump in 500 metres" (straight ahead: no side)', await waitSaid(a, 'Petrol pump in 500 metres'));
    check('No new search within 8 km', osm.asked.length - before === 1);
    // Stops at the pump: off by itself after 90 s.
    await move(a, 18.5549, 73.8500, 0, NaN, 30);
    await move(a, 18.5549, 73.8501, 0, NaN, 40);
    await a.waitForTimeout(200);
    check('Still on after 40 s at the pump', !(await a.$('#lowFuel')));
    await move(a, 18.5549, 73.8500, 0, NaN, 55);
    check('Stopped 95 s at the pump: "Low fuel is off"', await waitSaid(a, 'Low fuel is off'));
    check('Bilal hears "Asha has filled up"', await waitSaid(b, 'Asha has filled up'));
    await a.waitForTimeout(200);
    check('The card is back to Fuel / Food / Mechanic / Low fuel', !!(await a.$('#lowFuel')) && !(await a.$('#gotFuel')));
    check("Bilal: the tag is gone from Asha's row", !(await b.$('[data-rider="web-a"] .tag[aria-label="Low on fuel"]')));
    // Again, with no pump near: "none found", then searching again after 8 km.
    osm.fuel = [];
    before = osm.asked.length;
    await a.click('#lowFuel');
    check('Nothing ahead: "No petrol pump found in the next 20 kilometres. I\'ll keep looking."', await waitSaid(a, "No petrol pump found in the next 20 kilometres. I'll keep looking."));
    await a.waitForTimeout(200);
    check('Panel: "No petrol pump ahead yet"', (await a.textContent('#roadCard')).includes('No petrol pump ahead yet'));
    await shot(a, 'road4_lowfuel_none_classic.png', '#roadCard');
    osm.fuel = [node(14, 18.7000, 73.8500, { name: 'Far Pump' })];
    await move(a, 18.5600, 73.8500, 15, 0, 20);
    await a.waitForTimeout(300);
    check('Nothing ahead: not searching again within a minute', osm.asked.length - before === 1);
    await move(a, 18.5700, 73.8500, 15, 0, 50);
    check('A minute on: searches again and finds "Far Pump"', await waitSaid(a, 'Nearest petrol pump ahead: Far Pump, 14 kilometres') && osm.asked.length - before === 2);
    await move(a, 18.6450, 73.8500, 15, 0, 300);
    await a.waitForTimeout(400);
    check('8 km on: searches again', osm.asked.length - before === 3 && (await said(a, 'Nearest petrol pump ahead: Far Pump, 14 kilometres')) === 1);
    for (const look of ['glass', 'neu']) { await setLook(a, look); await shot(a, 'road3_lowfuel_' + look + '.png', '#roadCard'); }
    await a.click('#gotFuel');
    check('Got fuel: Bilal hears "Asha has filled up" again', await waitSaid(b, 'Asha has filled up') && await b.waitForFunction(() => window.__spoken.filter((t) => t === 'Asha has filled up').length === 2, null, { timeout: 3000 }).then(() => true, () => false));
    await a.waitForTimeout(200);
    check('Got fuel: low fuel off', !!(await a.$('#lowFuel')));
    // Watchers and check-ins aren't riders: ignored.
    const lowBefore = await said(b, 'Asha is low on fuel');
    await b.evaluate(() => {
      const ch = new BroadcastChannel('fake-sfu');
      ch.postMessage({ type: 'text', topic: 'rc-fuel', text: JSON.stringify({ t: 'low', name: 'Ammi', on: true }), from: 'watch-1', to: [] });
      ch.postMessage({ type: 'text', topic: 'rc-fuel', text: JSON.stringify({ t: 'low', name: 'Asha', on: true }), from: 'web-a~home', to: [] });
    });
    await b.waitForTimeout(300);
    check('From family watching or a home check-in: not said', (await said(b, 'Ammi is low on fuel')) === 0 && (await said(b, 'Asha is low on fuel')) === lowBefore);
    // Leaving with low fuel on turns it off.
    await a.click('#lowFuel');
    await a.waitForTimeout(300);
    await a.click('#leaveBtn');
    await a.click('#leaveGo');
    await a.waitForTimeout(800);
    await a.evaluate(() => { const v = document.getElementById('rideView'); if (v) v.classList.add('hidden'); });
    await a.fill('#nameIn', 'Asha');
    await a.click('#joinBtn');
    await a.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    await a.waitForTimeout(300);
    check('Leaving turns low fuel off (back in the ride: the Low fuel button)', !!(await a.$('#lowFuel')));
    check('Leaving with low fuel on: nobody hears "filled up"', (await said(b, 'Asha has filled up')) === 2);
    await ctx.close();
  }

  // ================= Google Places, and falling back to OpenStreetMap =================
  {
    const ctx = await newCtx('TEST-KEY');
    const a = await page(ctx, 'Asha', 'web-a', { mapKind: 'osm' });
    await a.evaluate(() => {
      window.__gPlaces = [
        { id: 'g1', name: 'Shell Kothrud', lat: 18.5100, lng: 73.8500 }, // in both searches
        { id: 'g2', name: 'Bharat Petroleum', lat: 18.5800, lng: 73.8510 }, // only in the one ahead
        { id: 'g3', name: 'Old Pump', lat: 18.4900, lng: 73.8500 }, // behind
      ];
    });
    await move(a, 18.5000, 73.8500);
    await a.waitForTimeout(200);
    const before = osm.asked.length;
    await a.click('[data-find="fuel"]');
    await a.waitForSelector('#findSheet .fplace', { timeout: 8000 });
    const req = await a.evaluate(() => window.__gReq);
    check('Google: two searches, 3 km around me and 9 km around a point 9 km ahead', req.length === 2 && req[0].locationRestriction.radius === 3000 &&
      req[1].locationRestriction.radius === 9000 && Math.abs(req[1].locationRestriction.center.lat - 18.5809) < 0.002 && Math.abs(req[1].locationRestriction.center.lng - 73.85) < 0.001);
    check('Google: petrol stations, 20 each, nearest first, name / location / id', req.every((r) => JSON.stringify(r.includedTypes) === '["gas_station"]' && r.maxResultCount === 20 &&
      r.rankPreference === 'DISTANCE' && JSON.stringify(r.fields) === '["displayName","location","id"]'));
    check('Merged (Shell once), behind left out, no OpenStreetMap', JSON.stringify(await a.$$eval('#findSheet .fplace .n', (els) => els.map((e) => e.textContent))) === '["Shell Kothrud","Bharat Petroleum"]' &&
      osm.asked.length === before);
    check('"Search by Google Maps."', (await a.textContent('#findSheet')).includes('Search by Google Maps.'));
    await a.click('#findClose');
    await a.click('[data-find="food"]');
    await a.waitForFunction(() => /Nothing found/.test(document.getElementById('findSheetIn').textContent), null, { timeout: 5000 });
    check('Google food: restaurants and cafés', JSON.stringify((await a.evaluate(() => window.__gReq))[2].includedTypes) === '["restaurant","cafe"]');
    await a.click('#findClose');
    // A second rider's page where Google's Places isn't switched on: OpenStreetMap, and Google isn't asked again.
    osm.fuel = [node(21, 18.5200, 73.8500, { name: 'OSM Pump' })];
    const b = await page(ctx, 'Bilal', 'web-b', { mapKind: 'osm' });
    await b.evaluate(() => { window.__gFail = true; });
    await move(b, 18.5000, 73.8500);
    await b.waitForTimeout(200);
    await b.click('[data-find="fuel"]');
    await b.waitForSelector('#findSheet .fplace', { timeout: 8000 });
    check('Google fails: OpenStreetMap answers ("OSM Pump", "Search by OpenStreetMap.")', (await b.textContent('#findSheet .fplace .n')) === 'OSM Pump' &&
      (await b.textContent('#findSheet')).includes('Search by OpenStreetMap.') && osm.asked.length === before + 1);
    await b.click('#findClose');
    const g = (await b.evaluate(() => window.__gReq)).length;
    await b.click('[data-find="fuel"]');
    await b.waitForSelector('#findSheet .fplace', { timeout: 8000 });
    check('Next search: straight to OpenStreetMap (Google remembered as failed)', (await b.evaluate(() => window.__gReq)).length === g && osm.asked.length === before + 2);
    await ctx.close();
  }

  // ================= Rain alerts =================
  {
    const iso = (ms) => new Date(ms).toISOString().slice(0, 16);
    // A forecast for one place: rain (mm) per 15 minutes from now, and the chance (%) per hour.
    const place = (now, rain, chance) => {
      const t0 = Math.floor(now / 60000) * 60000, h0 = Math.floor(t0 / 3600000) * 3600000;
      return { latitude: 18.5, longitude: 73.85, minutely_15: { time: rain.map((_, i) => iso(t0 + i * 900000)), precipitation: rain },
        hourly: { time: [0, 1, 2, 3].map((i) => iso(h0 + i * 3600000)), precipitation_probability: [0, 1, 2, 3].map(() => chance) } };
    };
    const DRY = [0, 0, 0, 0, 0, 0, 0, 0];
    let pageNow = () => Date.now();
    let plan = { here: DRY, ahead: DRY, chance: 70 };
    meteo.answer = (u) => {
      const n = u.searchParams.get('latitude').split(',').length, now = pageNow();
      const list = [place(now, plan.here, plan.chance), place(now, plan.ahead, plan.chance)].slice(0, n);
      return n === 1 ? list[0] : list;
    };
    const ctx = await newCtx();
    const a = await page(ctx, 'Asha', 'web-a', { rain: true, history: false });
    pageNow = () => Date.now() + 0;
    const shiftOf = async () => a.evaluate(() => window.__shift);
    // Already raining here: nothing to say.
    plan = { here: [0.6, 0.4, 0, 0, 0, 0, 0, 0], ahead: [0, 0, 0, 1, 0, 0, 0, 0], chance: 70 };
    await move(a, 18.5000, 73.8500);
    await a.waitForFunction(() => true, null, { timeout: 100 });
    await a.waitForTimeout(500);
    const u = meteo.asked[meteo.asked.length - 1];
    check('Rain on: asks Open-Meteo once for here and 25 km ahead (rounded to 0.01°)', meteo.asked.length === 1 && u.searchParams.get('latitude') === '18.50,18.72' &&
      u.searchParams.get('longitude') === '73.85,73.85');
    check('Open-Meteo: 15-minute rain for 2 hours, hourly chance, GMT', u.searchParams.get('minutely_15') === 'precipitation' && u.searchParams.get('hourly') === 'precipitation_probability' &&
      u.searchParams.get('forecast_minutely_15') === '8' && u.searchParams.get('forecast_hours') === '3' && u.searchParams.get('timezone') === 'GMT');
    check('Already raining here: nothing said, no pill', !(await spoken(a)).some((t) => t.startsWith('Rain')) && await a.isHidden('#rainPill'));
    // 16 minutes later: rain here in half an hour.
    plan = { here: [0, 0, 0.8, 1.2, 0, 0, 0, 0], ahead: DRY, chance: 70 };
    let sh = 16 * 60;
    pageNow = () => Date.now() + sh * 1000;
    await move(a, 18.5010, 73.8500, 15, 0, 16 * 60);
    check('"Rain expected here in about 30 minutes"', await waitSaid(a, 'Rain expected here in about 30 minutes'));
    await a.waitForTimeout(200);
    check('Pill next to the connection status: "Rain in ~30 min"', (await a.textContent('#rainPill')) === 'Rain in ~30 min' && await a.isVisible('#rainPill'));
    await shot(a, 'road5_rain_classic.png');
    // Before 15 minutes: no new request.
    await move(a, 18.5020, 73.8500, 15, 0, 60);
    await a.waitForTimeout(200);
    check('Not asked again within 15 minutes', meteo.asked.length === 2);
    // 16 more minutes: the same rain isn't said again.
    sh += 60 + 16 * 60;
    plan = { here: [0, 0.8, 1.2, 0, 0, 0, 0, 0], ahead: DRY, chance: 70 };
    await move(a, 18.5030, 73.8500, 15, 0, 16 * 60);
    await a.waitForTimeout(500);
    check('Asked again after 15 minutes, same alert not repeated', meteo.asked.length === 3 && (await spoken(a)).filter((t) => t.startsWith('Rain expected here')).length === 1);
    check('Pill updated: "Rain in ~15 min"', (await a.textContent('#rainPill')) === 'Rain in ~15 min');
    // Dry here, rain ahead within the hour.
    sh += 16 * 60;
    plan = { here: DRY, ahead: [0, 0, 0, 0.9, 1, 0, 0, 0], chance: 55 };
    await move(a, 18.5040, 73.8500, 15, 0, 16 * 60);
    check('"Rain ahead in the next hour, about 25 kilometres on"', await waitSaid(a, 'Rain ahead in the next hour, about 25 kilometres on'));
    await a.waitForTimeout(200);
    check('Pill: "Rain ahead"', (await a.textContent('#rainPill')) === 'Rain ahead');
    for (const look of ['glass', 'neu']) { await setLook(a, look); await shot(a, 'road5_rain_' + look + '.png'); }
    // Wet but unlikely (30 %): no alert, the pill goes.
    sh += 16 * 60;
    plan = { here: [0, 0, 0.5, 0, 0, 0, 0, 0], ahead: DRY, chance: 30 };
    await move(a, 18.5050, 73.8500, 15, 0, 16 * 60);
    await a.waitForTimeout(500);
    check('A 30 % chance: no alert, pill hidden', meteo.asked.length === 5 && (await spoken(a)).filter((t) => t.startsWith('Rain')).length === 2 && await a.isHidden('#rainPill'));
    check('Shift sanity', (await shiftOf()) === sh * 1000);
    // Bilal: standing still, no destination, only Rain alerts needs his location: one place, an hour away.
    plan = { here: [0, 0, 0, 0, 0.4, 0.5, 0, 0], ahead: DRY, chance: 80 };
    pageNow = () => Date.now();
    const n0 = meteo.asked.length;
    const b = await page(ctx, 'Bilal', 'web-b', { rain: true, history: false });
    await move(b, 18.6000, 73.9000, 0, NaN);
    check('No direction: just here, "Rain expected here in about 60 minutes"', await waitSaid(b, 'Rain expected here in about 60 minutes') &&
      meteo.asked[n0].searchParams.get('latitude') === '18.60' && meteo.asked[n0].searchParams.get('longitude') === '73.90');
    // Switching it off: pill gone, no more requests.
    await b.click('#ride [data-open-settings]');
    await b.click('label:has([data-setting="rain"])');
    await b.click('#settingsClose');
    check('Rain alerts off: pill hidden', await b.isHidden('#rainPill'));
    await b.evaluate(() => { window.__shift += 20 * 60000; });
    await move(b, 18.6010, 73.9000, 0, NaN);
    await b.waitForTimeout(300);
    check('Rain alerts off: no more weather requests', meteo.asked.length === n0 + 1);
    await ctx.close();
  }

  // ================= Settings =================
  {
    const ctx = await newCtx();
    const a = await page(ctx, 'Asha', 'web-a', {});
    const b = await page(ctx, 'Bilal', 'web-b', {});
    await a.click('#ride [data-open-settings]');
    const txt = await a.textContent('#toggles');
    check('Settings: "Fuel & food finder" with its description', txt.includes('Fuel & food finder') && txt.includes('Find petrol, food or a mechanic on the road ahead, and tap Low fuel to be warned at the next petrol pump ahead. Searches send your location to Google Maps or OpenStreetMap only when you use them.'));
    check('Settings: "Rain alerts" with its description', txt.includes('Rain alerts') && txt.includes('Hear when rain is expected where you are or on the road ahead. Every 15 minutes your rough location (about 1 km) goes to the free Open-Meteo weather service.'));
    check('Finder on and rain off by default', await a.isChecked('[data-setting="finder"]') && !(await a.isChecked('[data-setting="rain"]')));
    await shot(a, 'road6_settings_classic.png', '[data-setting="finder"]');
    await a.click('label:has([data-setting="finder"])');
    await a.click('#settingsClose');
    check('Finder off: no card', (await a.innerHTML('#roadCard')) === '');
    await b.click('#lowFuel');
    await b.waitForTimeout(500);
    check('Finder off: "Bilal is low on fuel" not said', (await said(a, 'Bilal is low on fuel')) === 0);
    await a.click('#ride [data-open-settings]');
    await a.click('label:has([data-setting="finder"])');
    await a.click('#settingsClose');
    check('Finder back on: the card is back', !!(await a.$('#roadCard [data-find="fuel"]')));
    for (const look of ['glass', 'neu']) {
      await setLook(a, look);
      await a.click('#ride [data-open-settings]');
      await shot(a, 'road6_settings_' + look + '.png', '[data-setting="finder"]');
      await a.click('#settingsClose');
    }
    await ctx.close();
  }

  await browser.close();
  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  process.exit(fails || errors.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
