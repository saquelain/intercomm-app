// RideComm private ride server (Cloudflare Worker).
//
// Hands out LiveKit ride passes (access tokens) only to riders who send the group key, and only
// for a single ride room. Speaks LiveKit's standard token-endpoint format:
//   POST  { room_name, participant_name, participant_identity }   header X-RideComm-Key: <group key>
//   200   { server_url, participant_token, room_name, participant_name }
//   401   wrong or missing group key
//
// Secrets (set with `wrangler secret put` or the deploy workflow):
//   LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET, GROUP_SECRET

const ROOM = /^ride-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/;
const TOKEN_TTL_S = 6 * 60 * 60;

// The web ride page (iPhone) asks from the browser, so answer the browser's CORS check. Safe for any
// site: a pass still needs the group key, and no cookies are involved.
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-RideComm-Key',
  'Access-Control-Max-Age': '86400',
};

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    if (request.method !== 'POST') return json({ error: 'POST only' }, 405);
    for (const name of ['LIVEKIT_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET', 'GROUP_SECRET']) {
      if (!env[name]) return json({ error: `server not configured: ${name} missing` }, 500);
    }
    const key = request.headers.get('X-RideComm-Key') || '';
    if (!(await sameSecret(key, env.GROUP_SECRET))) return json({ error: 'wrong group key' }, 401);

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: 'body must be JSON' }, 400);
    }
    const room = String(body.room_name || '');
    const identity = String(body.participant_identity || '').slice(0, 64);
    const name = String(body.participant_name || 'Rider').slice(0, 40);
    if (!ROOM.test(room)) return json({ error: 'invalid room' }, 400);
    if (!identity) return json({ error: 'participant_identity required' }, 400);

    const token = await accessToken(env, { room, identity, name });
    return json({ server_url: env.LIVEKIT_URL, participant_token: token, room_name: room, participant_name: name });
  },
};

/** LiveKit access token (HS256 JWT): join this one room, talk, listen and send data. Nothing else. */
export async function accessToken(env, { room, identity, name }, nowS = Math.floor(Date.now() / 1000)) {
  const payload = {
    iss: env.LIVEKIT_API_KEY,
    sub: identity,
    name,
    nbf: nowS - 10,
    exp: nowS + TOKEN_TTL_S,
    video: { room, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: true },
  };
  const header = { alg: 'HS256', typ: 'JWT' };
  const unsigned = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const signature = await hmac(env.LIVEKIT_API_SECRET, unsigned);
  return `${unsigned}.${b64url(signature)}`;
}

/** Compares secrets via their HMACs, so the time taken doesn't reveal how much matched. */
async function sameSecret(given, expected) {
  const [a, b] = await Promise.all([hmac('rc-compare', given), hmac('rc-compare', expected)]);
  if (a.byteLength !== b.byteLength) return false;
  const x = new Uint8Array(a);
  const y = new Uint8Array(b);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

async function hmac(secret, data) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return crypto.subtle.sign('HMAC', key, enc.encode(data));
}

function b64url(input) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json', ...CORS } });
}
