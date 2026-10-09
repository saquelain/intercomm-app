// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
// Screenshots go to tools/webtest/out/ (gitignored).
const OUT = __dirname + '/out/';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 400, height: 860 }, deviceScaleFactor: 2 });
  // The page's Google key only works on the real site: test the OpenStreetMap map here (gmap.test.js does Google).
  await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
    const res = await r.fetch();
    r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
  });
  // Same browser context: both tabs share the BroadcastChannel "ride".
  await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
  await ctx.route(/sandbox\/connection-details/, async (r) => {
    const b = JSON.parse(r.request().postData());
    r.fulfill({ contentType: 'application/json', body: JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') }) });
  });
  // Leaflet through the proxy is slow; use a local copy when there is one (see README.md here).
  await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
    const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
    return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
  });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
  await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
  await ctx.addInitScript(() => {
    window.__spoken = [];
    speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
    navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: { latitude: 12.9716, longitude: 77.5946 } });
    // Fake GPS: the test sets window.__fix; watchers get it (and again whenever it changes).
    window.__watchers = [];
    navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
    navigator.geolocation.clearWatch = () => {};
    window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
    window.confirm = () => true;
  });
  const errors = [];
  const mk = async (name, id) => {
    const page = await ctx.newPage();
    page.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await page.addInitScript((i) => localStorage.setItem('rc-web-id', i), id);
    await page.goto('http://127.0.0.1:8765/ride/?code=TSTWEB');
    await page.fill('#nameIn', name);
    await page.click('#joinBtn');
    await page.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return page;
  };
  const spoken = (p) => p.evaluate(() => window.__spoken.splice(0));
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);

  const a = await mk('Asha', 'web-a');
  await a.screenshot({ path: OUT + 'w1_alone.png' });
  const b = await mk('Bilal', 'web-b');
  await a.waitForTimeout(500);
  check('A hears Bilal joined', (await spoken(a)).some((t) => t === 'Bilal joined the ride'));
  check('A shows 2 riders', (await a.textContent('#statusText')).includes('2 riders'));
  check('B lists Asha', (await b.textContent('#riders')).includes('Asha'));
  await spoken(b);

  // Quick message + vote from A; B votes yes -> approved.
  await a.click('[data-say="SLOW_DOWN"]');
  await a.click('[data-vote="FUEL"]');
  await b.waitForTimeout(300);
  const bs = await spoken(b);
  check('B hears slow down', bs.includes('Asha says slow down'));
  check('B hears fuel vote', bs.some((t) => t.startsWith('Asha wants to stop for fuel')));
  await b.screenshot({ path: OUT + 'w2_vote.png' });
  await b.click('[data-cast="yes"]');
  await a.waitForTimeout(300);
  check('A hears fuel approved', (await spoken(a)).some((t) => t.startsWith('Fuel stop approved. 2 yes')));
  check('B hears fuel approved', (await spoken(b)).some((t) => t.startsWith('Fuel stop approved. 2 yes')));

  // B loses signal; A says "wait for me" meanwhile; B comes back and catches up.
  await b.evaluate(() => window.__room.goOffline());
  await b.waitForTimeout(200);
  await a.click('[data-say="WAIT"]');
  await a.click('[data-vote="BREAK"]');
  await a.waitForTimeout(300);
  await b.evaluate(() => window.__room.goOnline());
  await b.waitForTimeout(500);
  const caught = await spoken(b);
  check('B does not hear the old quick message', !caught.some((t) => t.includes('wait for me')));
  check('B gets the still-open vote', caught.some((t) => t.startsWith('While you were offline, Asha wants a break')));
  check('B can still vote on it', (await b.$('[data-cast="no"]')) !== null);
  await b.click('[data-cast="no"]');
  await a.waitForTimeout(300);
  check('A counts B\'s late vote', (await spoken(a)).some((t) => t.startsWith('Break not approved. 1 yes, 1 no')));
  await spoken(b);

  // SOS from B reaches A with location; I'm OK clears it.
  await b.click('#sosBtn');
  await b.screenshot({ path: OUT + 'w3_countdown.png' });
  await b.waitForTimeout(5600);
  await a.waitForTimeout(300);
  check('A hears SOS', (await spoken(a)).some((t) => t === 'S O S. Bilal needs help.'));
  check('A has map link', (await a.innerHTML('#alerts')).includes('maps.google.com/?q=12.9716,77.5946'));
  await a.screenshot({ path: OUT + 'w4_sos.png' });
  await b.screenshot({ path: OUT + 'w5_my_sos.png' });
  await b.click('#okBtn');
  await a.waitForTimeout(300);
  check('A hears Bilal OK', (await spoken(a)).includes('Bilal is OK'));
  check('A alert cleared', (await a.textContent('#alerts')).trim() === '');

  // Profile photo: A picks one in Settings, B sees it on A's card.
  await a.click('[data-open-settings]:visible');
  await a.setInputFiles('#photoFile', __dirname + '/face.png');
  await a.waitForTimeout(400);
  await a.screenshot({ path: OUT + 'w8_settings.png' });
  check('B sees A\'s photo', /background-image:url\(blob:/.test(await b.innerHTML('#riders')));

  // Group map: both switch it on in Settings and get a GPS position 2.4 km apart.
  await a.evaluate(() => window.__move({ latitude: 12.7350, longitude: 77.3000, accuracy: 8, speed: 16, heading: 40 }));
  await b.evaluate(() => window.__move({ latitude: 12.7160, longitude: 77.2870, accuracy: 8, speed: 15, heading: 35 }));
  await a.click('label:has([data-setting="map"])');
  await a.click('#settingsClose');
  await b.click('[data-open-settings]:visible');
  await b.click('label:has([data-setting="map"])');
  await b.click('#settingsClose');
  await a.waitForTimeout(800);
  check('A map card shows both riders', (await a.textContent('#mapCard')).includes('2 of 2 riders on the map'));
  check('A sees Bilal\'s distance on his card', /Bilal[\s\S]*2\.\d km/.test(await a.textContent('#riders')));
  await a.click('[data-open-map]');
  await a.waitForSelector('#map .leaflet-marker-icon', { timeout: 10000 });
  await a.waitForTimeout(800);
  await a.screenshot({ path: OUT + 'w9_map.png' });
  // Long-press (right-click on desktop) on the map to set a regroup point.
  await a.mouse.click(260, 360, { button: 'right' });
  await a.waitForTimeout(300);
  await a.click('[data-label="Dhaba"]');
  await a.click('#pinSet');
  await b.waitForTimeout(400);
  const heard = await spoken(b);
  check('B hears regroup with distance', heard.some((t) => /^Asha set a regroup point: Dhaba, [\d.]+ (kilo)?meters/.test(t)));
  check('B map card shows regroup + Navigate', (await b.innerHTML('#mapCard')).includes('Regroup: Dhaba') && (await b.innerHTML('#mapCard')).includes('google.com/maps/dir'));
  await a.waitForTimeout(500);
  await a.screenshot({ path: OUT + 'w10_map_regroup.png' });
  await b.screenshot({ path: OUT + 'w11_card_regroup.png' });
  await a.click('#mapStyle');
  await a.waitForTimeout(300);
  check('Map switches to dark', await a.evaluate(() => document.getElementById('map').classList.contains('dark') && JSON.parse(localStorage.getItem('rc-web-settings')).darkMap === true));
  await a.screenshot({ path: OUT + 'w12_map_dark.png' });
  await a.click('#mapStyle');
  // B rides to the point: arrival counted, everyone there announced.
  const pin = await a.evaluate(() => ({ lat: 0 }));
  await spoken(a);
  const g = await b.evaluate(() => document.querySelector('#mapCard a').href.match(/destination=([\d.]+),([\d.]+)/).slice(1).map(Number));
  await a.evaluate((g) => window.__move({ latitude: g[0], longitude: g[1], accuracy: 6, speed: 0 }), g);
  await b.evaluate((g) => window.__move({ latitude: g[0] + 0.0004, longitude: g[1], accuracy: 6, speed: 0 }), g);
  await a.waitForTimeout(500);
  const arrivedA = await spoken(a);
  check('A hears arrival count', arrivedA.some((t) => /You're at the regroup point\. \d of 2 riders here\./.test(t)));
  check('Everyone there announced', arrivedA.includes('Everyone is at the regroup point') || (await spoken(b)).includes('Everyone is at the regroup point'));
  // Switching the map off hides it and stops sharing, mid-ride.
  await a.click('#mapBack');
  await a.click('[data-open-settings]:visible');
  await a.click('label:has([data-setting="map"])');
  await a.click('#settingsClose');
  check('Map card hidden after switching off', await a.evaluate(() => document.getElementById('mapCard').classList.contains('hidden')));

  // Mute shows on the other side; leaving says bye.
  await b.click('#micBtn');
  await a.waitForTimeout(200);
  check('A sees Bilal mic off', (await a.textContent('#riders')).includes('Mic off'));
  await b.click('#leaveBtn');
  // With Home safe on, leaving asks first: plain Leave here.
  await b.click('#leaveGo');
  await a.waitForTimeout(500);
  check('A hears Bilal left (not dropped)', (await spoken(a)).includes('Bilal left the ride'));
  await a.screenshot({ path: OUT + 'w6_after.png' });

  console.log(errors.length ? 'PAGE ERRORS:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
})();
