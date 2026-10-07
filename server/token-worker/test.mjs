// Run with: node test.mjs   (Node 20+). Checks the worker end to end with a fake environment.
import worker, { accessToken } from './src/index.js';
import assert from 'node:assert/strict';

const env = {
  LIVEKIT_URL: 'wss://example.livekit.cloud',
  LIVEKIT_API_KEY: 'APItestkey',
  LIVEKIT_API_SECRET: 'test-secret-that-is-long-enough-for-hs256-0123456789',
  GROUP_SECRET: 'bikers-only',
};
const call = (key, body) => worker.fetch(new Request('https://w.dev/', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...(key == null ? {} : { 'X-RideComm-Key': key }) },
  body: JSON.stringify(body),
}), env);
const ok = { room_name: 'ride-CQNQNE', participant_name: 'Saquelain', participant_identity: 'device-1' };

assert.equal((await call(null, ok)).status, 401);
assert.equal((await call('wrong', ok)).status, 401);
assert.equal((await call('bikers-only', { ...ok, room_name: 'admin' })).status, 400);
assert.equal((await call('bikers-only', { ...ok, participant_identity: '' })).status, 400);

const res = await call('bikers-only', ok);
assert.equal(res.status, 200);
const data = await res.json();
assert.equal(data.server_url, env.LIVEKIT_URL);
const [, payload] = data.participant_token.split('.');
const claims = JSON.parse(Buffer.from(payload, 'base64url').toString());
assert.equal(claims.sub, 'device-1');
assert.equal(claims.video.room, 'ride-CQNQNE');
assert.equal(claims.video.roomList, undefined, 'must not allow listing rooms');
// Print a token for the cross-check with LiveKit's own verifier.
console.log(await accessToken(env, { room: 'ride-CQNQNE', identity: 'device-1', name: 'Saquelain' }));
console.error('worker tests passed');
