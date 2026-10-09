// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// My garage: adding a bike (the usual service items), the join-screen card, a service item edited and its note,
// "Done", the ride-start reminder, a ride's km added to the odometer, papers with dates (soon, expired) and the
// calendar file, a second bike and switching, editing the odometer, removing a bike, the stored format (same as
// the app's), the Settings switch, and screenshots in the three looks.
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
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: VIEW, deviceScaleFactor: 2, acceptDownloads: true });
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
      window.__shift = 0;
      const now = Date.now.bind(Date);
      Date.now = () => now() + window.__shift;
    });
    return ctx;
  }
  async function open(ctx, id, settings) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(id + ': ' + e.message));
    await p.addInitScript(([i, s]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', s);
    }, [id, JSON.stringify(settings || {})]);
    await p.goto(BASE + 'ride/?code=GARAGE');
    return p;
  }
  const text = async (p, sel) => ((await p.textContent(sel)) || '').replace(/\s+/g, ' ');
  const stored = (p) => p.evaluate(() => JSON.parse(localStorage.getItem('rc-web-garage') || 'null'));
  const iso = (daysFromNow) => { const d = new Date(); d.setDate(d.getDate() + daysFromNow); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
  const setLook = async (p, look) => {
    await p.click('section:not(.hidden) [data-open-settings]');
    await p.click('[data-choice="look"][data-value="' + look + '"]');
    await p.click('#settingsClose');
    await p.waitForTimeout(150);
  };
  const shot = async (p, file) => { await p.waitForTimeout(250); await p.screenshot({ path: OUT + file }); };
  const sheet = (p) => text(p, '#garageSheetIn');
  const rowOf = async (p, name) => p.evaluate((n) => {
    const row = Array.from(document.querySelectorAll('#garageSheetIn .gitem')).find((r) => r.querySelector('.rname').textContent === n);
    return row ? row.textContent.replace(/\s+/g, ' ') : '';
  }, name);

  {
    const ctx = await newCtx();
    const a = await open(ctx, 'web-a');
    check('Join screen: "My garage: add your bike"', (await text(a, '#garageBox')).includes('My garage: add your bike'));
    await a.click('#garageOpen');
    check('Empty garage explains itself', (await sheet(a)).includes('Type its odometer once'));
    await shot(a, 'g1_empty.png');
    await a.click('#gAddBike');
    await a.fill('#gName', 'Classic 350');
    await a.fill('#gReg', 'mh12ab1234');
    await a.fill('#gOdo', '12000');
    await a.click('#gSaveBike');
    let s = await sheet(a);
    check('Bike added with its odometer', s.includes('Classic 350') && s.includes('MH12AB1234') && s.includes('12,000 km'));
    check('The usual service items', ['Oil change', 'Chain clean & lube', 'Air filter', 'General service'].every((n) => s.includes(n)) &&
      (await rowOf(a, 'Oil change')).includes('In 3,000 km') && (await rowOf(a, 'Oil change')).includes('Every 3,000 km or 6 months'));
    let g = await stored(a);
    check('Stored like the app: bikes, current, licence; items with km, months, lastKm, lastAt', g.current === g.bikes[0].id && g.licence === 0 &&
      g.bikes[0].odo === 12000 && g.bikes[0].added === 0 && g.bikes[0].insurance === 0 && g.bikes[0].puc === 0 &&
      JSON.stringify(g.bikes[0].items.map((i) => [i.name, i.km, i.months, i.lastKm])) === JSON.stringify([['Oil change', 3000, 6, 12000], ['Chain clean & lube', 600, 0, 12000], ['Air filter', 10000, 0, 12000], ['General service', 5000, 12, 12000]]));
    await a.click('#garageClose');
    check('Card: all good, next the chain', (await text(a, '#garageBox')).includes('12,000 km · MH12AB1234') && (await text(a, '#garageBox')).includes('All good: next, chain clean & lube in 600 km'));

    // The oil change was last done at 9,000 km: due now.
    await a.click('#garageOpen');
    await a.click('[data-gitem]');
    check('Edit form for the oil change', (await a.inputValue('#gIName')) === 'Oil change' && (await a.inputValue('#gIKm')) === '3000' && (await a.inputValue('#gIMonths')) === '6');
    await a.fill('#gILast', '9000');
    await a.click('#gSaveItem');
    check('Oil change due now', (await rowOf(a, 'Oil change')).includes('Due now') && (await a.$('#garageSheetIn .gitem .rsub.due')) != null);
    // Papers: insurance in 12 days, licence expired 3 days ago.
    await a.fill('[data-gdoc="insurance"]', iso(12));
    await a.fill('[data-gdoc="licence"]', iso(-3));
    s = await sheet(a);
    check('Insurance: ends in 12 days', s.includes('Ends in 12 days'));
    check('Licence: expired 3 days ago', s.includes('Expired 3 days ago'));
    const [dl] = await Promise.all([a.waitForEvent('download'), a.click('[data-gcal="insurance"]')]);
    const ics = fs.readFileSync(await dl.path(), 'utf8');
    check('Calendar file: all day, reminders 30 and 7 days before', ics.includes('DTSTART;VALUE=DATE:' + iso(12).replace(/-/g, '')) && ics.includes('TRIGGER:-P30D') &&
      ics.includes('TRIGGER:-P7D') && ics.includes('SUMMARY:Insurance for Classic 350 ends'));
    await shot(a, 'g2_page_classic.png');
    await a.evaluate(() => document.querySelector('#garageSheet .sheet-in').scrollTo(0, 99999));
    await shot(a, 'g3_papers_classic.png');
    await a.click('#garageClose');
    const card = await text(a, '#garageBox');
    check('Card: most urgent first', card.indexOf('Oil change due') >= 0 && card.indexOf('Oil change due') < card.indexOf('Licence expired 3 days ago') &&
      card.indexOf('Licence expired 3 days ago') < card.indexOf('Insurance ends in 12 days'));
    await shot(a, 'g4_card_classic.png');

    // Riding: the reminder when the ride starts, then 2.5 km added.
    await a.fill('#nameIn', 'Asha');
    await a.click('#joinBtn');
    await a.waitForSelector('#ride:not(.hidden)');
    await a.waitForFunction(() => window.__spoken.length >= 2);
    check('Ride start: "Reminder: oil change is due on your Classic 350"', (await a.evaluate(() => window.__spoken)).includes('Reminder: oil change is due on your Classic 350'));
    await a.evaluate(() => {
      window.__move({ latitude: 18.5, longitude: 73.85, accuracy: 8, speed: 0, heading: 0 });
      for (let i = 1; i <= 25; i++) { window.__shift += 5000; window.__move({ latitude: 18.5 + i * 0.0009, longitude: 73.85, accuracy: 8, speed: 20, heading: 0 }); }
    });
    await a.click('#leaveBtn');
    await a.click('#leaveGo');
    await a.waitForTimeout(300);
    if (!(await a.evaluate(() => document.getElementById('rideView').classList.contains('hidden')))) await a.click('#rideBack');
    g = await stored(a);
    check('The ride\'s 2.5 km went on the odometer', g.bikes[0].added === 2.5 && (await text(a, '#garageBox')).includes('12,002 km'));

    // Done: the oil change starts again at the odometer.
    await a.click('#garageOpen');
    await a.click('[data-gdone]');
    check('Done: oil change in 3,000 km again', (await rowOf(a, 'Oil change')).includes('In 3,000 km') && (await stored(a)).bikes[0].items[0].lastKm === 12002.5);

    // A second bike, switching back, a new odometer, a new item.
    await a.click('#gAddBike');
    await a.fill('#gName', 'Duke 390');
    await a.fill('#gOdo', '4200');
    await a.click('#gSaveBike');
    check('Second bike is the one I ride now', (await sheet(a)).includes('4,200 km') && (await a.textContent('.bchip.gpick.sel')).includes('Duke 390'));
    await shot(a, 'g5_two_bikes.png');
    await a.click('[data-gbike="' + g.bikes[0].id + '"]');
    check('Picked the Classic again', (await a.textContent('.bchip.gpick.sel')).includes('Classic 350') && (await stored(a)).current === g.bikes[0].id);
    await a.click('#gEditBike');
    await a.fill('#gOdo', '15000');
    await a.click('#gSaveBike');
    g = await stored(a);
    check('New odometer: typed value, nothing added', g.bikes[0].odo === 15000 && g.bikes[0].added === 0 && (await sheet(a)).includes('15,000 km'));
    await a.click('#gAddItem');
    await a.fill('#gIName', 'Brake pads');
    await a.fill('#gIKm', '8000');
    await a.click('#gSaveItem');
    check('Own item: brake pads every 8,000 km', (await rowOf(a, 'Brake pads')).includes('In 8,000 km'));
    await a.click('[data-gitem="' + (await stored(a)).bikes[0].items[4].id + '"]');
    await a.click('#gDelItem');
    check('Stop tracking an item', !(await sheet(a)).includes('Brake pads'));
    for (const look of ['glass', 'neu']) {
      await a.click('#garageClose');
      await setLook(a, look);
      await shot(a, 'g6_card_' + look + '.png');
      await a.click('#garageOpen');
      await shot(a, 'g7_page_' + look + '.png');
    }
    await a.click('#gDelBike');
    check('Removed the Classic: the Duke is left', (await stored(a)).bikes.length === 1 && (await sheet(a)).includes('Duke 390'));
    await a.click('#garageClose');

    // Switched off: no card, rides don't add km.
    await a.click('#join [data-open-settings]');
    await a.click('label:has([data-setting="garage"])');
    await a.click('#settingsClose');
    check('Switched off: no card', (await text(a, '#garageBox')) === '');
    await a.click('#joinBtn');
    await a.waitForSelector('#ride:not(.hidden)');
    await a.evaluate(() => { for (let i = 1; i <= 10; i++) { window.__shift += 5000; window.__move({ latitude: 18.6 + i * 0.0009, longitude: 73.85, accuracy: 8, speed: 20, heading: 0 }); } });
    await a.click('#leaveBtn');
    await a.click('#leaveGo');
    await a.waitForTimeout(300);
    check('Switched off: the ride added nothing', (await stored(a)).bikes[0].added === 0);
    await ctx.close();
  }

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
