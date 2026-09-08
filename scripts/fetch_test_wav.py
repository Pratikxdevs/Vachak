#!/usr/bin/env python3
"""
Fetch or generate a 16k mono PCM16 Hindi test WAV for emulator/headless ASR verification.

Outputs to: android/app/src/main/assets/test/hindi_sample.wav
Validates: RIFF, 16k, mono, PCM16, data chunk.

If a dataset sample is available at datasets/hindi_sample.wav, it will be converted via
ffmpeg/sox if needed. Otherwise a synthetic 1s 440Hz tone + silence WAV is generated (valid
format, not linguistic — sufficient for plumbing verification; real Hindi sample should replace
this before final demo).

Usage:
  python scripts/fetch_test_wav.py
  python scripts/fetch_test_wav.py --check  # validate existing asset
"""
import argparse
import struct
import math
import os
import pathlib

OUT = pathlib.Path("android/app/src/main/assets/test/hindi_sample.wav")
OUT.parent.mkdir(parents=True, exist_ok=True)
SAMPLE_RATE = 16000
CHANNELS = 1
BITS = 16

def write_wav(path: pathlib.Path, pcm: list, sample_rate: int = 16000):
    """Write mono PCM16 little-endian WAV with proper RIFF header and data chunk."""
    num_samples = len(pcm)
    byte_rate = sample_rate * CHANNELS * BITS // 8
    block_align = CHANNELS * BITS // 8
    data_bytes = struct.pack("<" + "h" * num_samples, *pcm)
    # fmt chunk size 16 for PCM
    with open(path, "wb") as f:
        # RIFF header
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + len(data_bytes)))
        f.write(b"WAVE")
        # fmt chunk
        f.write(b"fmt ")
        f.write(struct.pack("<I", 16))  # PCM
        f.write(struct.pack("<H", 1))  # audio format PCM
        f.write(struct.pack("<H", CHANNELS))
        f.write(struct.pack("<I", sample_rate))
        f.write(struct.pack("<I", byte_rate))
        f.write(struct.pack("<H", block_align))
        f.write(struct.pack("<H", BITS))
        # data chunk
        f.write(b"data")
        f.write(struct.pack("<I", len(data_bytes)))
        f.write(data_bytes)
    print(f"Wrote {path} : {num_samples} samples @ {sample_rate} Hz, {len(data_bytes)} bytes PCM, total {path.stat().st_size} bytes")

def generate_synthetic():
    """Generate 1.5s: 0.3s silence, 0.8s 220Hz tone, 0.4s silence — placeholder for नमस्ते."""
    sr = SAMPLE_RATE
    pcm = []
    # 0.3s silence
    pcm.extend([0] * int(0.3 * sr))
    # 0.8s tone at 220Hz (audible speech placeholder)
    freq = 220.0
    amp = 8000
    for i in range(int(0.8 * sr)):
        v = int(amp * math.sin(2 * math.pi * freq * i / sr))
        # add second harmonic for richer waveform
        v += int(amp * 0.3 * math.sin(2 * math.pi * freq * 2 * i / sr))
        pcm.append(max(-32767, min(32767, v)))
    # 0.4s silence
    pcm.extend([0] * int(0.4 * sr))
    return pcm

def validate_wav(path: pathlib.Path):
    with open(path, "rb") as f:
        b = f.read()
    assert len(b) > 44, "too small"
    assert b[0:4] == b"RIFF", "missing RIFF"
    assert b[8:12] == b"WAVE", "missing WAVE"
    sr = struct.unpack("<I", b[24:28])[0]
    ch = struct.unpack("<H", b[22:24])[0]
    bits = struct.unpack("<H", b[34:36])[0]
    # find data chunk
    off = 12
    found = False
    while off + 8 <= len(b):
        cid = b[off:off+4]
        sz = struct.unpack("<I", b[off+4:off+8])[0]
        if cid == b"data":
            found = True
            break
        off += 8 + sz + (sz % 2)
    assert found, "no data chunk"
    assert sr == 16000, f"sampleRate {sr} != 16000"
    assert ch == 1, f"channels {ch} != 1"
    assert bits == 16, f"bits {bits} != 16"
    pcm_bytes = b[off+8:off+8+sz]
    shorts = len(pcm_bytes)//2
    print(f"OK {path}: {sr} Hz, {ch} ch, {bits}-bit, {shorts} samples, {len(b)} bytes")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate existing WAV")
    args = parser.parse_args()
    if args.check:
        if not OUT.exists():
            print(f"Missing {OUT}")
            return 1
        validate_wav(OUT)
        return 0
    # If datasets has a real Hindi wav, try to use it (placeholder: not implemented without ffmpeg)
    # For now, generate synthetic placeholder — real pipeline replaces with Common Voice / IndicVoices clip via ffmpeg conversion
    pcm = generate_synthetic()
    write_wav(OUT, pcm, SAMPLE_RATE)
    validate_wav(OUT)
    print(f"Done. Place real Hindi 16k mono PCM16 sample at {OUT} to replace synthetic placeholder before demo.")

if __name__ == "__main__":
    import sys
    sys.exit(main())
