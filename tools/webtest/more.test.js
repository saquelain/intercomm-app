// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Talk to one rider, shared destination, break reminder, home safe (and the later check-in),
// and rides locked to the group with a group key. Three riders: Asha, Bilal, Chetan.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
const OUT = __dirname + '/out/';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 400, height: 860 }, deviceScaleFactor: 2 });
  const pass = (b) => JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') });
  await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
  await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
  // The group's private ride server: only the right key gets a pass.
  let serverCalls = 0;
  await ctx.route(/ride\.example\.workers\.dev/, (r) => {
    serverCalls++;
    const ok = r.request().headers()['x-ridecomm-key'] === 'our secret';
    r.fulfill(ok ? { contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) } : { status: 401, contentType: 'application/json', body: '{"error":"wrong group key"}' });
  });
  await ctx.route(/nominatim\.openstreetmap\.org/, (r) => r.fulfill({ contentType: 'application/json', body: JSON.stringify([
    { lat: '18.7546', lon: '73.4062', name: 'Lonavala', display_name: 'Lonavala, Maval, Pune, Maharashtra, India' },
    { lat: '18.75', lon: '73.38', name: 'Lonavala Lake', display_name: 'Lonavala Lake, Pune, Maharashtra, India' },
  ]) }));
  await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
    const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
    return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
  });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
  await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
  await ctx.addInitScript(() => {
    window.__spoken = [];
    speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
    navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: { latitude: 18.52, longitude: 73.85 } });
    window.__watchers = [];
    navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
    navigator.geolocation.clearWatch = () => {};
    window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
    window.confirm = () => true;
    // Lets a test move this page's clock forward (break reminder).
    window.__shift = 0;
    const now = Date.now.bind(Date);
    Date.now = () => now() + window.__shift;
  });
  const errors = [];
  const mk = async (name, id, opts = {}) => {
    const page = await ctx.newPage();
    page.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await page.addInitScript(([i, settings]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', settings || '{}');
      ['rc-web-info', 'rc-web-vol', 'rc-web-home', 'rc-web-checkin', 'rc-web-key'].forEach((k) => localStorage.removeItem(k));
    }, [id, opts.settings]);
    await page.goto('http://127.0.0.1:8765/ride/?code=' + (opts.code || 'TSTMRE') + (opts.hash || ''));
    await page.fill('#nameIn', name);
    if (opts.noJoin) return page;
    await page.click('#joinBtn');
    await page.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return page;
  };
  const spoken = (p) => p.evaluate(() => window.__spoken.splice(0));
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);
  const volumeOf = (p, id) => p.evaluate((i) => window.__room.remoteParticipants.get(i).volume, id);

  const a = await mk('Asha', 'web-a');
  const b = await mk('Bilal', 'web-b');
  const c = await mk('Chetan', 'web-c');
  await a.waitForTimeout(400);
  await spoken(a); await spoken(b); await spoken(c);

  // ---- Talk to one rider: Asha holds "talk only to Bilal" in his sheet ----
  await a.click('[data-rider="web-b"]');
  await a.waitForSelector('[data-hold="web-b"]');
  await a.hover('[data-hold="web-b"]');
  await a.mouse.down();
  await a.waitForTimeout(700);
  check('Chetan no longer hears Asha', (await volumeOf(c, 'web-a')) === 0);
  check('Bilal still hears Asha', (await volumeOf(b, 'web-a')) === 1);
  check('Bilal sees "Asha is talking to you"', (await b.textContent('#whisperBar')).includes('Asha is talking to you'));
  check('Chetan sees who Asha talks to', (await c.textContent('#riders')).includes('Talking only to Bilal'));
  check('Asha sees "Only Bilal hears you"', (await a.textContent('#whisperBar')).includes('Only Bilal hears you'));
  await a.screenshot({ path: OUT + 'w30_talking_to_one.png' });
  await b.screenshot({ path: OUT + 'w31_talked_to.png' });
  await a.mouse.up();
  await a.waitForTimeout(1200);
  check('Chetan hears Asha again after she lets go', (await volumeOf(c, 'web-a')) === 1);
  check('Bilal can hold to reply', (await b.$('#whisperBar [data-hold="web-a"]')) !== null);
  await a.click('#sheetDone');
  // Bilal replies by holding the banner button.
  await b.hover('#whisperBar [data-hold="web-a"]');
  await b.mouse.down();
  await b.waitForTimeout(700);
  check('Reply: Chetan doesn\'t hear Bilal', (await volumeOf(c, 'web-b')) === 0);
  check('Reply: Asha hears Bilal', (await volumeOf(a, 'web-b')) === 1);
  await b.mouse.up();
  // Holding a rider's row works too.
  await a.hover('[data-rider="web-c"]');
  await a.mouse.down();
  await a.waitForTimeout(900);
  check('Holding Chetan\'s row: Bilal doesn\'t hear Asha', (await volumeOf(b, 'web-a')) === 0);
  await a.mouse.up();
  await a.waitForTimeout(1200);
  check('Holding a row doesn\'t open the sheet', await a.evaluate(() => document.getElementById('riderSheet').classList.contains('hidden')));
  check('Back to normal for Bilal', (await volumeOf(b, 'web-a')) === 1);
  await spoken(a); await spoken(b); await spoken(c);

  // ---- Shared destination: Asha searches "Lonavala" ----
  await b.evaluate(() => window.__move({ latitude: 18.52, longitude: 73.85, accuracy: 8, speed: 10, heading: 300 }));
  await a.click('[data-open-dest]');
  await a.fill('#destQuery', 'Lonavala');
  await a.click('#destSearch');
  await a.waitForSelector('[data-place="0"]');
  await a.screenshot({ path: OUT + 'w32_dest_search.png' });
  await a.click('[data-place="0"]');
  await b.waitForTimeout(1900);
  const bSaid = await spoken(b);
  check('Bilal hears the destination with distance', bSaid.some((t) => /^Asha set the destination: Lonavala, \d+\.\d kilometers away$/.test(t)));
  check('Bilal\'s card: Heading to Lonavala + Navigate', (await b.textContent('#destCard')).includes('Heading to Lonavala') &&
    (await b.innerHTML('#destCard')).includes('destination=18.7546,73.4062'));
  check('Bilal\'s card shows the distance', /\d+\.\d km away/.test(await b.textContent('#destCard')));
  await b.screenshot({ path: OUT + 'w33_dest_card.png' });
  // Coordinates pasted from a maps link skip the search.
  await c.click('[data-open-dest]');
  await c.fill('#destQuery', 'https://www.google.com/maps/@18.9000,73.5000,15z');
  await c.click('#destSearch');
  await c.waitForSelector('[data-place="0"]');
  check('Pasted link gives a pinned place', (await c.textContent('#destResults')).includes('18.90000, 73.50000'));
  await c.click('#destCancel');
  // Arriving.
  await b.evaluate(() => window.__move({ latitude: 18.7550, longitude: 73.4060, accuracy: 8, speed: 5, heading: 300 }));
  await b.waitForTimeout(200);
  check('Bilal hears he reached Lonavala', (await spoken(b)).includes("You've reached Lonavala"));
  await a.click('#destClear');
  await b.waitForTimeout(300);
  check('Clearing reaches Bilal', (await spoken(b)).includes('Asha cleared the destination') && (await b.textContent('#destCard')).includes('Where are we heading?'));
  await spoken(a); await spoken(c);

  // ---- Break reminder: Asha's clock moves 2 hours on ----
  await a.evaluate(() => { window.__shift = 121 * 60000; });
  await a.waitForTimeout(5600);
  check('Asha is asked "Time for a break?"', (await spoken(a)).some((t) => t.startsWith("You've been riding for 2 hours 1 minute. Time for a break?")));
  check('Break card shows', (await a.textContent('#breakCard')).includes('Time for a break?'));
  await a.screenshot({ path: OUT + 'w34_break.png' });
  await a.click('#breakAsk');
  await b.waitForTimeout(300);
  check('The group gets a Break vote', (await spoken(b)).some((t) => t.startsWith('Asha wants a break')));
  await b.click('[data-cast="yes"]');
  await a.waitForTimeout(400);
  check('Break approved', (await spoken(a)).some((t) => t.startsWith('Break approved')));
  await a.evaluate(() => { window.__shift += 40 * 60000; });
  await a.waitForTimeout(5600);
  check('No reminder right after the break', !(await spoken(a)).some((t) => t.includes('Time for a break')));
  await spoken(b); await spoken(c);

  // ---- Home safe ----
  await b.click('#homeBtn');
  await a.waitForTimeout(300);
  check('Asha hears Bilal is home safe', (await spoken(a)).includes('Bilal is home safe'));
  const aHome = await a.textContent('#homeCard');
  check('Asha\'s card: home safe Bilal, Chetan on the road', aHome.includes('Home safe: Bilal') && aHome.includes('On the road: Chetan'));
  // Chetan leaves without saying he's home.
  await c.click('#leaveBtn');
  check('Leaving offers "I\'m home safe · leave"', await c.isVisible('#leaveHome'));
  await c.click('#leaveGo');
  await a.waitForTimeout(500);
  check('Asha sees Chetan left without getting home', (await a.textContent('#homeCard')).includes('Chetan (left the ride)'));
  await a.screenshot({ path: OUT + 'w35_home_card.png' });
  check('Chetan is offered a check-in later', (await c.textContent('#checkIn')).includes('Home safe?'));
  await c.screenshot({ path: OUT + 'w36_check_in.png' });
  await spoken(a);
  await c.click('#checkInGo');
  await c.waitForSelector('#checkIn :text("still in the ride")', { timeout: 8000 });
  check('Chetan: told the 2 riders still in the ride', (await c.textContent('#checkIn')).includes('Told the 2 riders still in the ride'));
  await a.waitForTimeout(500);
  const aSaid = await spoken(a);
  check('Asha hears Chetan is home safe', aSaid.includes('Chetan is home safe'));
  check('The quick check-in isn\'t announced as a rider', !aSaid.some((t) => t.includes('joined') || t.includes('left') || t.includes('dropped')));
  check('Asha\'s card: Chetan home now', (await a.textContent('#homeCard')).includes('Chetan') && !(await a.textContent('#homeCard')).includes('left the ride'));
  check('Asha still lists 2 riders', (await a.$$('#riders .rider')).length === 2);

  // ---- Locked rides: Dev opens an invite with the group key, on a page that knows the server ----
  const locked = JSON.stringify({ locked: true, server: 'https://ride.example.workers.dev/' });
  const d = await mk('Dev', 'web-d', { code: 'KCKDPX', hash: '#k=' + encodeURIComponent('our secret'), settings: locked, noJoin: true });
  await d.click('#joinBtn');
  await d.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
  check('Dev joins through the private server with the key from the link', serverCalls === 1);
  await d.click('#leaveBtn');
  await d.click('#leaveGo');
  await d.evaluate(() => localStorage.setItem('rc-web-key', 'wrong'));
  await d.click('#joinBtn');
  await d.waitForSelector('#joinErr:not(.hidden)');
  check('Wrong key: "Wrong group key"', (await d.textContent('#joinErr')).includes('Wrong group key'));
  await d.click('[data-open-settings]:visible');
  await d.evaluate(() => document.querySelector('#settings .sheet-in').scrollTo(0, 99999));
  await d.screenshot({ path: OUT + 'w37_settings_locked.png' });

  // ---- Look: Glass on the join screen; the ride screen stays Classic until its design is done ----
  const e = await mk('Esha', 'web-e', { code: 'TSTGKE', settings: JSON.stringify({ look: 'glass' }), noJoin: true });
  check('Glass look on the join screen', await e.evaluate(() => document.documentElement.classList.contains('look-glass') && getComputedStyle(document.querySelector('.orbs')).display === 'block'));
  await e.screenshot({ path: OUT + 'w38_join_glass.png' });
  await e.click('#joinBtn');
  await e.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
  check('Ride screen in Glass too', await e.evaluate(() => document.documentElement.classList.contains('in-ride') &&
    getComputedStyle(document.querySelector('.orbs')).display === 'block' && getComputedStyle(document.querySelector('.mic')).width === '100px'));
  await e.waitForTimeout(400);
  await e.screenshot({ path: OUT + 'w39_ride_glass.png' });
  await e.click('[data-open-settings]:visible');
  await e.waitForTimeout(300);
  await e.screenshot({ path: OUT + 'w40_settings_glass.png' });
  await e.click('#settingsClose');
  await e.click('[data-open-dest]');
  await e.fill('#destQuery', 'Lonavala');
  await e.click('#destSearch');
  await e.waitForSelector('[data-place="0"]');
  await e.screenshot({ path: OUT + 'w41_dest_glass.png' });
  await e.click('[data-place="0"]');
  await e.click('#homeBtn');
  await e.waitForTimeout(300);
  await e.screenshot({ path: OUT + 'w42_ride_glass_cards.png' });
  await e.evaluate(() => window.scrollTo(0, 99999));
  await e.waitForTimeout(300);
  await e.screenshot({ path: OUT + 'w43_ride_glass_bottom.png' });
  await e.click('#leaveBtn').catch(() => {});
  if (await e.isVisible('#leaveGo')) await e.click('#leaveGo');
  await e.waitForSelector('#join:not(.hidden)');
  check('Back on the join screen: Glass again', await e.evaluate(() => !document.documentElement.classList.contains('in-ride')));
  await e.click('[data-open-settings]:visible');
  await e.click('[data-choice="look"][data-value="classic"]');
  check('Switching to Classic in Settings', await e.evaluate(() => !document.documentElement.classList.contains('look-glass')));

  console.log(errors.length ? 'PAGE ERRORS:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
})();
