// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Ride Wrapped: the button on the Points & badges page ("so far" in October), the join-screen card in December, the
// slides with the same numbers as the app's WrappedLogicTest (3 rides, 520 km, Sunday, around 6 am, the longest ride's
// route, March, Amit (2) and Rahul (1), the 1,000 km club, Long hauler), tapping back and on, the share picture
// (1080 × 1920), "Watch again", no rides that year, and the Settings switch.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'http://127.0.0.1:8765/';

/** Google's encoded polyline, like the page and the app. */
function encode(pts) {
  let out = '', pLat = 0, pLon = 0;
  const one = (v) => { v = v < 0 ? ~(v << 1) : v << 1; while (v >= 0x20) { out += String.fromCharCode((0x20 | (v & 0x1f)) + 63); v >>= 5; } out += String.fromCharCode(v + 63); };
  pts.forEach(([lat, lon]) => { const a = Math.round(lat * 1e5), b = Math.round(lon * 1e5); one(a - pLat); one(b - pLon); pLat = a; pLon = b; });
  return out;
}

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const VIEW = { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) };
  const errors = [];
  let fails = 0;
  const check = (label, cond) => { if (!cond) fails++; console.log((cond ? 'PASS ' : 'FAIL ') + label); };
  const at = (y, m, d, h = 9) => new Date(y, m - 1, d, h).getTime();
  // The app test's rides, plus one from last year.
  const book = [
    { id: 'a', at: at(2026, 3, 1, 6), km: 320, min: 400, others: 2, names: ['Amit', 'Rahul'] },
    { id: 'b', at: at(2026, 3, 8, 6), km: 120, min: 150, others: 1, names: ['Amit'] },
    { id: 'c', at: at(2026, 7, 15, 17), km: 80, min: 100, others: 0, names: [] },
    { id: 'old', at: at(2025, 12, 31), km: 500, min: 60, others: 0, names: [] },
  ];
  const route = (k) => Array.from({ length: 30 }, (_, t) => [18.5 + t * 0.003 * Math.cos(k + t / 6), 73.8 + t * 0.003 * Math.sin(k * 1.7 + t / 9)]);
  const rides = [
    { id: 'ra', code: 'ABCDEF', start: at(2026, 3, 1, 6) + 30000, end: at(2026, 3, 1, 14), dist: 320000, moving: 400 * 60000, top: 90, avg: 48, riders: ['Asha', 'Amit', 'Rahul'], route: encode(route(1)), stops: [] },
    { id: 'rc', code: 'ABCDEF', start: at(2026, 7, 15, 17), end: at(2026, 7, 15, 19), dist: 80000, moving: 100 * 60000, top: 80, avg: 48, riders: ['Asha'], route: encode(route(2)), stops: [] },
    { id: 'rold', code: 'ABCDEF', start: at(2025, 12, 31), end: at(2025, 12, 31, 18), dist: 500000, moving: 3600000, top: 80, avg: 50, riders: ['Asha'], route: encode(route(3)), stops: [] },
  ];

  async function open(ctx, nowMs, settings, score) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(e.message));
    await p.addInitScript(([t, s, sc, rd]) => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      const real = Date.now.bind(Date), shift = t - real();
      Date.now = () => real() + shift;
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-settings', s);
      localStorage.setItem('rc-web-score', sc);
      localStorage.setItem('rc-web-rides', rd);
    }, [nowMs, JSON.stringify(settings || {}), JSON.stringify(score || book), JSON.stringify(rides)]);
    await p.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
      const res = await r.fetch();
      r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
    });
    await p.route(/livekit|tile\.openstreetmap\.org|unpkg|cdnjs/, (r) => r.abort());
    await p.goto(BASE + 'ride/');
    return p;
  }
  const text = async (p, sel) => ((await p.textContent(sel)) || '').replace(/\s+/g, ' ');
  const slide = (p) => text(p, '#wrapBody');
  const next = (p) => p.mouse.click(VIEW.width - 40, VIEW.height / 2);
  const back = (p) => p.mouse.click(30, VIEW.height / 2);

  {
    const ctx = await browser.newContext({ viewport: VIEW, deviceScaleFactor: 2, acceptDownloads: true });
    // 9 October 2026: no card on the join screen, "Your 2026 so far" on the Points & badges page.
    const a = await open(ctx, at(2026, 10, 9));
    check('October: no Wrapped card on the join screen', (await text(a, '#wrappedBox')) === '');
    await a.click('#pointsOpen');
    check('Points & badges: "Your 2026 so far"', (await text(a, '#pointsSheetIn')).includes('Your 2026 so far'));
    await a.click('#wrappedOpen');
    check('Wrapped opens on the first slide', !(await a.evaluate(() => document.getElementById('wrapView').classList.contains('hidden'))) && (await a.$$('#wrapProg i')).length === 9);
    let s = await slide(a);
    check('Slide 1: 520 km, Delhi to Jaipur 1.8 times, 1.2% of the Earth', s.includes('520 km') && s.includes("That's like riding Delhi to Jaipur 1.8 times.") && s.includes('1.2% of the way around the Earth'));
    await a.screenshot({ path: OUT + 'w1_distance.png' });
    await next(a); s = await slide(a);
    check('Slide 2: 3 rides, 10 hours, Sunday, around 6 am', s.includes('3 rides') && s.includes('10 hours in the saddle') && s.includes('Your favourite day: Sunday') && s.includes('around 6 am'));
    await next(a); s = await slide(a);
    check('Slide 3: longest ride 320 km with Amit, Rahul, and its route', s.includes('320 km') && s.includes('Sunday 1 March') && s.includes('With Amit, Rahul') && (await a.$('#wrapBody .wbigroute svg path')) != null);
    await a.screenshot({ path: OUT + 'w3_longest.png' });
    await back(a);
    check('Tap on the left goes back', (await slide(a)).includes('3 rides'));
    await next(a); await next(a); s = await slide(a);
    check('Slide 4: March, 440 km', s.includes('March') && s.includes('440 km') && (await a.$$('#wrapBody .wbars .mb')).length === 12);
    await next(a); s = await slide(a);
    check('Slide 5: 2 riders, Amit 2 rides, Rahul 1 ride', s.includes('2 riders') && s.includes('1. Amit · 2 rides') && s.includes('2. Rahul · 1 ride'));
    await a.screenshot({ path: OUT + 'w5_crew.png' });
    await next(a); s = await slide(a);
    check('Slide 6: the 1,000 km club this year, level Road Buddy', s.includes('1 new badge') && s.includes('1,000 km club') && s.includes('Level now: Road Buddy') && s.includes('550 points'));
    await next(a); s = await slide(a);
    check('Slide 7: Long hauler', s.includes('Long hauler') && s.includes("Short hops aren't your thing"));
    await a.screenshot({ path: OUT + 'w7_type.png' });
    await next(a); s = await slide(a);
    check('Slide 8: 2 routes this year (not last year\'s)', s.includes('2 routes on your phone') && (await a.$$('#wrapBody .wgrid svg')).length === 2);
    await next(a); s = await slide(a);
    check('Slide 9: share', s.includes('That was 2026') && s.includes('3 rides · 10 h · Long hauler') && (await text(a, '#wrapMore')) === '');
    await next(a);
    check('No slide after the last', (await slide(a)).includes('That was 2026'));
    await a.screenshot({ path: OUT + 'w9_share.png' });
    const [dl] = await Promise.all([a.waitForEvent('download'), a.click('#wrShare')]);
    const png = fs.readFileSync(await dl.path());
    fs.writeFileSync(OUT + 'w10_picture.png', png);
    check('Share picture: 1080 × 1920 PNG', dl.suggestedFilename() === 'ridecomm-2026-wrapped.png' && png.readUInt32BE(16) === 1080 && png.readUInt32BE(20) === 1920);
    await a.click('#wrAgain');
    check('Watch again', (await slide(a)).includes('520 km'));
    await a.click('#wrapClose');
    check('Closed', await a.evaluate(() => document.getElementById('wrapView').classList.contains('hidden')));
    await a.close();

    // 5 December: the card on the join screen says it's ready.
    const d = await open(ctx, at(2026, 12, 5));
    check('December: "Your 2026 is ready" on the join screen', (await text(d, '#wrappedBox')).includes('Your 2026 is ready'));
    await d.screenshot({ path: OUT + 'w11_join_card.png' });
    await d.close();
    // 10 January 2027: still last year's.
    const j = await open(ctx, at(2027, 1, 10));
    check('10 January: still 2026', (await text(j, '#wrappedBox')).includes('Your 2026 is ready'));
    await j.click('#wrappedOpen');
    check('…with 2026\'s rides', (await slide(j)).includes('520 km'));
    await j.close();
    // A year without rides, and switched off.
    const none = await open(ctx, at(2026, 10, 9), {}, [book[3]]);
    await none.click('#pointsOpen');
    check('No rides this year: no button', !(await text(none, '#pointsSheetIn')).includes('Wrapped'));
    await none.close();
    const off = await open(ctx, at(2026, 12, 5), { wrapped: false });
    check('Switched off: no card', (await text(off, '#wrappedBox')) === '');
    await off.click('#pointsOpen');
    check('Switched off: no button', !(await text(off, '#pointsSheetIn')).includes('Wrapped'));
    await ctx.close();
  }

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
