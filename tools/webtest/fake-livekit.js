// Stand-in for livekit-client: pages in one browser share a "ride" over a BroadcastChannel,
// so the page's own logic (votes, messages, SOS, alerts, catch-up) can be exercised end to end.
window.LivekitClient = (() => {
  const RoomEvent = {
    TrackSubscribed: 'ts', TrackUnsubscribed: 'tu', ParticipantConnected: 'pc', ParticipantDisconnected: 'pd',
    ActiveSpeakersChanged: 'as', TrackMuted: 'tm', TrackUnmuted: 'tun', TrackPublished: 'tp', LocalTrackPublished: 'ltp',
    Reconnecting: 'rg', Reconnected: 'rd', AudioPlaybackStatusChanged: 'ap', Disconnected: 'dc',
  };
  const Track = { Kind: { Audio: 'audio' }, Source: { Microphone: 'microphone' } };
  class Participant {
    constructor(identity, name) { this.identity = identity; this.name = name; this.isMicrophoneEnabled = true; this.isSpeaking = false; }
  }
  class LocalP extends Participant {
    constructor(room, id, name) { super(id, name); this.room = room; }
    async sendText(text, opts) {
      if (this.room.offline) throw new Error('offline');
      this.room.ch.postMessage({ type: 'text', topic: opts.topic, text, from: this.identity, to: opts.destinationIdentities || [] });
      return {};
    }
    getTrackPublication() { return undefined; }
    async streamBytes(opts) {
      const room = this.room, from = this.identity, parts = [];
      return {
        write: async (b) => { parts.push(b); },
        close: async () => { room.ch.postMessage({ type: 'bytes', topic: opts.topic, parts, from, to: opts.destinationIdentities || [] }); },
      };
    }
    async setMicrophoneEnabled(on) { this.isMicrophoneEnabled = on; this.room.ch.postMessage({ type: 'mute', identity: this.identity, on }); }
  }
  class Room {
    constructor() { this.handlers = {}; this.text = {}; this.bytes = {}; this.remoteParticipants = new Map(); this.state = 'disconnected'; this.canPlaybackAudio = true; }
    on(ev, fn) { (this.handlers[ev] = this.handlers[ev] || []).push(fn); return this; }
    emit(ev, ...a) { (this.handlers[ev] || []).forEach((f) => f(...a)); }
    registerTextStreamHandler(t, fn) { this.text[t] = fn; }
    registerByteStreamHandler(t, fn) { this.bytes[t] = fn; }
    async connect(url, token) {
      const { identity, name } = JSON.parse(atob(token));
      this.localParticipant = new LocalP(this, identity, name);
      this.ch = new BroadcastChannel('fake-sfu');
      this.ch.onmessage = (e) => this.onMsg(e.data);
      this.state = 'connected';
      window.__room = this;
      this.ch.postMessage({ type: 'hello', identity, name });
      await new Promise((r) => setTimeout(r, 200));
    }
    onMsg(m) {
      if (this.offline) return;
      if (m.type === 'hello' || m.type === 'here') {
        if (this.remoteParticipants.has(m.identity)) return;
        const p = new Participant(m.identity, m.name);
        this.remoteParticipants.set(m.identity, p);
        if (m.type === 'hello') {
          this.ch.postMessage({ type: 'here', identity: this.localParticipant.identity, name: this.localParticipant.name });
          this.emit(RoomEvent.ParticipantConnected, p);
        }
      } else if (m.type === 'bye') {
        const p = this.remoteParticipants.get(m.identity);
        if (p) { this.remoteParticipants.delete(m.identity); this.emit(RoomEvent.ParticipantDisconnected, p); }
      } else if (m.type === 'text') {
        if (m.to.length && !m.to.includes(this.localParticipant.identity)) return;
        const h = this.text[m.topic];
        if (h) h({ readAll: async () => m.text }, { identity: m.from });
      } else if (m.type === 'bytes') {
        if (m.to.length && !m.to.includes(this.localParticipant.identity)) return;
        const h = this.bytes[m.topic];
        if (h) h({ readAll: async () => m.parts }, { identity: m.from });
      } else if (m.type === 'mute') {
        const p = this.remoteParticipants.get(m.identity);
        if (p) { p.isMicrophoneEnabled = m.on; this.emit(m.on ? RoomEvent.TrackUnmuted : RoomEvent.TrackMuted); }
      }
    }
    // Test hooks: a short signal loss and recovery.
    goOffline() { this.offline = true; this.state = 'reconnecting'; this.emit(RoomEvent.Reconnecting); }
    goOnline() { this.offline = false; this.state = 'connected'; this.emit(RoomEvent.Reconnected); }
    disconnect() { this.ch.postMessage({ type: 'bye', identity: this.localParticipant.identity }); this.state = 'disconnected'; this.emit(RoomEvent.Disconnected); }
    async startAudio() {}
  }
  return { Room, RoomEvent, Track };
})();
