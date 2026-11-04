#!/usr/bin/env python3
import sys
import os
import wave
import numpy as np

def denoise_audio(input_path, output_path=None, strength=1.8):
    if not os.path.exists(input_path):
        print(f"Error: file not found: {input_path}", file=sys.stderr)
        return False

    if output_path is None:
        output_path = input_path

    # Read WAV
    with wave.open(input_path, 'rb') as wf:
        n_channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw_bytes = wf.readframes(n_frames)

    if sampwidth == 2:
        dtype = np.int16
        scale = 32768.0
    elif sampwidth == 4:
        dtype = np.int32
        scale = 2147483648.0
    else:
        # Fallback to int16
        dtype = np.int16
        scale = 32768.0

    audio_int = np.frombuffer(raw_bytes, dtype=dtype)
    if n_channels > 1:
        audio_int = audio_int.reshape(-1, n_channels)[:, 0] # Use primary channel
        n_channels = 1

    audio = audio_int.astype(np.float32) / scale

    if len(audio) < 1024:
        # Too short to denoise
        return True

    # Spectral Wiener Gating
    n_fft = 512
    hop = 128
    window = np.hanning(n_fft)

    num_frames = 1 + (len(audio) - n_fft) // hop
    if num_frames < 2:
        return True

    frames = np.lib.stride_tricks.sliding_window_view(audio[: (num_frames - 1) * hop + n_fft], n_fft)[::hop]
    windowed = frames * window
    stft = np.fft.rfft(windowed, axis=1)
    mag = np.abs(stft)
    phase = np.angle(stft)

    # Energy per frame
    frame_energy = np.mean(mag ** 2, axis=1)

    # Estimate noise from lowest 15% energy frames (speech pauses/background)
    k_frames = max(4, min(len(frame_energy) // 6, 40))
    quiet_indices = np.argsort(frame_energy)[:k_frames]
    noise_est = np.mean(mag[quiet_indices], axis=0) * strength

    # Smooth Wiener attenuation
    snr_post = np.maximum(mag / (noise_est + 1e-8), 1e-4)
    spectral_floor = 0.05
    gain = np.maximum(spectral_floor, 1.0 - (1.0 / snr_post))

    clean_stft = (mag * gain) * np.exp(1j * phase)
    inv_frames = np.fft.irfft(clean_stft, axis=1) * window

    out = np.zeros(len(audio), dtype=np.float32)
    norm = np.zeros(len(audio), dtype=np.float32)

    for i in range(len(inv_frames)):
        start = i * hop
        out[start : start + n_fft] += inv_frames[i]
        norm[start : start + n_fft] += window ** 2

    nonzero = norm > 1e-6
    out[nonzero] /= norm[nonzero]

    # Normalize gain slightly so voice is clear and loud
    peak = np.max(np.abs(out))
    if peak > 1e-4:
        target_peak = 0.92
        out = out * min(target_peak / peak, 2.5)

    # Convert back to int16 PCM
    out_clipped = np.clip(out, -1.0, 1.0)
    out_int16 = (out_clipped * 32767.0).astype(np.int16)

    # Write to temporary file then replace to avoid corruption
    temp_out = output_path + ".tmp.wav"
    with wave.open(temp_out, 'wb') as out_wf:
        out_wf.setnchannels(1)
        out_wf.setsampwidth(2)
        out_wf.setframerate(framerate)
        out_wf.writeframes(out_int16.tobytes())

    os.replace(temp_out, output_path)
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: denoise.py <input_wav> [output_wav] [strength]")
        sys.exit(1)

    in_file = sys.argv[1]
    out_file = sys.argv[2] if len(sys.argv) > 2 else in_file
    st = float(sys.argv[3]) if len(sys.argv) > 3 else 1.8

    ok = denoise_audio(in_file, out_file, strength=st)
    if ok:
        print("DENOISE_SUCCESS")
    else:
        sys.exit(1)
