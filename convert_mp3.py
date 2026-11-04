#!/usr/bin/env python3
import sys
import os
import subprocess

def convert_wav_to_mp3(input_path, output_path=None):
    if not os.path.exists(input_path):
        print(f"Error: input file not found: {input_path}", file=sys.stderr)
        return False

    if output_path is None:
        base, _ = os.path.splitext(input_path)
        output_path = base + ".mp3"

    # Try ffmpeg first if available
    ffmpeg_paths = ["/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg", "ffmpeg"]
    for ffmpeg in ffmpeg_paths:
        try:
            res = subprocess.run(
                [ffmpeg, "-y", "-i", input_path, "-codec:a", "libmp3lame", "-qscale:a", "2", output_path],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            if res.returncode == 0 and os.path.exists(output_path):
                print(output_path)
                return True
        except Exception:
            continue

    # Fallback to PyAV
    try:
        import av
        input_container = av.open(input_path)
        output_container = av.open(output_path, 'w', format='mp3')
        in_stream = input_container.streams.audio[0]
        out_stream = output_container.add_stream('mp3', rate=in_stream.rate)
        out_stream.bit_rate = 192000
        for frame in input_container.decode(in_stream):
            for packet in out_stream.encode(frame):
                output_container.mux(packet)
        for packet in out_stream.encode(None):
            output_container.mux(packet)
        output_container.close()
        input_container.close()
        if os.path.exists(output_path):
            print(output_path)
            return True
    except Exception as e:
        print(f"PyAV conversion failed: {e}", file=sys.stderr)

    return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: convert_mp3.py <input_wav> [output_mp3]")
        sys.exit(1)

    in_f = sys.argv[1]
    out_f = sys.argv[2] if len(sys.argv) > 2 else None
    success = convert_wav_to_mp3(in_f, out_f)
    if not success:
        sys.exit(1)
