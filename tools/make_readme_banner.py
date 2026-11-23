#!/usr/bin/env python3
"""Generate the README banner for GitHub's light and dark themes with Pillow."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "docs/assets"
APP_RES = ROOT / "app/src/main/res"
PANDA = APP_RES / "drawable-nodpi/panda_reading.webp"


def render(background, ink, muted, panel, artwork):
    banner = Image.new("RGB", (1600, 500), background)
    draw = ImageDraw.Draw(banner)
    draw.rounded_rectangle((944, 52, 1512, 448), radius=64, fill=panel)
    draw.ellipse((1070, 90, 1410, 430), fill=artwork)

    panda = Image.open(PANDA).convert("RGBA")
    panda = ImageOps.contain(panda.crop(panda.getbbox()), (366, 350), Image.Resampling.LANCZOS)
    banner.paste(panda, (1240 - panda.width // 2, 250 - panda.height // 2), panda)

    title = ImageFont.truetype(str(APP_RES / "font/poppins_extrabold.ttf"), 108)
    chinese = ImageFont.truetype(str(ROOT / "tools/fonts/NotoSansSC-Bold.otf"), 38)
    english = ImageFont.truetype(str(APP_RES / "font/poppins_medium.ttf"), 25)
    draw.text((96, 95), "LittleStar", fill=ink, font=title, anchor="lt")
    draw.text((102, 239), "给海外华裔孩子的汉字学习 App", fill=ink, font=chinese, anchor="lt")
    draw.text((102, 309), "Learn Chinese characters, stories, and songs",
              fill=muted, font=english, anchor="lt")
    return banner


def main():
    render("#F5F6F7", "#19263D", "#4D5666", "#E4EBFD", "#FFFFFF").save(
        ASSETS / "banner.png", optimize=True)
    render("#171E2B", "#F2F5FC", "#CBD3E4", "#293653", "#E4EBFD").save(
        ASSETS / "banner_dark.png", optimize=True)


if __name__ == "__main__":
    main()
