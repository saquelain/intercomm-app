// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Ride history (recorded from fake GPS moves, the summary, the share picture), the ride planner (making a plan,
// the invite link, the calendar file, opening a link with "#p=", today's plan in the ride) and the invite page.
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
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);
  const pass = (b) => JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';

  async function newCtx(opts = {}) {
    const ctx = await browser.newContext(Object.assign({ ignoreHTTPSErrors: true, viewport: VIEW, deviceScaleFactor: 2, acceptDownloads: true }, opts));
    // The page's Google key only works on the real site: the OpenStreetMap map here (gmap.test.js does Google).
    await ctx.route(/127\.0\.0\.1:8765\/(ride|watch)\/(\?|$)/, async (r) => {
      const res = await r.fetch();
      r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
    });
    await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
    await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
    // Place search: a few known places; "where am I" answers Swargate.
    await ctx.route(/nominatim\.openstreetmap\.org/, (r) => {
      const u = new URL(r.request().url());
      if (u.pathname.includes('reverse')) return r.fulfill({ contentType: 'application/json', body: JSON.stringify({ name: 'Swargate', display_name: 'Swargate, Pune, Maharashtra, India' }) });
      const q = (u.searchParams.get('q') || '').toLowerCase();
      const all = [
        { lat: '18.7546', lon: '73.4062', name: 'Lonavala', display_name: 'Lonavala, Maval, Pune, Maharashtra, India' },
        { lat: '18.7631', lon: '73.3778', name: 'Khandala', display_name: 'Khandala, Maval, Pune, Maharashtra, India' },
      ];
      r.fulfill({ contentType: 'application/json', body: JSON.stringify(all.filter((p) => p.name.toLowerCase().startsWith(q.slice(0, 4)))) });
    });
    await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
      const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
      return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
    });
    await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
    await ctx.addInitScript(() => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: { latitude: 18.5018, longitude: 73.8636 } });
      window.__watchers = [];
      navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
      navigator.geolocation.clearWatch = () => {};
      window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
      window.confirm = () => true;
      // Lets a test move this page's clock forward (time between GPS fixes).
      window.__shift = 0;
      const now = Date.now.bind(Date);
      Date.now = () => now() + window.__shift;
      // Captures what the page shares (only once a test switches it on: browsers without sharing get a download).
      window.__shared = [];
      window.__allowShare = () => {
        navigator.share = async (d) => { window.__shared.push({ title: d.title, text: d.text, url: d.url, files: d.files ? d.files.map((f) => ({ name: f.name, type: f.type, size: f.size })) : null }); };
        navigator.canShare = () => true;
      };
    });
    return ctx;
  }
  async function page(ctx, name, id, opts = {}) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await p.addInitScript(([i, settings, extra]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', settings || '{}');
      Object.entries(extra || {}).forEach(([k, v]) => localStorage.setItem(k, v));
    }, [id, opts.settings, opts.storage]);
    await p.goto(BASE + (opts.path || 'ride/?code=' + (opts.code || 'TSTRDE')) + (opts.hash || ''));
    if (opts.noName) return p;
    await p.fill('#nameIn', name);
    if (opts.noJoin) return p;
    await p.click('#joinBtn');
    await p.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return p;
  }
  const download = async (p, sel) => {
    const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 8000 }), p.click(sel)]);
    return { name: dl.suggestedFilename(), body: fs.readFileSync(await dl.path()) };
  };
  const pngSize = (buf) => [buf.readUInt32BE(16), buf.readUInt32BE(20)];
  const planOf = (url) => JSON.parse(Buffer.from(new URL(url).hash.slice(1).split('&').find((x) => x.startsWith('p=')).slice(2), 'base64url').toString('utf8'));
  const b64 = (o) => Buffer.from(JSON.stringify(o), 'utf8').toString('base64url');

  // ================= Ride history =================
  {
    const ctx = await newCtx();
    const a = await page(ctx, 'Asha', 'web-a');
    const b = await page(ctx, 'Bilal', 'web-b');
    await a.waitForTimeout(300);
    // Asha rides 1.9 km north (10 s between fixes, 36 then 54 km/h), stops 4 minutes, then rides on 0.5 km.
    const lat0 = 18.5, lon0 = 73.85, step = 0.0009;
    const at = (fix, secs) => a.evaluate(([f, s]) => { window.__shift += s * 1000; window.__move(f); }, [fix, secs]);
    for (let i = 0; i < 20; i++) {
      await at({ latitude: lat0 + i * step, longitude: lon0, accuracy: 8, speed: i < 10 ? 10 : 15, heading: 0 }, i ? 10 : 0);
      // A bad fix (accuracy 120 m, far away) is left out.
      if (i === 5) await at({ latitude: lat0 + 0.05, longitude: lon0 + 0.05, accuracy: 120, speed: 10, heading: 0 }, 0);
    }
    for (let i = 0; i < 8; i++) await at({ latitude: lat0 + 19 * step + (i % 2) * 0.00005, longitude: lon0, accuracy: 10, speed: 0 }, 30);
    await at({ latitude: lat0 + 19 * step + 0.0012, longitude: lon0, accuracy: 8, speed: 10, heading: 0 }, 10);
    for (let i = 1; i <= 4; i++) await at({ latitude: lat0 + 19 * step + 0.0012 + i * step, longitude: lon0, accuracy: 8, speed: 10, heading: 0 }, 10);
    // The page saves the ride as it goes (every minute, and when the phone hides the page).
    await a.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })));
    const mid = await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides') || '[]'));
    check('Saved while riding (one record)', mid.length === 1 && mid[0].code === 'TSTRDE' && mid[0].dist > 2300);
    check('Route stored as an encoded polyline', typeof mid[0].route === 'string' && mid[0].route.length > 20 && !mid[0].route.includes('['));
    check('Bad GPS fix left out of the distance', mid[0].dist < 2600);
    check('Moving time 4 min (only the moving fixes)', Math.abs(mid[0].moving - 240000) < 5000);
    check('Top speed 54 km/h', mid[0].top === 54);
    check('Average from distance and moving time', Math.abs(mid[0].avg - mid[0].dist / 1000 / (240 / 3600)) < 0.2);
    check('One stop of 4 minutes', mid[0].stops.length === 1 && Math.abs(mid[0].stops[0].dur - 240000) < 5000 && Math.abs(mid[0].stops[0].lat - (lat0 + 19 * step)) < 0.0002);
    check('Riders: Asha and Bilal', JSON.stringify(mid[0].riders) === '["Asha","Bilal"]');
    // Leaving shows the summary.
    await a.click('#leaveBtn');
    await a.click('#leaveGo');
    await a.waitForSelector('#rideView:not(.hidden)', { timeout: 5000 });
    await a.waitForSelector('#rideMap .rmk', { timeout: 10000 });
    await a.waitForTimeout(800);
    const body = await a.textContent('#rideBody');
    check('Summary opens after leaving', true);
    check('Summary: distance 2.4 km', /2\.4\s*km\s*Distance/.test(body));
    check('Summary: riding time 4 min, top speed 54', body.includes('4 minRiding time') && /54\s*km\/h\s*Top speed/.test(body));
    check('Summary: total time', /\d+ minTotal time/.test(body));
    check('Summary: one stop of 4 minutes with a map link', body.includes('Stopped 4 min') && (await a.innerHTML('#rideBody')).includes('google.com/maps/search/?api=1&amp;query=18.5'));
    check('Summary: riders', body.includes('Asha, Bilal'));
    check('Map: the route line, start, finish and stop markers', (await a.$$('#rideMap .leaflet-overlay-pane path')).length === 2 && (await a.$$('#rideMap .rmk')).length === 3);
    await a.screenshot({ path: OUT + 'h1_summary.png' });
    await a.evaluate(() => document.getElementById('rideView').scrollTo(0, 99999));
    await a.waitForTimeout(200);
    await a.screenshot({ path: OUT + 'h2_summary_bottom.png' });
    // Share picture: this browser can't share files, so it's saved instead.
    const pic = await download(a, '#rideShare');
    const [w, h] = pngSize(pic.body);
    check('Share picture falls back to a download (PNG 1080×1350)', /^ridecomm-ride-\d{4}-\d\d-\d\d\.png$/.test(pic.name) && w === 1080 && h === 1350);
    fs.writeFileSync(OUT + 'h3_share_picture.png', pic.body);
    await a.evaluate(() => window.__allowShare());
    await a.click('#rideShare');
    await a.waitForTimeout(300);
    const shared = await a.evaluate(() => window.__shared);
    check('Share picture goes to the share sheet when it can', shared.length === 1 && shared[0].files && shared[0].files[0].type === 'image/png' && shared[0].files[0].size > 20000);
    await a.click('#rideBack');
    const rides = await a.textContent('#ridesBox');
    check('Join screen: "Your rides" with the ride', /Your rides/.test(rides) && /\w{3} \d+ \w{3} · 2\.4 km · \d+ min/.test(rides) && rides.includes('with Bilal'));
    check('No "See all" for one ride', !(await a.$('#ridesAll')));
    await a.screenshot({ path: OUT + 'h4_join_rides.png' });
    // A ride left at once isn't kept.
    await a.click('#joinBtn');
    await a.waitForSelector('#ride:not(.hidden)');
    await a.click('#leaveBtn'); await a.click('#leaveGo');
    await a.waitForTimeout(400);
    check('A short ride (no distance, under 5 min) is not kept', (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides')).length)) === 1 &&
      await a.evaluate(() => document.getElementById('rideView').classList.contains('hidden')));
    // More rides: "See all".
    await a.evaluate(() => {
      const l = JSON.parse(localStorage.getItem('rc-web-rides'));
      for (let i = 1; i <= 4; i++) l.push(Object.assign({}, l[0], { id: 'old' + i, start: l[0].start - i * 86400000 * 3, end: l[0].end - i * 86400000 * 3, dist: 10000 * i + 2300, riders: ['Asha'] }));
      localStorage.setItem('rc-web-rides', JSON.stringify(l));
    });
    await a.reload();
    check('"See all (5)" with five rides', (await a.textContent('#ridesAll')).includes('See all (5)') && (await a.$$('#ridesBox [data-ride]')).length === 3);
    await a.click('#ridesAll');
    check('All rides listed', (await a.$$('#ridesSheet [data-ride]')).length === 5);
    await a.screenshot({ path: OUT + 'h5_all_rides.png' });
    await a.click('#ridesSheet [data-ride="old4"]');
    await a.waitForSelector('#rideView:not(.hidden)');
    await a.click('#rideDelete');
    check('Delete a ride', (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides')).length)) === 4 && await a.evaluate(() => document.getElementById('rideView').classList.contains('hidden')));
    // Settings: the switch and "Clear ride history".
    await a.click('[data-open-settings]:visible');
    check('Settings: Ride history switch (on) and Clear button', await a.isChecked('[data-setting="history"]') && (await a.textContent('#historyClear')).includes('Clear ride history (4)'));
    await a.evaluate(() => document.getElementById('historyClear').scrollIntoView({ block: 'center' }));
    await a.screenshot({ path: OUT + 'h6_settings_history.png' });
    await a.click('#historyClear');
    check('Clear ride history', (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides')).length)) === 0 && !(await a.$('#historyClear')));
    await a.click('#settingsClose');
    check('No rides card once cleared', (await a.textContent('#ridesBox')) === '');
    // Ride history off: nothing is recorded and location isn't needed.
    await a.click('[data-open-settings]:visible');
    await a.click('.switch:has([data-setting="history"])');
    await a.click('#settingsClose');
    await a.click('#joinBtn');
    await a.waitForSelector('#ride:not(.hidden)');
    const watchersBefore = await a.evaluate(() => window.__watchers.length);
    await a.evaluate(() => { window.__shift += 6 * 60000; });
    await a.click('#leaveBtn'); await a.click('#leaveGo');
    await a.waitForTimeout(300);
    check('History off: a 6-minute ride is not kept', (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides') || '[]').length)) === 0);
    check('History off: location not started just for it', watchersBefore === (await a.evaluate(() => window.__watchers.length)) &&
      (await a.evaluate(() => window.__watchers.length)) <= 1);
    await b.close();
    // The summary in Glass and Soft, and at the bottom of the join screen.
    for (const look of ['glass', 'neu']) {
      const p = await page(ctx, 'Farah', 'web-f' + look, { settings: JSON.stringify({ look }), noJoin: true, storage: { 'rc-web-rides': JSON.stringify(mid) } });
      await p.evaluate(() => window.scrollTo(0, 99999)); await p.screenshot({ path: OUT + 'h7_join_rides_' + look + '.png' });
      await p.click('[data-ride]');
      await p.waitForSelector('#rideMap .rmk', { timeout: 10000 });
      await p.waitForTimeout(600);
      await p.screenshot({ path: OUT + 'h8_summary_' + look + '.png' });
      await p.close();
    }
    await ctx.close();
  }

  // ================= Ride planner =================
  let invite;
  const tomorrow = new Date(Date.now() + 86400000);
  const ymd = tomorrow.getFullYear() + '-' + String(tomorrow.getMonth() + 1).padStart(2, '0') + '-' + String(tomorrow.getDate()).padStart(2, '0');
  const startAt = new Date(tomorrow.getFullYear(), tomorrow.getMonth(), tomorrow.getDate(), 6, 30).getTime();
  {
    const ctx = await newCtx();
    const c = await page(ctx, 'Chetan', 'web-c', { noJoin: true });
    check('Join screen: "Plan a ride"', (await c.textContent('#plansBox')).includes('Plan a ride'));
    await c.click('#planNew');
    const def = await c.inputValue('#planWhen');
    const next = new Date(Date.now() + 3600000); next.setMinutes(0, 0, 0);
    check('Start defaults to the next full hour', def === next.getFullYear() + '-' + String(next.getMonth() + 1).padStart(2, '0') + '-' + String(next.getDate()).padStart(2, '0') + 'T' + String(next.getHours()).padStart(2, '0') + ':00');
    await c.click('#planSave');
    check('A meeting point is needed', (await c.textContent('#planErr')).includes('Choose where to meet'));
    await c.fill('#planTitle', 'Sunday Lonavala ride, with chai ☕');
    await c.fill('#planWhen', ymd + 'T06:30');
    // Meeting point: "My location" (named by a reverse lookup).
    await c.click('#planHere');
    await c.waitForSelector('#planSheetIn .prow');
    check('My location as the meeting point: Swargate', (await c.textContent('#planSheetIn')).includes('Swargate'));
    // Stops: pasted coordinates, and a search.
    await c.click('[data-plan-pick="stop"]');
    check('Place picker titled for the plan, no "use my location" for stops', (await c.textContent('#destTitle')) === 'Add a stop' && await c.isHidden('#placeHereWrap'));
    await c.fill('#destQuery', 'https://www.google.com/maps/@18.6500,73.6000,15z');
    await c.click('#destSearch');
    await c.click('[data-place="0"]');
    await c.click('[data-plan-pick="stop"]');
    await c.fill('#destQuery', 'Khandala');
    await c.click('#destSearch');
    await c.waitForSelector('[data-place="0"]');
    await c.click('[data-place="0"]');
    await c.click('[data-plan-pick="dest"]');
    await c.fill('#destQuery', 'Lonavala');
    await c.click('#destSearch');
    await c.waitForSelector('[data-place="0"]');
    await c.click('[data-place="0"]');
    check('Title and time kept while picking places', (await c.inputValue('#planTitle')).startsWith('Sunday') && (await c.inputValue('#planWhen')) === ymd + 'T06:30');
    check('Plan sheet: meeting point, 2 stops, destination', (await c.$$('#planSheetIn .prow')).length === 4 && (await c.textContent('#planSheetIn')).includes('Khandala'));
    await c.screenshot({ path: OUT + 'p1_plan_sheet.png' });
    await c.evaluate(() => document.getElementById('planSheetIn').scrollTo(0, 99999));
    await c.screenshot({ path: OUT + 'p2_plan_sheet_bottom.png' });
    await c.click('#planSave');
    await c.waitForSelector('#planSheetIn :text("Ride planned")');
    const saved = await c.evaluate(() => JSON.parse(localStorage.getItem('rc-web-plans')));
    const code = saved[0] && saved[0].code;
    check('Plan saved with a new ride code', saved.length === 1 && /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/.test(code) && saved[0].at === startAt);
    await c.screenshot({ path: OUT + 'p3_plan_saved.png' });
    await c.evaluate(() => window.__allowShare());
    await c.click('#planSheetIn [data-plan-share]');
    await c.waitForTimeout(200);
    const sh = (await c.evaluate(() => window.__shared))[0];
    invite = sh.url;
    check('Share: the invite link with the plan after "#p="', invite.startsWith('https://saquelain.github.io/intercomm-app/join/?code=' + code + '#p=') && !invite.includes('&k='));
    const pl = planOf(invite);
    check('Link plan: v1, title, time, meet, stops, destination, by', pl.v === 1 && pl.title === 'Sunday Lonavala ride, with chai ☕' && pl.at === startAt && pl.meet.name === 'Swargate' &&
      pl.stops.length === 2 && pl.stops[0].name === 'Pinned place' && pl.stops[1].name === 'Khandala' && pl.dest.name === 'Lonavala' && pl.by === 'Chetan');
    check('Link plan is base64url without padding', /#p=[A-Za-z0-9_-]+$/.test(invite));
    check('Share text says when and where', sh.text.includes('Tomorrow, 6:30') && sh.text.includes('Meet at Swargate'));
    // Calendar file.
    const cal = await download(c, '#planSheetIn [data-plan-ics]');
    const ics = cal.body.toString('utf8'), unfolded = ics.replace(/\r\n /g, '');
    const utc = (ms) => new Date(ms).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    check('Calendar file: name and event', cal.name === 'ride-' + code + '.ics' && ics.startsWith('BEGIN:VCALENDAR\r\n') && ics.includes('BEGIN:VEVENT'));
    check('Calendar: start in UTC, 3 hours long', unfolded.includes('DTSTART:' + utc(startAt)) && unfolded.includes('DTEND:' + utc(startAt + 3 * 3600000)));
    check('Calendar: title, place, reminder an hour before', unfolded.includes('SUMMARY:Sunday Lonavala ride\\, with chai ☕') && unfolded.includes('LOCATION:Swargate') &&
      unfolded.includes('BEGIN:VALARM') && unfolded.includes('TRIGGER:-PT60M'));
    check('Calendar: stops and the join link in the description', unfolded.includes('Stops: Pinned place\\, Khandala') && unfolded.includes('Join: https://saquelain.github.io/intercomm-app/join/?code=' + code + '#p='));
    check('Calendar: lines folded at 75', ics.split('\r\n').every((l) => Buffer.byteLength(l) <= 75 || l.length <= 75));
    await c.click('#planDone');
    const card = await c.textContent('#plansBox');
    check('Upcoming rides card: title, day and time, meeting point', card.includes('Upcoming rides') && card.includes('Sunday Lonavala ride') && card.includes('Tomorrow, 6:30') && card.includes('Meet at Swargate'));
    await c.evaluate(() => window.scrollTo(0, 99999)); await c.screenshot({ path: OUT + 'p4_upcoming.png' });
    // Join from the plan: the ride shows the plan, each place with Navigate.
    await c.click('[data-plan-join="' + code + '"]');
    await c.waitForSelector('#ride:not(.hidden)');
    check('Join fills the code', (await c.textContent('#codeOut')) === code);
    const pc = await c.innerHTML('#planCard');
    check('In the ride: the plan with Navigate links', pc.includes('Ride plan') && pc.includes('Swargate') && pc.includes('Stop 2') && pc.includes('destination=18.7631,73.3778') &&
      (await c.$$('#planCard a.nav')).length === 4);
    await c.screenshot({ path: OUT + 'p5_plan_in_ride.png' });
    await c.click('#planSetDest');
    await c.waitForTimeout(200);
    check('"Set as destination" sets it for everyone', (await c.textContent('#destCard')).includes('Heading to Lonavala') && !(await c.$('#planSetDest')));
    // Share from the ride carries the plan too.
    await c.click('#shareBtn');
    await c.waitForTimeout(200);
    check('Ride share link carries the plan', (await c.evaluate(() => window.__shared.slice(-1)[0].url)) === invite);
    await c.click('#leaveBtn'); await c.click('#leaveGo');
    await c.waitForTimeout(300);
    await c.click('[data-plan-del="' + code + '"]');
    check('Delete a plan', !(await c.textContent('#plansBox')).includes('Upcoming') && (await c.evaluate(() => JSON.parse(localStorage.getItem('rc-web-plans')).length)) === 0);
    // Opening a "#p=" link saves the plan (by ride code; a newer copy replaces it) and keeps "#k=" working.
    const today = { v: 1, title: 'Evening chai ride', at: Date.now() + 5 * 60000, meet: { name: 'Shell pump, Baner', lat: 18.559, lon: 73.786 }, stops: [{ name: 'Tea stop', lat: 18.6, lon: 73.75 }],
      dest: { name: 'Lonavala', lat: 18.7546, lon: 73.4062 }, by: 'Asha' };
    const locked = JSON.stringify({ locked: true, server: 'https://ride.example.workers.dev/' });
    const d = await page(ctx, 'Dev', 'web-d', { code: 'PNABCD', hash: '#p=' + b64(Object.assign({}, today, { title: 'Old title' })) + '&k=' + encodeURIComponent('our secret'), settings: locked, noJoin: true });
    check('"#p=" link: plan saved and code filled', (await d.inputValue('#codeIn')) === 'PNABCD' && (await d.textContent('#plansBox')).includes('Old title'));
    check('"#k=" still saved alongside', (await d.evaluate(() => localStorage.getItem('rc-web-key'))) === 'our secret');
    check('Hash removed from the address', !(await d.evaluate(() => location.hash)));
    await d.goto(BASE + 'ride/?code=PNABCD&again=1#p=' + b64(today));
    const plans = await d.evaluate(() => JSON.parse(localStorage.getItem('rc-web-plans')));
    check('Same ride again: replaced, not added', plans.length === 1 && plans[0].title === 'Evening chai ride');
    check('A broken "#p=" is ignored', await (async () => { await d.goto(BASE + 'ride/?code=PNXYZ2#p=bm9wZQ'); return (await d.evaluate(() => JSON.parse(localStorage.getItem('rc-web-plans')).length)) === 1; })());
    await d.evaluate(() => window.__allowShare());
    await d.click('[data-plan-share="PNABCD"]');
    await d.waitForTimeout(200);
    const dUrl = await d.evaluate(() => window.__shared[0].url);
    check('Locked ride: the invite carries "&k=" after the plan', /#p=[A-Za-z0-9_-]+&k=our%20secret$/.test(dUrl) && planOf(dUrl).title === 'Evening chai ride');
    await d.close();
    // Today's plan in each look.
    for (const look of ['classic', 'glass', 'neu']) {
      const p = await page(ctx, 'Esha', 'web-e' + look, { code: 'PNABCD', hash: '#p=' + b64(today), settings: JSON.stringify({ look }), noJoin: true });
      await p.evaluate(() => window.scrollTo(0, 99999)); await p.screenshot({ path: OUT + 'p6_join_plan_' + look + '.png' });
      await p.click('#planNew');
      await p.click('#planHere');
      await p.waitForSelector('#planSheetIn .prow');
      await p.screenshot({ path: OUT + 'p7_plan_sheet_' + look + '.png' });
      await p.click('#planCancel');
      await p.click('[data-plan-join="PNABCD"]');
      await p.waitForSelector('#ride:not(.hidden)');
      if (look === 'classic') check("Today's plan in the ride", (await p.textContent('#planCard')).includes("Today's plan") && (await p.textContent('#planCard')).includes('planned by Asha'));
      await p.waitForTimeout(3600);
      await p.evaluate(() => window.scrollTo(0, 0));
      await p.screenshot({ path: OUT + 'p8_today_plan_' + look + '.png' });
      await p.close();
    }
    // Planner off: no cards.
    const o = await page(ctx, 'Om', 'web-o', { code: 'PNABCD', settings: JSON.stringify({ planner: false }), noJoin: true });
    check('Planner off: no plan cards or button', (await o.textContent('#plansBox')) === '');
    await ctx.close();
  }

  // ================= The invite page =================
  {
    const ctx = await newCtx();
    const j = await ctx.newPage();
    j.on('pageerror', (e) => errors.push('join: ' + e.message));
    const raw = invite.split('#')[1];
    const code = new URL(invite).searchParams.get('code');
    await j.goto(BASE + 'join/?code=' + code + '#' + raw + '&k=' + encodeURIComponent('our secret'));
    const txt = await j.textContent('#plan');
    const when = await j.evaluate((t) => new Date(t).toLocaleString(undefined, { weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' }), startAt);
    check('Invite page shows the plan: title, time, meet, stops, destination', await j.isVisible('#plan') && txt.includes('Sunday Lonavala ride, with chai ☕') && txt.includes(when) &&
      txt.includes('Meet at') && txt.includes('Swargate') && txt.includes('Stop 2') && txt.includes('Khandala') && txt.includes('Lonavala') && txt.includes('Planned by Chetan'));
    const pPart = raw.split('&')[0];
    check('Web link passes the plan and key', (await j.getAttribute('#web', 'href')) === '../ride/?code=' + code + '#' + pPart + '&k=our%20secret');
    check('App link passes the plan and key', (await j.getAttribute('#open', 'href')).startsWith('intent://join/' + code + '?' + pPart + '&k=our%20secret#Intent;scheme=ridecomm;package=com.ridecomm.app;'));
    const cal = await download(j, '#cal');
    check('Invite page: Add to calendar', cal.name === 'ride-' + code + '.ics' && cal.body.toString().replace(/\r\n /g, '').includes('TRIGGER:-PT60M') &&
      cal.body.toString().includes('DTSTART:' + new Date(startAt).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')));
    await j.screenshot({ path: OUT + 'p9_invite_plan.png', fullPage: true });
    // The web link opens the ride page with the plan.
    await j.click('#web');
    await j.waitForSelector('#plansBox .planitem');
    check('Ride page from the invite: plan saved, code filled', (await j.inputValue('#codeIn')) === code && (await j.textContent('#plansBox')).includes('Sunday Lonavala ride'));
    // Without a plan, as before.
    await j.goto(BASE + 'join/?code=' + code);
    check('No plan: nothing extra, plain links', await j.isHidden('#plan') && (await j.getAttribute('#web', 'href')) === '../ride/?code=' + code &&
      (await j.getAttribute('#open', 'href')).startsWith('intent://join/' + code + '#Intent;'));
    await j.goto(BASE + 'join/?code=' + code + '&n=2#k=abc');
    check('Key only: passed on as before', (await j.getAttribute('#web', 'href')) === '../ride/?code=' + code + '#k=abc' && (await j.getAttribute('#open', 'href')).startsWith('intent://join/' + code + '?k=abc#Intent;'));
    await j.goto(BASE + 'join/?code=' + code + '&n=3#p=garbage!!');
    check('Broken plan: ignored', await j.isHidden('#plan') && (await j.getAttribute('#web', 'href')) === '../ride/?code=' + code);
    await ctx.close();
    // On an iPhone.
    const ictx = await newCtx({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' });
    const ip = await ictx.newPage();
    ip.on('pageerror', (e) => errors.push('join iPhone: ' + e.message));
    await ip.goto(BASE + 'join/?code=' + code + '#' + raw);
    check('iPhone: "Join the ride" opens the web ride with the plan', (await ip.textContent('#web')) === 'Join the ride' && (await ip.getAttribute('#web', 'href')) === '../ride/?code=' + code + '#' + pPart);
    await ip.screenshot({ path: OUT + 'p10_invite_iphone.png', fullPage: true });
    await ictx.close();
  }

  console.log(errors.length ? 'PAGE ERRORS:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
})();
