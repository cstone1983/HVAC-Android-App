"""Render the alarm notification's sound: res/raw/alarm_alert.wav

Deliberately NOT the siren. The siren is a continuous wail meant to fill a house and keep going
for half an hour; this is a notification, heard once, and on a wall panel it plays over the top
of the real siren. Two wails at once is mud. A short burst of hard two-tone beeps cuts through
the siren instead of fighting it, and on a phone — where there is no siren — it still reads
immediately as something wrong rather than as a message arriving.

Also not the device default alarm, which on this phone is "Morning Xylophone": a gentle
wake-up tone, exactly the thing that made the panels sound comical.

Two bursts of four alternating beeps, about 1.6 s in all. The alternation is what makes it read
as an alert rather than a timer; a single repeated pitch sounds like a microwave.
"""
import math
import os
import struct
import wave

SAMPLE_RATE = 44_100
LOW_HZ = 1_200.0
HIGH_HZ = 1_600.0
BEEP_MS = 100
GAP_MS = 60
BURST_GAP_MS = 250
BURSTS = 2
BEEPS_PER_BURST = 4
AMPLITUDE = int(32767 * 0.80)
ENVELOPE_MS = 5   # fast attack and release, so the edges do not click

out = 'app/src/main/res/raw/alarm_alert.wav'
os.makedirs(os.path.dirname(out), exist_ok=True)

frames = bytearray()


def silence(ms):
    for _ in range(int(SAMPLE_RATE * ms / 1000)):
        frames.extend(struct.pack('<h', 0))


def beep(freq, ms):
    total = int(SAMPLE_RATE * ms / 1000)
    ramp = max(1, int(SAMPLE_RATE * ENVELOPE_MS / 1000))
    for i in range(total):
        s = math.sin(2.0 * math.pi * freq * i / SAMPLE_RATE)
        # Lightly squared: enough odd harmonics to carry over a siren, without the full
        # buzz-saw of a hard clip.
        shaped = max(-1.0, min(1.0, s * 1.8))
        if i < ramp:
            shaped *= i / ramp
        elif i > total - ramp:
            shaped *= (total - i) / ramp
        frames.extend(struct.pack('<h', int(shaped * AMPLITUDE)))


for burst in range(BURSTS):
    for n in range(BEEPS_PER_BURST):
        beep(HIGH_HZ if n % 2 else LOW_HZ, BEEP_MS)
        if n != BEEPS_PER_BURST - 1:
            silence(GAP_MS)
    if burst != BURSTS - 1:
        silence(BURST_GAP_MS)

with wave.open(out, 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SAMPLE_RATE)
    w.writeframes(bytes(frames))

seconds = len(frames) / 2 / SAMPLE_RATE
print('wrote %s  (%.2f s)' % (out, seconds))
