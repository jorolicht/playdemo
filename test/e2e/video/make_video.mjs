#!/usr/bin/env node
/**
 * make_video.mjs - Erzeugt das Werbevideo (Demo-Turnier) als out/promo.mp4.
 *
 * Ablauf:
 *   1. Sprechertexte aus narration.json mit macOS 'say' vertonen (out/voice/*.wav, durations.json)
 *   2. Aufnahme mit Playwright (promo.spec.ts) -> out/raw.webm, out/timeline.json
 *   3. Musik: MUSIC=<datei> oder synthetischer Platzhalter-Jingle
 *   4. Mischen mit ffmpeg: Sprache zeitgenau, Musik mit Ducking (sidechaincompress), H.264/AAC
 *
 * Verwendung (aus test/e2e):
 *   node video/make_video.mjs                 # alles
 *   MUSIC=track.mp3 node video/make_video.mjs # eigene, lizenzfreie Musik
 *   SKIP_RECORD=1 node video/make_video.mjs   # nur neu mischen (vorhandene Aufnahme)
 *
 * Voraussetzungen: macOS 'say', ffmpeg/ffprobe im PATH, laufende Umgebung unter WP_URL (test/e2e/.env).
 */
import { execFileSync, spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(DIR, 'out');
const VOICE = path.join(OUT, 'voice');

/** Führt ein Programm aus und bricht bei Fehler mit dessen Ausgabe ab. */
function run(cmd, args, opts = {}) {
  return execFileSync(cmd, args, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'pipe'], ...opts });
}

/** Dauer einer Mediendatei in Sekunden. */
function duration(file) {
  return parseFloat(run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]));
}

for (const tool of ['say', 'ffmpeg', 'ffprobe']) {
  if (spawnSync('which', [tool]).status !== 0) {
    console.error(`❌ ${tool} nicht gefunden (ffmpeg: brew install ffmpeg)`);
    process.exit(1);
  }
}
fs.mkdirSync(VOICE, { recursive: true });

// --- 1. Sprache -----------------------------------------------------------------------------
const narration = JSON.parse(fs.readFileSync(path.join(DIR, 'narration.json'), 'utf-8'));
const durations = {};
for (const seg of narration.segments) {
  const aiff = path.join(VOICE, `${seg.id}.aiff`);
  const wav = path.join(VOICE, `${seg.id}.wav`);
  run('say', ['-v', narration.voice, '-r', String(narration.rate), '-o', aiff, seg.text]);
  run('ffmpeg', ['-y', '-v', 'error', '-i', aiff, '-ar', '48000', '-ac', '2', wav]);
  fs.rmSync(aiff);
  durations[seg.id] = duration(wav);
  console.log(`🎙  ${seg.id.padEnd(12)} ${durations[seg.id].toFixed(1)} s`);
}
fs.writeFileSync(path.join(VOICE, 'durations.json'), JSON.stringify(durations, null, 2));

// --- 2. Aufnahme ----------------------------------------------------------------------------
if (!process.env.SKIP_RECORD) {
  console.log('🎬 Aufnahme mit Playwright ...');
  const res = spawnSync('npx', ['playwright', 'test', '-c', path.join(DIR, 'playwright.video.config.ts')],
    { cwd: path.join(DIR, '..'), stdio: 'inherit' });
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
  `[0:v]fps=30,format=yuv420p,fade=in:st=0:d=0.6,fade=out:st=${len - 1}:d=1[v]`,
);
const mp4 = path.join(OUT, 'promo.mp4');
console.log('🎚  Mischen ...');
run('ffmpeg', ['-y', '-v', 'error', ...inputs, '-filter_complex', filters.join(';'),
  '-map', '[v]', '-map', '[a]', '-t', String(len),
  '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-c:a', 'aac', '-b:a', '192k',
  '-movflags', '+faststart', mp4]);
console.log(`✅ ${mp4} (${duration(mp4).toFixed(1)} s)`);
