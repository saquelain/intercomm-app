// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Settings in groups: the short list (profile and 8 groups), each group showing only its own switches and boxes,
// Back, a switch still working inside its group, and screenshots in the three looks.
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
  const ctx = await browser.newContext({ viewport: VIEW, deviceScaleFactor: 2 });
  const p = await ctx.newPage();
  p.on('pageerror', (e) => errors.push(e.message));
  await p.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
    const res = await r.fetch();
    r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
  });
  await p.route(/livekit|tile\.openstreetmap\.org|unpkg|cdnjs/, (r) => r.abort());
  await p.goto(BASE + 'ride/');
  const visible = (sel) => p.$$eval(sel, (els) => els.filter((e) => e.offsetParent !== null).map((e) => e.dataset.setting || e.dataset.choice || e.id));

  await p.click('#join [data-open-settings]');
  const groups = await p.$$eval('#setGroups .sgroup .t', (els) => els.map((e) => e.textContent));
  check('List: 8 groups', JSON.stringify(groups) === JSON.stringify(['Look & map', 'Talking & sound', 'Alerts', 'Group & location', 'Safety & SOS', 'Ride tools', 'Points & badges', 'Ride server']));
  check('List: profile shown, no switches', (await p.isVisible('#photoPick')) && (await visible('#toggles input, #choices [data-choice]')).length === 0);
  check('Title "Settings", no Back', (await p.textContent('#settingsTitle')) === 'Settings' && !(await p.isVisible('#settingsBack')));
  await p.screenshot({ path: OUT + 's1_list_classic.png' });

  const expect = {
    look: [['look', 'mapKind', 'night'], []], talk: [['talk', 'whisper', 'voice', 'vibrate', 'awake'], []], alerts: [['hazards', 'roles', 'rain', 'breakEvery'], []],
    group: [['map', 'family', 'dest', 'home'], ['homeBox']], safety: [[], ['infoBox']], tools: [['finder', 'planner', 'history', 'garage'], []],
    points: [['points', 'wrapped'], []], server: [[], ['serverBox']],
  };
  for (const [g, [keys, boxes]] of Object.entries(expect)) {
    await p.click('[data-setgroup="' + g + '"]');
    const on = [...new Set(await p.$$eval('#toggles input[data-setting], #choices [data-choice]', (els) => els.filter((e) => e.offsetParent !== null || e.closest('.setting').offsetParent !== null).map((e) => e.dataset.setting || e.dataset.choice)))];
    const shownBoxes = await p.$$eval('#homeBox, #infoBox, #serverBox', (els) => els.filter((e) => !e.classList.contains('sethide')).map((e) => e.id));
    check('Group ' + g + ': ' + (keys.concat(boxes).join(', ') || '(none)'), JSON.stringify(on.sort()) === JSON.stringify(keys.slice().sort()) && JSON.stringify(shownBoxes) === JSON.stringify(boxes) && !(await p.isVisible('#photoPick')));
    await p.click('#settingsBack');
  }
  await p.click('[data-setgroup="tools"]');
  check('Inside a group: title and Back', (await p.textContent('#settingsTitle')) === 'Ride tools' && (await p.isVisible('#settingsBack')));
  await p.click('label:has([data-setting="garage"])');
  check('A switch works inside its group', (await p.evaluate(() => JSON.parse(localStorage.getItem('rc-web-settings')).garage)) === false);
  await p.click('label:has([data-setting="garage"])');
  await p.screenshot({ path: OUT + 's2_tools_classic.png' });
  await p.click('#settingsBack');
  for (const look of ['glass', 'neu']) {
    await p.click('[data-setgroup="look"]');
    await p.click('[data-choice="look"][data-value="' + look + '"]');
    check('Still in "Look & map" after changing the look (' + look + ')', (await p.textContent('#settingsTitle')) === 'Look & map');
    await p.screenshot({ path: OUT + 's3_look_' + look + '.png' });
    await p.click('#settingsBack');
    await p.screenshot({ path: OUT + 's4_list_' + look + '.png' });
  }
  await p.click('#settingsClose');
  await p.click('#join [data-open-settings]');
  check('Opening again starts at the list', (await p.textContent('#settingsTitle')) === 'Settings');

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  console.log(fails ? fails + ' FAILED' : 'All passed');
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
