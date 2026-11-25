"""Check or sync launcher picker artwork from the Glance Roborazzi golden images."""

import argparse
from pathlib import Path
import shutil
import struct


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SCREENSHOTS = ROOT / "app/src/test/screenshots"
LANGUAGES = {"en": "en", "ja": "ja", "hans": "zh", "hant": "b+zh+Hant"}
SIZES = {"single": (384, 192), "panel": (480, 432), "status": (480, 432)}


def artwork_mapping():
    for language, qualifier in LANGUAGES.items():
        for mode, night in (("light", ""), ("dark", "-night")):
            for kind, size in SIZES.items():
                source = SCREENSHOTS / f"widget_picker_{language}_{kind}_{mode}.webp"
                targets = [
                    RES / f"drawable{night}-nodpi/widget_picker_{language}_{kind}.webp",
                    RES / f"drawable-{qualifier}{night}-nodpi/widget_preview_{kind}.webp",
                ]
                if language == "en":
                    targets.append(RES / f"drawable{night}-nodpi/widget_preview_{kind}.webp")
                yield source, targets, size


def validate_image(path, expected_size):
    data = path.read_bytes()
    if len(data) > 1_000_000:
        raise ValueError(f"{path.relative_to(ROOT)} exceeds the image size budget")
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise ValueError(f"{path.relative_to(ROOT)} is not a WebP image")
    position = 12
    while position + 8 <= len(data):
        chunk = data[position:position + 4]
        length = struct.unpack_from("<I", data, position + 4)[0]
        start = position + 8
        if start + length > len(data):
            raise ValueError(f"{path.relative_to(ROOT)} has an incomplete WebP chunk")
        if chunk == b"VP8L":
            if length < 5 or data[start] != 0x2F:
                raise ValueError(f"{path.relative_to(ROOT)} has an invalid lossless header")
            header = struct.unpack_from("<I", data, start + 1)[0]
            size = ((header & 0x3FFF) + 1, ((header >> 14) & 0x3FFF) + 1)
            if size != expected_size or not header & (1 << 28):
                raise ValueError(f"{path.relative_to(ROOT)} needs size {expected_size} and alpha")
            return data
        position = start + length + (length & 1)
    raise ValueError(f"{path.relative_to(ROOT)} is not lossless WebP")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sync", action="store_true", help="Copy visually reviewed golden images to resources")
    arguments = parser.parse_args()
    mappings = list(artwork_mapping())
    # Validate every source before modifying any Android resource.
    sources = {source: validate_image(source, size) for source, _, size in mappings}
    expected = {target for _, targets, _ in mappings for target in targets}
    if arguments.sync:
        for source, targets, _ in mappings:
            for target in targets:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                target.with_suffix(".png").unlink(missing_ok=True)
    for source, targets, size in mappings:
        for target in targets:
            if validate_image(target, size) != sources[source]:
                raise ValueError(f"{target.relative_to(ROOT)} differs from {source.name}")
    actual = {
        path for path in RES.glob("drawable*/*")
        if path.suffix in {".png", ".webp"}
        and path.name.startswith(("widget_picker_", "widget_preview_"))
    }
    if actual != expected:
        raise ValueError("Unexpected or missing widget preview resources: " +
                         ", ".join(str(path.relative_to(ROOT)) for path in sorted(actual ^ expected)))
    print(f"Widget picker artwork: {len(sources)} golden images, {len(expected)} lossless resources verified")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error
