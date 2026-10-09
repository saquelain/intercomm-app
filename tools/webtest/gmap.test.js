// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// The Group map on Google Maps: the page is served with a stand-in key, and Google's script is loaded
// without it (Google's keyless "for development purposes only" map), so the real Google map draws.
// Then a key Google refuses: the map falls back to OpenStreetMap by itself. Last, the page's real key, with
// the page served at its real address (the key only works there): the actual Google map, as riders see it.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const SITE = 'https://saquelain.github.io/intercomm-app/ride/';
  const run = async (googleKey, keyless, site) => {
    const base = site ? SITE : 'http://127.0.0.1:8765/ride/';
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) }, deviceScaleFactor: 2 });
    const pass = (b) => JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') });
    await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
    await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
    await ctx.route((u) => u.href.startsWith(base) && !u.pathname.endsWith('.png'), async (r) => {
      const res = await r.fetch({ url: 'http://127.0.0.1:8765/ride/' });
      const html = await res.text();
      r.fulfill({ contentType: 'text/html', body: googleKey == null ? html : html.replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '" + googleKey + "';") });
    });
    if (keyless) {
      await ctx.route(/maps\.googleapis\.com\/maps\/api\/js\?/, (r) => r.continue({ url: r.request().url().replace(/key=[^&]*&/, '') }));
    }
    await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
      const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
      return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
    });
    const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
    await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
    await ctx.addInitScript(() => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      window.__watchers = [];
      navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
      navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: window.__fix || { latitude: 18.52, longitude: 73.85 } });
      navigator.geolocation.clearWatch = () => {};
      window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
    });
    const errors = [];
    const mk = async (name, id, settings) => {
      const page = await ctx.newPage();
      page.on('pageerror', (e) => errors.push(name + ': ' + e.message));
      await page.addInitScript(([i, s]) => {
        if (sessionStorage.getItem('set-up')) return;
        sessionStorage.setItem('set-up', '1');
        localStorage.setItem('rc-web-id', i);
        localStorage.setItem('rc-web-settings', s);
      }, [id, JSON.stringify(settings)]);
      await page.goto(base + '?code=GMAPTS');
      await page.fill('#nameIn', name);
      await page.click('#joinBtn');
      await page.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
      return page;
    };
    const a = await mk('Asha', 'web-a', { map: true, look: 'neu' });
    const b = await mk('Bilal', 'web-b', { map: true });
    await a.evaluate(() => window.__move({ latitude: 18.5204, longitude: 73.8567, accuracy: 12, speed: 0 }));
    await b.evaluate(() => window.__move({ latitude: 18.5310, longitude: 73.8446, accuracy: 12, speed: 0 }));
    await a.waitForTimeout(800);
    return { ctx, a, b, errors };
  };
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);

  // ---- Google's map, keyless (development) mode ----
  {
    const { ctx, a, errors } = await run('TESTKEY', true);
    await a.click('[data-open-settings]:visible');
    const settingsText = await a.textContent('#choices');
    await a.click('#settingsClose');
    check('Settings offers Google Maps / OpenStreetMap', settingsText.includes('Google Maps') && settingsText.includes('OpenStreetMap') && settingsText.includes('looks like the Google Maps app'));
    await a.click('[data-open-map]');
    await a.waitForSelector('#map .gm-style', { timeout: 20000 });
    await a.waitForSelector('#map .mk.me', { timeout: 10000 });
    await a.waitForTimeout(2500);
    check('Google map is showing', await a.evaluate(() => !!document.querySelector('#map .gm-style') && document.getElementById('mapView').classList.contains('google')));
    check('Both riders on it', (await a.innerHTML('#map')).includes('Bilal') && (await a.innerHTML('#map')).includes('>You<'));
    check('OpenStreetMap credit hidden', await a.evaluate(() => getComputedStyle(document.querySelector('.attrib')).display === 'none'));
    check('Map ends above the bottom panel', await a.evaluate(() => {
      const m = document.getElementById('map').getBoundingClientRect(), p = document.querySelector('.mappanel').getBoundingClientRect();
      return m.bottom <= p.top && m.bottom > 300;
    }));
    // Riders are fitted in view, below the top bar.
    check('Riders fitted in view', await a.evaluate(() => Array.from(document.querySelectorAll('#map .mk')).every((e) => {
      const r = e.getBoundingClientRect(); return r.top > 90 && r.bottom < document.getElementById('map').getBoundingClientRect().bottom && r.left >= 0 && r.right <= innerWidth;
    })));
    await a.screenshot({ path: OUT + 'w50_gmap.png' });
    // A long press with a finger (Google has no such event; the page times it) asks for a regroup point.
    await a.evaluate(() => {
      const t = document.querySelector('#map .gm-style');
      t.dispatchEvent(new PointerEvent('pointerdown', { pointerType: 'touch', isPrimary: true, clientX: 200, clientY: 420, bubbles: true }));
    });
    await a.waitForTimeout(900);
    check('Long press opens the regroup dialog', await a.isVisible('#pinDialog'));
    await a.evaluate(() => document.querySelector('#map .gm-style').dispatchEvent(new PointerEvent('pointerup', { pointerType: 'touch', isPrimary: true, bubbles: true })));
    await a.click('[data-label="Dhaba"]');
    await a.click('#pinSet');
    await a.waitForSelector('#map .pin', { timeout: 5000 });
    check('Regroup pin on the Google map', (await a.innerHTML('#map')).includes('Dhaba'));
    // A press that moves (a drag) doesn't.
    await a.evaluate(() => {
      const t = document.querySelector('#map .gm-style');
      t.dispatchEvent(new PointerEvent('pointerdown', { pointerType: 'touch', isPrimary: true, clientX: 200, clientY: 420, bubbles: true }));
      t.dispatchEvent(new PointerEvent('pointermove', { pointerType: 'touch', isPrimary: true, clientX: 240, clientY: 460, bubbles: true }));
    });
    await a.waitForTimeout(900);
    check('Dragging doesn\'t open it', !(await a.isVisible('#pinDialog')));
    // Tapping a rider centres on them.
    await a.click('#map .mk:not(.me)');
    await a.waitForTimeout(1200);
    check('Tap a rider: centred on them', await a.evaluate(() => {
      const m = document.getElementById('map').getBoundingClientRect(), r = document.querySelector('#map .mk:not(.me) .face').getBoundingClientRect();
      return Math.abs((r.left + r.right) / 2 - (m.left + m.right) / 2) < 30 && Math.abs((r.top + r.bottom) / 2 - (m.top + m.bottom) / 2) < 30;
    }));
    await a.click('#mapStyle');
    await a.waitForTimeout(1200);
    check('Dark map', await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-settings')).darkMap === true && document.getElementById('map').classList.contains('dark')));
    await a.screenshot({ path: OUT + 'w51_gmap_dark.png' });
    await a.click('#mapStyle');
    // Switch to OpenStreetMap in Settings, mid-ride: the map is rebuilt as Leaflet.
    await a.click('#mapBack');
    await a.click('[data-open-settings]:visible');
    await a.click('[data-choice="mapKind"][data-value="osm"]');
    await a.click('#settingsClose');
    await a.click('[data-open-map]');
    await a.waitForSelector('#map .leaflet-marker-icon', { timeout: 10000 });
    await a.waitForTimeout(600);
    check('Switched to OpenStreetMap', await a.evaluate(() => !document.querySelector('#map .gm-style') && !document.getElementById('mapView').classList.contains('google') && getComputedStyle(document.querySelector('.attrib')).display !== 'none'));
    check('Regroup pin kept', (await a.innerHTML('#map')).includes('Dhaba'));
    await a.screenshot({ path: OUT + 'w52_back_to_osm.png' });
    check('No page errors (Google)', errors.length === 0);
    if (errors.length) console.log(errors.join('\n'));
    await ctx.close();
  }

  // ---- A key Google refuses: falls back to OpenStreetMap with a note ----
  {
    const { ctx, a, errors } = await run('NOT-A-REAL-KEY', false);
    await a.click('[data-open-map]');
    await a.waitForSelector('#map .leaflet-marker-icon', { timeout: 30000 });
    await a.waitForTimeout(500);
    check('Refused key: OpenStreetMap instead', await a.evaluate(() => !document.querySelector('#map .gm-style') && !!document.querySelector('#map.leaflet-container')));
    check('Refused key: rider told', (await a.textContent('#banner')).includes("didn't accept this page's key"));
    await a.screenshot({ path: OUT + 'w53_refused_key.png' });
    check('No page errors (refused key)', errors.length === 0);
    if (errors.length) console.log(errors.join('\n'));
    await ctx.close();
  }
  // ---- The page's own key, at the real address: Google's normal map ----
  {
    const { ctx, a, errors } = await run(null, false, true);
    await a.click('[data-open-map]');
    await a.waitForSelector('#map .gm-style', { timeout: 20000 });
    await a.waitForSelector('#map .mk.me', { timeout: 10000 });
    await a.waitForTimeout(3500);
    check('Real key: Google map, not refused', await a.evaluate(() => !!document.querySelector('#map .gm-style') && !document.getElementById('banner').textContent.includes("didn't accept")));
    await a.screenshot({ path: OUT + 'w54_gmap_real.png' });
    await a.click('#mapStyle');
    await a.waitForTimeout(2500);
    await a.screenshot({ path: OUT + 'w55_gmap_real_dark.png' });
    check('No page errors (real key)', errors.length === 0);
    if (errors.length) console.log(errors.join('\n'));
    await ctx.close();
  }
  await browser.close();
})();
