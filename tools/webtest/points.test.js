// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Points & badges: a ride earning points (distance, riding together, a break, a hazard, riding sweep, home safe later),
// the ride's leaderboard on both phones, the summary with "New badge", the join-screen card, the Points & badges page
// (this year, badges, streak, reset), the same rules as the app on a ready-made score book, the Settings switch, and
// points with Ride history off.
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

  async function newCtx() {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: VIEW, deviceScaleFactor: 2 });
    await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
      const res = await r.fetch();
      r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
    });
    await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
    await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
    await ctx.route(/tile\.openstreetmap\.org|unpkg|cdnjs/, (r) => r.abort());
    await ctx.addInitScript(() => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      navigator.vibrate = () => true;
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
  async function open(ctx, id, settings, score) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(id + ': ' + e.message));
    await p.addInitScript(([i, s, sc]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', s);
      if (sc) localStorage.setItem('rc-web-score', sc);
    }, [id, JSON.stringify(settings || {}), score ? JSON.stringify(score) : null]);
    await p.goto(BASE + 'ride/?code=PNTSRD');
    return p;
  }
  async function join(p, name) {
    await p.fill('#nameIn', name);
    await p.click('#joinBtn');
    await p.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
  }
  const spoken = (p) => p.evaluate(() => window.__spoken.slice());
  const text = async (p, sel) => ((await p.textContent(sel)) || '').replace(/\s+/g, ' ');
  /** Rides [steps] × 100 m north at 72 km/h (5 s a step), all at once with the clock moved on. */
  const ride = (p, steps, from) => p.evaluate(([n, f]) => {
    for (let i = 1; i <= n; i++) { window.__shift += 5000; window.__move({ latitude: f + i * 0.0009, longitude: 73.85, accuracy: 8, speed: 20, heading: 0 }); }
    return f + n * 0.0009;
  }, [steps, from]);
  /** Stands still for [mins] minutes (a fix every 30 s). */
  const stand = (p, mins, lat) => p.evaluate(([m, la]) => {
    for (let i = 0; i < m * 2; i++) { window.__shift += 30000; window.__move({ latitude: la + (i % 2) * 0.00005, longitude: 73.85, accuracy: 8, speed: 0, heading: 0 }); }
  }, [mins, lat]);
  const setLook = async (p, look) => {
    await p.click('section:not(.hidden) [data-open-settings]');
    await p.click('[data-choice="look"][data-value="' + look + '"]');
    await p.click('#settingsClose');
    await p.waitForTimeout(150);
  };
  const shot = async (p, file, sel) => {
    if (sel) await p.evaluate((s) => document.querySelector(s).scrollIntoView({ block: 'start' }), sel);
    await p.waitForTimeout(300);
    await p.screenshot({ path: OUT + file });
  };

  // ================= A ride earning points =================
  {
    const ctx = await newCtx();
    const a = await open(ctx, 'web-a');
    check('Join screen: Points & badges card for a new rider', (await text(a, '#pointsBox')).includes('Rookie') && (await text(a, '#pointsBox')).includes('0 points') &&
      (await text(a, '#pointsBox')).includes('Ride with RideComm to earn points'));
    await join(a, 'Asha');
    await a.evaluate(() => window.__move({ latitude: 18.5, longitude: 73.85, accuracy: 8, speed: 0, heading: 0 }));
    const b = await open(ctx, 'web-b');
    await join(b, 'Bilal');
    await a.waitForTimeout(400);
    check('Ride points card on both, with both riders', (await text(a, '#scoreCard')).includes('Ride points') && (await text(a, '#scoreCard')).includes('Bilal') &&
      (await text(b, '#scoreCard')).includes('Asha') && (await text(b, '#scoreCard')).includes('You'));
    check('Levels on the board', (await text(b, '#scoreCard')).includes('Rookie'));

    // Bilal makes Asha the sweep; Asha marks a pothole, rides 37 km, stops 11 minutes, rides 37 km more.
    await b.click('#riders [data-rider="web-a"]');
    await b.waitForTimeout(150);
    await b.click('[data-role="sweep"]');
    await b.click('#sheetDone');
    await a.click('[data-open-hazards]');
    await a.click('[data-hazard="POTHOLE"]');
    let lat = await ride(a, 370, 18.5);
    await a.waitForTimeout(16000); // the points tick (every 15 s) notes the last movement
    await stand(a, 11, lat);
    await a.waitForTimeout(16000); // ...and counts the break
    lat = await ride(a, 370, lat);
    await a.evaluate(() => { window.__shift += 3 * 60000; });
    await a.waitForTimeout(16000); // ...and sends the new points (2 minutes after the last time)
    const mine = await text(a, '#scoreCard'), theirs = await text(b, '#scoreCard');
    check('Asha leads the ride points: You +134', /1\s*You.*\+134/.test(mine));
    check('Bilal sees Asha +134 at the top', /1\s*Asha\s*Rookie\s*\+134/.test(theirs));
    await shot(b, 'p1_ride_board.png', '#scoreCard');

    // Leaving (not home yet): the summary with the points and the new badge.
    await a.click('#leaveBtn');
    await a.click('#leaveGo');
    await a.waitForSelector('#rideView:not(.hidden)', { timeout: 5000 });
    const body = await text(a, '#rideBody');
    check('Summary: Points +134', /Points\s*\+134/.test(body));
    check('Summary: the lines', ['74 km ridden+74', 'Rode with 1 rider+10', 'Took 1 break+15', 'Marked 1 hazard+10', 'Rode sweep+25'].every((l) => body.includes(l)));
    check('Summary: New badge: First ride', body.includes('New badge: First ride'));
    check('Said "New badge: First ride"', (await spoken(a)).includes('New badge: First ride'));
    const book = await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-score')));
    check('Score book: one entry of plain facts', book.length === 1 && book[0].km === 74.1 && book[0].others === 1 && book[0].breaks === 1 && book[0].hz === 1 &&
      book[0].sweep === true && book[0].lead === false && book[0].home === false && book[0].min === 61 && JSON.stringify(book[0].names) === '["Bilal"]');
    check('Same id as the ride in the history', book[0].id === (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-rides'))[0].id)));
    await a.evaluate(() => document.getElementById('rideView').scrollTo(0, 400));
    await shot(a, 'p2_summary_points.png');
    await a.click('#rideBack');

    // Home safe later: +15 on that ride.
    await a.waitForSelector('#checkInGo');
    await a.click('#checkInGo');
    await a.waitForFunction(() => /Told the/.test(document.getElementById('checkIn').textContent), null, { timeout: 8000 });
    check('Home safe check-in later adds 15 points', (await text(a, '#pointsBox')).includes('149 points') && (await a.evaluate(() => JSON.parse(localStorage.getItem('rc-web-score'))[0].home)) === true);
    const card = await text(a, '#pointsBox');
    check('Join card: level, points to the next level, this year, the badge', card.includes('Rookie') && card.includes('51 points to Rider') &&
      card.includes('This year: 74 km · 1 ride · 149 points') && card.includes('First ride'));
    await shot(a, 'p3_join_card.png', '#pointsBox');

    // The Points & badges page.
    await a.click('#pointsOpen');
    const pg = await text(a, '#pointsSheetIn');
    check('Page: level 1 of 7', pg.includes('Level 1 of 7') && pg.includes('149 points'));
    check('Page: this year', /74\s*km\s*Distance/.test(pg) && pg.includes('1Ride') && /1\s*h\s*Riding time/.test(pg) && /149\s*Points/.test(pg) && /74\s*km\s*Longest ride/.test(pg));
    check('Page: week streak 1 week', /1\s*week\s*Week streak/.test(pg));
    check('Page: rode most with Bilal (1)', pg.includes('Rode most with Bilal (1)'));
    check('Page: badges 1 of 16, progress on the locked ones', pg.includes('1 of 16') && pg.includes('74 / 100 km') && pg.includes('1 / 10 hazards') && pg.includes('1 / 5 rides'));
    check('Page: one bar for this month', (await a.$$('#pointsSheetIn .mb i:not(.none)')).length === 1);
    check('Page: how to earn points, never for speed', pg.includes('Never for speed') && pg.includes('Legend 12,000'));
    await shot(a, 'p4_page_classic.png');
    await a.click('#pointsClose');
    for (const look of ['glass', 'neu']) {
      await setLook(a, look);
      await a.click('#pointsOpen');
      await shot(a, 'p5_page_' + look + '.png');
      await a.evaluate(() => document.getElementById('pointsSheetIn').scrollTo(0, 99999));
      await shot(a, 'p6_page_badges_' + look + '.png');
      await a.click('#pointsClose');
      await shot(a, 'p7_join_card_' + look + '.png', '#pointsBox');
    }
    await setLook(a, 'classic');
    await a.click('#pointsOpen');
    await a.click('#pointsReset');
    check('Reset points', (await a.evaluate(() => localStorage.getItem('rc-web-score'))) === '[]' && (await text(a, '#pointsBox')).includes('0 points'));
    await a.click('#pointsClose');
    await ctx.close();
  }

  // ================= A ready-made score book: same numbers as the app =================
  {
    const day = 86400000, now = Date.now();
    const at = (y, m, d, h = 9) => new Date(y, m - 1, d, h).getTime();
    const thisMonday = (() => { const d = new Date(now); d.setHours(9, 0, 0, 0); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return d.getTime(); })();
    const book = [
      // The app's fullGroupRide test: 305 points.
      { id: 'a', at: thisMonday, km: 120, min: 180, others: 7, names: ['Amit', 'Rahul'], breaks: 5, hz: 9, lead: true, sweep: false, home: true },
      { id: 'b', at: thisMonday - 7 * day, km: 64, min: 80, others: 2, names: ['Amit'], breaks: 1, hz: 0, lead: true },
      { id: 'c', at: thisMonday - 14 * day, km: 38, min: 50, others: 1, names: ['Rahul', 'Amit'] },
      { id: 'd', at: thisMonday - 21 * day + 2 * day, km: 1.2, min: 3, others: 4, lead: true, sweep: true }, // a car park chat: 1 point
      { id: 'e', at: at(new Date(now).getFullYear() - 1, 12, 30, 5), km: 310, min: 400 }, // last year, an early start
    ];
    const ctx = await newCtx();
    const a = await open(ctx, 'web-a', {}, book);
    // 305 + (64 + 20 + 15 + 25) + (38 + 10) + 1 + 310 = 788
    const card = await text(a, '#pointsBox');
    check('Total 788 points: Road Buddy', card.includes('Road Buddy') && card.includes('788 points') && card.includes('712 points to Explorer'));
    check('Streak: 3 weeks in a row up to this week', /3\s*wk/.test(card));
    await a.click('#pointsOpen');
    const pg = await text(a, '#pointsSheetIn');
    check('Year: only this year\'s rides', /4\s*Rides/.test(pg) && /223\s*km\s*Distance/.test(pg));
    check('Year: rode most with Amit (3), Rahul (2)', pg.includes('Rode most with Amit (3), Rahul (2)'));
    const got = await a.$$eval('#pointsSheetIn .badge.got .bt', (els) => els.map((e) => e.textContent));
    check('Badges earned: First ride, Century, Long haul, Pack ride, Early bird', ['First ride', 'Century', 'Long haul', 'Pack ride', 'Early bird'].every((t) => got.includes(t)) && got.length === 5);
    check('Every week not yet (3 of 4 weeks)', pg.includes('3 / 4 weeks'));
    await shot(a, 'p8_page_book.png');
    await ctx.close();
  }

  // ================= Switched off, and points without Ride history =================
  {
    const ctx = await newCtx();
    const a = await open(ctx, 'web-a');
    const c = await open(ctx, 'web-c', { points: false });
    check('Switched off: no card on the join screen', (await text(c, '#pointsBox')) === '');
    await join(a, 'Asha');
    await join(c, 'Chirag');
    await a.waitForTimeout(400);
    check('Switched off: no ride points card', (await text(c, '#scoreCard')) === '');
    check('Switched off: nothing sent, so Asha sees only herself', !(await text(a, '#scoreCard')).includes('Chirag'));
    // Switched on mid-ride: the board appears and Asha hears about it.
    await c.click('#ride [data-open-settings]');
    await c.click('label:has([data-setting="points"])');
    await c.click('#settingsClose');
    await a.waitForTimeout(400);
    check('Switched on mid-ride: card, and Asha sees Chirag', (await text(c, '#scoreCard')).includes('Ride points') && (await text(a, '#scoreCard')).includes('Chirag'));
    await c.click('#ride [data-open-settings]');
    await c.click('label:has([data-setting="points"])');
    await c.click('#settingsClose');
    check('Switched off mid-ride: card gone', (await text(c, '#scoreCard')) === '');

    const d = await open(ctx, 'web-d', { history: false });
    await join(d, 'Dev');
    await d.evaluate(() => window.__move({ latitude: 18.5, longitude: 73.85, accuracy: 8, speed: 0, heading: 0 }));
    await ride(d, 60, 18.5);
    await d.click('#leaveBtn');
    await d.click('#leaveGo');
    await d.waitForTimeout(400);
    const dBook = await d.evaluate(() => JSON.parse(localStorage.getItem('rc-web-score') || '[]'));
    check('Ride history off: no ride kept, points still counted', (await d.evaluate(() => localStorage.getItem('rc-web-rides'))) == null && dBook.length === 1 && dBook[0].km === 6);
    check('Ride history off: no summary', await d.evaluate(() => document.getElementById('rideView').classList.contains('hidden')));
    check('Ride history off: the card shows the points', (await text(d, '#pointsBox')).includes('26 points'));
    await ctx.close();
  }

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
