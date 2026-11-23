#!/usr/bin/env python3
import sys
import os
import subprocess

def convert_to_ogg(input_path, output_path=None):
    if not os.path.exists(input_path):
        print(f"Error: input file not found: {input_path}", file=sys.stderr)
        return False

    if output_path is None:
        base, _ = os.path.splitext(input_path)
        output_path = base + ".ogg"

    # Try ffmpeg first if available (Opus in OGG container, then Vorbis fallback)
    ffmpeg_paths = ["/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg", "ffmpeg"]
    ffmpeg_profiles = [
        ["-codec:a", "libopus", "-b:a", "64k", "-vbr", "on", "-application", "voip"],
        ["-codec:a", "libvorbis", "-qscale:a", "4"],
    ]
    for ffmpeg in ffmpeg_paths:
        for codec_args in ffmpeg_profiles:
            try:
                res = subprocess.run(
                    [ffmpeg, "-y", "-i", input_path] + codec_args + [output_path],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL
                )
                if res.returncode == 0 and os.path.exists(output_path) and os.path.getsize(output_path) > 0:
                    print(output_path)
                    return True
            except Exception:
                continue

    # Fallback to PyAV
    for codec_name in ("libopus", "libvorbis"):
        try:
            import av
            input_container = av.open(input_path)
            output_container = av.open(output_path, 'w', format='ogg')
            in_stream = input_container.streams.audio[0]
            out_rate = 48000 if codec_name == "libopus" else in_stream.rate
            out_stream = output_container.add_stream(codec_name, rate=out_rate)
            resampler = av.AudioResampler(
                format=out_stream.codec_context.format,
                layout=out_stream.codec_context.layout,
                rate=out_rate,
            )
            for frame in input_container.decode(in_stream):
                for resampled in resampler.resample(frame):
                    for packet in out_stream.encode(resampled):
                        output_container.mux(packet)
            for packet in out_stream.encode(None):
                output_container.mux(packet)
            output_container.close()
            input_container.close()
            if os.path.exists(output_path) and os.path.getsize(output_path) > 0:
                print(output_path)
                return True
        except Exception as e:
            print(f"PyAV ({codec_name}) conversion failed: {e}", file=sys.stderr)

    return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: convert_ogg.py <input_audio> [output_ogg]")
        sys.exit(1)

    in_f = sys.argv[1]
    out_f = sys.argv[2] if len(sys.argv) > 2 else None
    success = convert_to_ogg(in_f, out_f)
    if not success:
        sys.exit(1)
