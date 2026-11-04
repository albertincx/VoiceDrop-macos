#!/usr/bin/env python3
import sys
import json
import os
from faster_whisper import WhisperModel

def transcribe_audio(audio_path, model_name="large-v3-turbo", language=None):
    if not os.path.exists(audio_path):
        return {"error": f"Audio file not found: {audio_path}"}
    
    # Check if local cache has large-v3-turbo
    try:
        model = WhisperModel(model_name, device="cpu", compute_type="int8", local_files_only=True)
    except Exception as e:
        # Fallback to local_files_only=False or tiny if large not available
        try:
            model = WhisperModel("tiny", device="cpu", compute_type="int8", local_files_only=True)
        except Exception as e2:
            model = WhisperModel(model_name, device="cpu", compute_type="int8", local_files_only=False)

    kwargs = {"beam_size": 5}
    if language and language.strip() and language != "auto":
        kwargs["language"] = language.strip()

    segments, info = model.transcribe(audio_path, **kwargs)
    text_segments = []
    full_text = []

    for segment in segments:
        text_segments.append({
            "start": segment.start,
            "end": segment.end,
            "text": segment.text
        })
        full_text.append(segment.text.strip())

    result = {
        "text": " ".join(full_text).strip(),
        "language": info.language,
        "language_probability": info.language_probability,
        "duration": info.duration,
        "segments": text_segments
    }
    return result

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: transcribe.py <audio_file> [language] [model]"}))
        sys.exit(1)

    file_path = sys.argv[1]
    lang = sys.argv[2] if len(sys.argv) > 2 else "auto"
    model = sys.argv[3] if len(sys.argv) > 3 else "large-v3-turbo"

    try:
        res = transcribe_audio(file_path, model_name=model, language=lang)
        print(json.dumps(res, ensure_ascii=False))
    except Exception as err:
        print(json.dumps({"error": str(err)}))
        sys.exit(1)
