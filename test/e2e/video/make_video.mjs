#!/usr/bin/env node
/**
 * make_video.mjs - Erzeugt das Werbevideo (Demo-Turnier) als out/promo.mp4.
 *
 * Ablauf:
 *   1. Sprechertexte aus narration.json vertonen (out/voice/*.wav, durations.json):
 *      engine "azure" (Azure Speech, z. B. de-DE-KatjaNeural) oder "say" (macOS-Systemstimme)
 *   2. Aufnahme mit Playwright (promo.spec.ts) -> out/raw.webm, out/timeline.json
 *   3. Musik: MUSIC=<datei> oder synthetischer Platzhalter-Jingle
 *   4. Mischen mit ffmpeg: Sprache zeitgenau, Musik mit Ducking (sidechaincompress), H.264/AAC
 *
 * Verwendung (aus test/e2e):
 *   node video/make_video.mjs                 # alles
 *   MUSIC=track.mp3 node video/make_video.mjs # eigene, lizenzfreie Musik
 *   SKIP_RECORD=1 node video/make_video.mjs   # nur neu mischen (vorhandene Aufnahme)
 *   TTS_ENGINE=say node video/make_video.mjs  # Engine aus narration.json überschreiben
 *   PROMO_VARIANT=reel node video/make_video.mjs  # Instagram-Reel 9:16 mit Untertiteln (out/reel/,
 *                                                 # Texte aus narration_reel.json, zusätzlich subtitles.srt)
 *
 * Voraussetzungen: ffmpeg/ffprobe im PATH, laufende Umgebung unter WP_URL (test/e2e/.env);
 * für Azure AZURE_SPEECH_KEY und AZURE_SPEECH_REGION (Umgebung oder test/e2e/.env), für say macOS.
 */
import { execFileSync, spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

const DIR = path.dirname(fileURLToPath(import.meta.url));
// Varianten: Querformat (Standard) und Instagram-Reel (Hochformat, Untertitel im Bild)
const VARIANTS = {
  landscape: { narration: 'narration.json', out: 'out', size: '1920:1080' },
  reel: { narration: 'narration_reel.json', out: 'out/reel', size: '1080:1920' },
};
const variantName = process.env.PROMO_VARIANT || 'landscape';
const variant = VARIANTS[variantName];
if (!variant) {
  console.error(`❌ Unbekannte PROMO_VARIANT "${variantName}" (erlaubt: ${Object.keys(VARIANTS).join(', ')})`);
  process.exit(1);
}
const OUT = path.join(DIR, variant.out);
const VOICE = path.join(OUT, 'voice');
dotenv.config({ path: path.join(DIR, '..', '.env'), quiet: true });

/** Führt ein Programm aus und bricht bei Fehler mit dessen Ausgabe ab. */
function run(cmd, args, opts = {}) {
  return execFileSync(cmd, args, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'pipe'], ...opts });
}

/** Dauer einer Mediendatei in Sekunden. */
function duration(file) {
  return parseFloat(run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]));
}

const narration = JSON.parse(fs.readFileSync(path.join(DIR, variant.narration), 'utf-8'));
const engine = process.env.TTS_ENGINE || narration.engine;
for (const tool of engine === 'say' ? ['say', 'ffmpeg', 'ffprobe'] : ['ffmpeg', 'ffprobe']) {
  if (spawnSync('which', [tool]).status !== 0) {
    console.error(`❌ ${tool} nicht gefunden (ffmpeg: brew install ffmpeg)`);
    process.exit(1);
  }
}
fs.mkdirSync(VOICE, { recursive: true });

// --- 1. Sprache -----------------------------------------------------------------------------
/** Vertont einen Text mit Azure Speech (REST) und speichert ihn als WAV (48 kHz, mono). */
async function azureTts(text, file) {
  const { AZURE_SPEECH_KEY: key, AZURE_SPEECH_REGION: region } = process.env;
  if (!key || !region) {
    console.error('❌ AZURE_SPEECH_KEY und AZURE_SPEECH_REGION fehlen (test/e2e/.env oder Umgebung)');
    process.exit(1);
  }
  const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const { voice, rate } = narration.azure;
  const ssml = `<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="de-DE">` +
    `<voice name="${voice}"><prosody rate="${rate}">${esc(text)}</prosody></voice></speak>`;
  const res = await fetch(`https://${region}.tts.speech.microsoft.com/cognitiveservices/v1`, {
    method: 'POST',
    headers: {
      'Ocp-Apim-Subscription-Key': key,
      'Content-Type': 'application/ssml+xml',
      'X-Microsoft-OutputFormat': 'riff-48khz-16bit-mono-pcm',
      'User-Agent': 'tourney-promo-video',
    },
    body: ssml,
  });
  if (!res.ok) {
    console.error(`❌ Azure Speech: HTTP ${res.status} ${await res.text()}`);
    process.exit(1);
  }
  fs.writeFileSync(file, Buffer.from(await res.arrayBuffer()));
}

console.log(`🗣  Sprachausgabe: ${engine === 'say' ? `say ${narration.say.voice}` : `Azure ${narration.azure.voice}`}`);
const durations = {};
for (const seg of narration.segments) {
  const raw = path.join(VOICE, `${seg.id}.${engine === 'say' ? 'aiff' : 'raw.wav'}`);
  const wav = path.join(VOICE, `${seg.id}.wav`);
  if (engine === 'say') run('say', ['-v', narration.say.voice, '-r', String(narration.say.rate), '-o', raw, seg.text]);
  else await azureTts(seg.text, raw);
  run('ffmpeg', ['-y', '-v', 'error', '-i', raw, '-ar', '48000', '-ac', '2', wav]);
  fs.rmSync(raw);
  durations[seg.id] = duration(wav);
  console.log(`🎙  ${seg.id.padEnd(12)} ${durations[seg.id].toFixed(1)} s`);
}
fs.writeFileSync(path.join(VOICE, 'durations.json'), JSON.stringify(durations, null, 2));

// --- 2. Aufnahme ----------------------------------------------------------------------------
if (!process.env.SKIP_RECORD) {
  console.log('🎬 Aufnahme mit Playwright ...');
  const res = spawnSync('npx', ['playwright', 'test', '-c', path.join(DIR, 'playwright.video.config.ts')],
    { cwd: path.join(DIR, '..'), stdio: 'inherit', env: { ...process.env, PROMO_VARIANT: variantName } });
  if (res.status !== 0) process.exit(res.status ?? 1);
}
const timeline = JSON.parse(fs.readFileSync(path.join(OUT, 'timeline.json'), 'utf-8'));
const len = (timeline.endMs - timeline.trimStartMs) / 1000;

// --- 3. Musik -------------------------------------------------------------------------------
let music = process.env.MUSIC ? path.resolve(process.env.MUSIC) : '';
if (!music) {
  // Platzhalter: 120 BPM, Akkordfolge C - Am - F - G (je 2 s), Bass, Kick und Hi-Hat
  music = path.join(OUT, 'music_placeholder.wav');
  const x = 'mod(t,2)';
  const f = 'if(lt(mod(t,8),2),261.63,if(lt(mod(t,8),4),220,if(lt(mod(t,8),6),174.61,196)))';
  const third = 'if(between(mod(t,8),2,4),1.1892,1.2599)';
  const env = `min(1,${x}*6)*min(1,(2-${x})*6)`;
  const pad = (detune) =>
    `0.10*${env}*(sin(2*PI*${f}*${detune}*t)+sin(2*PI*${f}*${third}*${detune}*t)+sin(2*PI*${f}*1.4983*${detune}*t))`;
  const beat = 'mod(t,0.5)';
  const bass = `0.16*sin(2*PI*${f}/2*t)*exp(-${beat}*4)*min(1,${beat}*200)`;
  const kick = `0.30*sin(2*PI*(45+90*exp(-${beat}*30))*${beat})*exp(-${beat}*10)`;
  const hat = '0.035*(random(0)*2-1)*exp(-mod(t+0.25,0.5)*60)';
  const ch = (detune) => `${pad(detune)}+${bass}+${kick}+${hat}`;
  run('ffmpeg', ['-y', '-v', 'error', '-f', 'lavfi',
    '-i', `aevalsrc='${ch(1)}|${ch(1.003)}':s=48000:d=${Math.ceil(len) + 1}`,
    '-af', 'lowpass=f=6000,aecho=0.8:0.6:120:0.25,loudnorm=I=-20:TP=-2', music]);
  console.log('🎵 Platzhalter-Jingle erzeugt (eigene Musik: MUSIC=<datei>)');
}

// --- 4. Mischen -----------------------------------------------------------------------------
const inputs = ['-ss', String(timeline.trimStartMs / 1000), '-i', path.join(OUT, 'raw.webm'),
  '-stream_loop', '-1', '-i', music];
const voiceLabels = [];
const filters = [];
timeline.segments.forEach((seg, i) => {
  inputs.push('-i', path.join(VOICE, `${seg.id}.wav`));
  const delay = Math.max(0, seg.startMs - timeline.trimStartMs);
  filters.push(`[${i + 2}:a]adelay=${delay}|${delay}[v${i}]`);
  voiceLabels.push(`[v${i}]`);
});
filters.push(
  `${voiceLabels.join('')}amix=inputs=${voiceLabels.length}:normalize=0,volume=1.6,asplit[voice][side]`,
  `[1:a]aformat=channel_layouts=stereo,atrim=0:${len},volume=0.55,afade=in:st=0:d=0.5,afade=out:st=${len - 3}:d=3[music]`,
  // Ducking: Musik wird leiser (ca. 10 dB), sobald Sprache anliegt
  '[music][side]sidechaincompress=threshold=0.05:ratio=4:attack=40:release=600[ducked]',
  // Lautheit für Web/Social Media: -16 LUFS, True Peak -1,5 dB
  '[ducked][voice]amix=inputs=2:normalize=0,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[a]',
  `[0:v]fps=30,scale=${variant.size}:flags=lanczos,format=yuv420p,fade=in:st=0:d=0.6,fade=out:st=${len - 1}:d=1[v]`,
);
// Untertitel als SRT (im Reel bereits ins Bild eingeblendet; für Plattformen mit eigener Untertitelspur)
if (timeline.captions?.length) {
  const ts = (ms) => {
    const t = Math.max(0, Math.round(ms - timeline.trimStartMs));
    const p = (n, w = 2) => String(n).padStart(w, '0');
    return `${p(Math.floor(t / 3600000))}:${p(Math.floor(t / 60000) % 60)}:${p(Math.floor(t / 1000) % 60)},${p(t % 1000, 3)}`;
  };
  const srt = timeline.captions.map((c, i) => `${i + 1}\n${ts(c.startMs)} --> ${ts(c.endMs)}\n${c.text}\n`).join('\n');
  fs.writeFileSync(path.join(OUT, 'subtitles.srt'), srt);
}
const mp4 = path.join(OUT, 'promo.mp4');
console.log('🎚  Mischen ...');
run('ffmpeg', ['-y', '-v', 'error', ...inputs, '-filter_complex', filters.join(';'),
  '-map', '[v]', '-map', '[a]', '-t', String(len),
  '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-c:a', 'aac', '-b:a', '192k',
  '-movflags', '+faststart', mp4]);
console.log(`✅ ${mp4} (${duration(mp4).toFixed(1)} s)`);
