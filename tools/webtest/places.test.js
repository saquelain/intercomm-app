// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Choosing a place (plan's meeting point / stops, the destination): results as you type from Photon, nearest
// first; Nominatim when Photon is down and Search is tapped; a full Google Maps link with the place's name;
// "Pick on the map" (move the map under the pin, the spot's name, Use this spot, Back); the Dark and Red
// night looks with the Soft look.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'http://127.0.0.1:8765/';

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const VIEW = { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) };
  const errors = [];
  let fails = 0;
  const check = (label, cond) => { if (!cond) fails++; console.log((cond ? 'PASS ' : 'FAIL ') + label); };
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
  const asked = { photon: [], nominatim: [], reverse: [] };
  let photonDown = false;
  const ctx = await browser.newContext({ viewport: VIEW, deviceScaleFactor: 2 });
  await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
    const res = await r.fetch();
    r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
  });
  await ctx.route(/livekit/, (r) => r.abort());
  const feature = (name, lon, lat, extra) => ({ geometry: { coordinates: [lon, lat] }, properties: Object.assign({ name, state: 'Maharashtra', country: 'India' }, extra || {}) });
  await ctx.route(/photon\.komoot\.io/, (r) => {
    const u = new URL(r.request().url());
    if (u.pathname.includes('reverse')) { asked.reverse.push(u); return r.fulfill({ contentType: 'application/json', body: JSON.stringify({ features: [feature('Tiger Point', 73.37, 18.71, { city: 'Lonavala' })] }) }); }
    asked.photon.push(u);
    if (photonDown) return r.fulfill({ status: 503, body: 'busy' });
    const q = (u.searchParams.get('q') || '').toLowerCase();
    const all = [feature('Shivam Dhaba', 73.38, 18.76, { street: 'NH48', city: 'Khandala' }), feature('Lonavala', 73.4062, 18.7546), feature('Lonavala Lake', 73.39, 18.74)];
    r.fulfill({ contentType: 'application/json', body: JSON.stringify({ features: all.filter((f) => f.properties.name.toLowerCase().includes(q.slice(0, 4))) }) });
  });
  await ctx.route(/nominatim\.openstreetmap\.org/, (r) => {
    asked.nominatim.push(new URL(r.request().url()));
    r.fulfill({ contentType: 'application/json', body: JSON.stringify([{ lat: '18.52', lon: '73.85', name: 'Swargate', display_name: 'Swargate, Pune, Maharashtra, India' }]) });
  });
  await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
    const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
    return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
  });
  await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
  const p = await ctx.newPage();
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(BASE + 'ride/');
  const results = () => p.$$eval('#destResults [data-place] .n', (els) => els.map((e) => e.textContent));

  await p.click('#planNew');
  await p.click('[data-plan-pick="meet"]');
  check('Place sheet: search box, Pick on the map, paste hint', (await p.isVisible('#placePick')) && (await p.textContent('#destDialog')).includes('paste coordinates or a Google Maps link'));
  // Typing: results by themselves after a moment, from Photon.
  await p.type('#destQuery', 'lona', { delay: 40 });
  await p.waitForFunction(() => document.querySelectorAll('#destResults [data-place]').length > 0, null, { timeout: 4000 });
  check('As you type: Lonavala and Lonavala Lake', JSON.stringify(await results()) === JSON.stringify(['Lonavala', 'Lonavala Lake']));
  check('Results say where they came from', (await p.textContent('#destSource')) === 'Places from OpenStreetMap.');
  check('As you type uses Photon only, never Nominatim', asked.photon.length >= 1 && asked.nominatim.length === 0);
  await p.screenshot({ path: OUT + 'pl1_typing.png' });
  await p.fill('#destQuery', 'shivam dhaba');
  await p.click('#destSearch');
  await p.waitForFunction(() => document.querySelector('#destResults .n') && document.querySelector('#destResults .n').textContent === 'Shivam Dhaba');
  check('Search: a dhaba with its road and town', (await p.textContent('#destResults')).includes('NH48, Khandala, Maharashtra'));
  // Photon down: Search falls back to Nominatim.
  photonDown = true;
  await p.fill('#destQuery', 'swargate');
  await p.click('#destSearch');
  await p.waitForFunction(() => document.querySelector('#destResults .n') && document.querySelector('#destResults .n').textContent === 'Swargate');
  check('Photon down: Nominatim answers when Search is tapped', asked.nominatim.length === 1);
  photonDown = false;
  // A full Google Maps link: its pin and its name.
  await p.fill('#destQuery', 'https://www.google.com/maps/place/Tiger+Point/@18.7,73.3,15z/data=!3d18.7104!4d73.3712');
  await p.click('#destSearch');
  await p.waitForFunction(() => document.querySelector('#destResults .n'));
  check('Google Maps link: "Tiger Point" at its pin', (await results())[0] === 'Tiger Point' && (await p.textContent('#destResults')).includes('18.71040, 73.37120'));

  // Pick on the map.
  await p.click('#placePick');
  await p.waitForSelector('#pickMap .leaflet-tile', { timeout: 10000 });
  check('Map picker: hint, title, Use this spot', (await p.textContent('.pickhint')).includes('Move the map') && (await p.textContent('#pickTitle')) === 'Meeting point' && await p.isVisible('#pickUse'));
  await p.waitForFunction(() => document.getElementById('pickName').textContent === 'Tiger Point, Lonavala', null, { timeout: 4000 });
  check('Opens on the first result, named by a reverse lookup', (await p.textContent('#pickAt')) === '18.71040, 73.37120');
  const box = await p.$('#pickMap').then((e) => e.boundingBox());
  await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await p.mouse.down(); await p.mouse.move(box.x + box.width / 2 - 120, box.y + box.height / 2 + 80, { steps: 8 }); await p.mouse.up();
  await p.waitForTimeout(800);
  const at = (await p.textContent('#pickAt')).split(', ').map(Number);
  check('Dragging the map moves the pin\'s spot (east and north)', at[1] > 73.3712 && at[0] > 18.7104);
  await p.screenshot({ path: OUT + 'pl2_picker.png' });
  await p.click('#pickUse');
  check('Use this spot: picker closed, meeting point set', (await p.isHidden('#pickView')) && (await p.textContent('#planSheetIn')).includes('Tiger Point, Lonavala'));
  // Back from the picker returns to the search.
  await p.click('[data-plan-pick="stop"]');
  await p.click('#placePick');
  await p.waitForSelector('#pickMap.leaflet-container');
  await p.click('#pickBack');
  check('Back: the search sheet again', (await p.isHidden('#pickView')) && (await p.isVisible('#destDialog')));
  await p.click('#destCancel');

  // Night looks with the Soft look: Dark shows the dark page, Red the red layer.
  await p.evaluate(() => { const s = JSON.parse(localStorage.getItem('rc-web-settings') || '{}'); s.look = 'neu'; s.night = 'on'; localStorage.setItem('rc-web-settings', JSON.stringify(s)); });
  await p.reload();
  check('Night (Dark) with Soft: the dark Classic page, dimmed', await p.evaluate(() => { const c = document.documentElement.classList; return c.contains('night-dark') && !c.contains('look-neu') && getComputedStyle(document.getElementById('nightDim')).display === 'block'; }));
  await p.screenshot({ path: OUT + 'pl3_night_dark.png' });
  await p.evaluate(() => { const s = JSON.parse(localStorage.getItem('rc-web-settings')); s.nightStyle = 'red'; localStorage.setItem('rc-web-settings', JSON.stringify(s)); });
  await p.reload();
  check('Night (Red) keeps the Soft look under the red layer', await p.evaluate(() => { const c = document.documentElement.classList; return c.contains('night-red') && c.contains('look-neu'); }));

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
