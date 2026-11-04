#!/usr/bin/env python3
import os
import subprocess
from PIL import Image, ImageDraw

def create_app_icon():
    size = 1024
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Rounded rectangle background with vibrant gradient
    padding = 64
    r = 200
    
    # Draw background squircle
    draw.rounded_rectangle(
        [padding, padding, size - padding, size - padding],
        radius=r,
        fill=(28, 32, 48, 255),
        outline=(80, 110, 240, 200),
        width=8
    )

    # Draw subtle inner gradient/glow
    draw.ellipse([200, 200, 824, 824], fill=(60, 40, 120, 140))

    # Draw stylized microphone in center
    mic_w = 140
    mic_h = 280
    cx = size // 2
    cy = 460
    
    # Mic capsule
    draw.rounded_rectangle(
        [cx - mic_w//2, cy - mic_h//2, cx + mic_w//2, cy + mic_h//2],
        radius=mic_w//2,
        fill=(66, 153, 225, 255),
        outline=(255, 255, 255, 240),
        width=12
    )

    # Sound waves left & right
    for radius_offset in [130, 210, 290]:
        box = [cx - radius_offset, cy - radius_offset - 20, cx + radius_offset, cy + radius_offset - 20]
        draw.arc(box, start=140, end=220, fill=(160, 190, 255, 220), width=16)
        draw.arc(box, start=-40, end=40, fill=(160, 190, 255, 220), width=16)

    # Mic stand base
    stand_top = cy + mic_h//2 + 30
    draw.arc([cx - 110, cy - 30, cx + 110, cy + mic_h//2 + 30], start=0, end=180, fill=(255, 255, 255, 240), width=16)
    draw.line([cx, stand_top, cx, stand_top + 80], fill=(255, 255, 255, 240), width=16)
    draw.line([cx - 80, stand_top + 80, cx + 80, stand_top + 80], fill=(255, 255, 255, 240), width=18)

    iconset_dir = "/tmp/VoiceDrop.iconset"
    os.makedirs(iconset_dir, exist_ok=True)
    
    icon_sizes = [
        (16, "icon_16x16.png"),
        (32, "icon_16x16@2x.png"),
        (32, "icon_32x32.png"),
        (64, "icon_32x32@2x.png"),
        (128, "icon_128x128.png"),
        (256, "icon_128x128@2x.png"),
        (256, "icon_256x256.png"),
        (512, "icon_256x256@2x.png"),
        (512, "icon_512x512.png"),
        (1024, "icon_512x512@2x.png")
    ]

    for s, name in icon_sizes:
        resized = img.resize((s, s), Image.Resampling.LANCZOS)
        resized.save(os.path.join(iconset_dir, name))

    script_dir = os.path.dirname(os.path.abspath(__file__))
    dest_icns = os.path.join(script_dir, "Resources", "AppIcon.icns")
    subprocess.run(["iconutil", "-c", "icns", iconset_dir, "-o", dest_icns], check=True)
    print(f"Icon generated: {dest_icns}")

if __name__ == "__main__":
    create_app_icon()
