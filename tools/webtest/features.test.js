// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
// Screenshots go to tools/webtest/out/ (gitignored).
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) }, deviceScaleFactor: 2 });
  // The page's Google key only works on the real site: test the OpenStreetMap map here (gmap.test.js does Google).
  await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
    const res = await r.fetch();
    r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
  });
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
    speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); window.__vol = u.volume; };
    navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: { latitude: 12.9716, longitude: 77.5946 } });
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
    await page.addInitScript((i) => { localStorage.setItem('rc-web-id', i); localStorage.removeItem('rc-web-settings'); localStorage.removeItem('rc-web-info'); localStorage.removeItem('rc-web-vol'); }, id);
    await page.goto('http://127.0.0.1:8765/ride/?code=TSTWEB');
    await page.fill('#nameIn', name);
    await page.click('#joinBtn');
    await page.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return page;
  };
  const spoken = (p) => p.evaluate(() => window.__spoken.splice(0));
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);
  const settings = async (p, fn) => { await p.click('[data-open-settings]:visible'); await fn(); await p.click('#settingsClose'); };

  const a = await mk('Asha', 'web-a');
  const b = await mk('Bilal', 'web-b');
  await a.waitForTimeout(400);
  await spoken(a); await spoken(b);

  // Emergency info goes out with the SOS; my own screen shows it for helpers.
  await settings(a, async () => {
    await a.click('[data-blood="O+"]');
    await a.fill('#infoMedical', 'Allergic to penicillin');
    await a.fill('#infoContact', 'Ammi');
    await a.fill('#infoPhone', '+91 98450 12345');
    await a.screenshot({ path: OUT + 'w19_settings_info.png', fullPage: false });
    await a.evaluate(() => document.querySelector('#settings .sheet-in').scrollTo(0, 99999));
    await a.screenshot({ path: OUT + 'w19b_settings_bottom.png' });
  });
  await a.click('#sosBtn');
  await a.waitForTimeout(5800);
  await b.waitForTimeout(300);
  const alertHtml = await b.innerHTML('#alerts');
  check('B sees A\'s blood group with the SOS', alertHtml.includes('O+') && alertHtml.includes('penicillin'));
  check('B can call A\'s contact', alertHtml.includes('tel:+919845012345') && alertHtml.includes('Call Ammi'));
  check('A\'s own screen shows the helper card', (await a.textContent('#helper')).includes('Call 112') && (await a.textContent('#helper')).includes('O+'));
  await a.screenshot({ path: OUT + 'w17_sos_helper.png' });
  await b.screenshot({ path: OUT + 'w18_alert_info.png' });
  await a.click('#okBtn');
  await b.waitForTimeout(300);
  check('Helper card goes once OK', await a.evaluate(() => document.getElementById('helper').classList.contains('hidden')));
  await spoken(a); await spoken(b);

  // Hazards without the group map: A marks a pothole; B, 1 km behind, is warned as they get close.
  // B's location is already on (Ride history records the route), so the fix arrives at once.
  await b.evaluate(() => window.__move({ latitude: 12.9626, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await a.click('[data-open-hazards]');
  await a.screenshot({ path: OUT + 'w13_hazard_picker.png' });
  await a.click('[data-hazard="POTHOLE"]');
  await b.waitForTimeout(400);
  check('A hears it was marked', (await spoken(a)).includes('Pothole marked for the group'));
  check('B hears it was marked', (await spoken(b)).some((t) => t.startsWith('Asha marked: Pothole')));
  check('B lists it 1 km ahead', /Pothole[\s\S]*1\.0 km ahead/.test(await b.textContent('#hazCard')));
  await b.evaluate(() => window.__move({ latitude: 12.9690, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await b.waitForTimeout(200);
  const warn = await spoken(b);
  check('B warned "Pothole in 290 meters"', warn.some((t) => /^Pothole in (280|290|300) meters$/.test(t)));
  await b.evaluate(() => window.__move({ latitude: 12.9700, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await b.waitForTimeout(200);
  check('B warned only once', !(await spoken(b)).some((t) => t.startsWith('Pothole in')));
  await b.screenshot({ path: OUT + 'w13b_hazard_card.png' });

  // Lead & sweep: B makes A the lead.
  await b.click('#riders [data-rider="web-a"]');
  await b.waitForTimeout(200);
  await b.screenshot({ path: OUT + 'w14_rider_sheet.png' });
  await b.click('[data-role="lead"]');
  await a.waitForTimeout(300);
  check('A hears they are the lead', (await spoken(a)).includes('Bilal made you the lead'));
  check('A shows the LEAD tag on themselves', (await a.innerHTML('#riders')).includes('LEAD'));
  // Volume for me: 50%, then muted.
  await b.evaluate(() => { const r = document.getElementById('volRange'); r.value = 50; r.dispatchEvent(new Event('input', { bubbles: true })); });
  check('B hears Asha at 50%', (await b.textContent('#riders')).includes('50%'));
  await b.click('label:has(#volMute)');
  check('B muted Asha for themselves', (await b.textContent('#riders')).includes('muted for you'));
  await b.click('#sheetDone');
  check('Volume remembered', await b.evaluate(() => JSON.parse(localStorage.getItem('rc-web-vol'))['web-a'].m === true));

  // Group map on both: B gets 400 m ahead of the lead.
  await a.evaluate(() => window.__move({ latitude: 12.9716, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await settings(a, () => a.click('label:has([data-setting="map"])'));
  await settings(b, () => b.click('label:has([data-setting="map"])'));
  await a.waitForTimeout(300);
  await spoken(a); await spoken(b);
  await b.evaluate(() => window.__move({ latitude: 12.9755, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await a.waitForTimeout(5500); // positions go out every 5 s while moving
  await a.evaluate(() => window.__move({ latitude: 12.9717, longitude: 77.5946, accuracy: 8, speed: 15, heading: 0 }));
  await a.waitForTimeout(300);
  check('B hears they are ahead of the lead', (await spoken(b)).includes("You're ahead of the lead, Asha"));
  check('Lead hears it too', (await spoken(a)).includes('Bilal is ahead of you'));
  await b.click('[data-open-map]');
  await b.waitForSelector('#map .mkhz', { timeout: 10000 });
  await b.waitForTimeout(800);
  await b.screenshot({ path: OUT + 'w20_map_hazard_lead.png' });
  check('Map shows the pothole and the lead', (await b.innerHTML('#map')).includes('Pothole') && (await b.innerHTML('#map')).includes('Asha · Lead'));
  await b.click('#mapBack');

  // Push to talk on A.
  await settings(a, () => a.click('[data-choice="talk"][data-value="ptt"]'));
  check('PTT label', (await a.textContent('#micLabel')).includes('Hold to talk'));
  const box = await a.locator('#micBtn').boundingBox();
  await a.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await a.mouse.down();
  await a.waitForTimeout(150);
  check('Holding: talking', (await a.textContent('#micLabel')).includes('let go to stop'));
  await a.screenshot({ path: OUT + 'w15_ptt_talking.png' });
  await a.waitForTimeout(500);
  await a.mouse.up();
  check('Let go: silent again', (await a.textContent('#micLabel')).includes('Hold to talk'));
  await a.mouse.down(); await a.mouse.up();
  check('Tap: talking hands-free', (await a.textContent('#micLabel')).includes('tap to stop'));
  await a.mouse.down(); await a.mouse.up();
  check('Tap again: stopped', (await a.textContent('#micLabel')).includes('Hold to talk'));

  // Night mode on A.
  await settings(a, () => a.click('[data-choice="night"][data-value="on"]'));
  check('Night mode on', await a.evaluate(() => document.documentElement.classList.contains('night')));
  await spoken(a);
  await b.click('[data-say="SLOW_DOWN"]');
  await a.waitForTimeout(300);
  const sp = await a.evaluate(() => [window.__spoken.slice(), window.__vol]); check("Quieter voice at night", Math.abs(sp[1] - 0.55) < 0.001);
  await a.screenshot({ path: OUT + 'w16_night.png' });
  await settings(a, () => a.click('[data-choice="night"][data-value="off"]'));
  check('Night mode off', !(await a.evaluate(() => document.documentElement.classList.contains('night'))));

  // Hazard alerts off: card gone.
  await settings(b, () => b.click('label:has([data-setting="hazards"])'));
  check('Hazards off hides the card', (await b.innerHTML('#hazCard')) === '');

  console.log(errors.length ? 'PAGE ERRORS:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
})();
