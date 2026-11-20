"""Read-only scan of tracked and non-ignored files; excludes Git history and binaries.

Run: python -B tools/audit_public_files.py
Only filenames and line numbers are printed, never matching credential values.
"""

import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "personal_path": re.compile(
        r"(?i)(?<![a-z])[a-z](?:\\)?:[\\/]+Users[\\/]+[^\\/\s\x22\x27<>]+"
        r"|/(?:home|Users)/[\w.-]+/"),
    "private_ip": re.compile(
        r"(?<![\d.])(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}"
        r"|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?![\d.])"),
    "mac": re.compile(r"(?i)(?<![0-9a-f])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f])"),
    "credential_format": re.compile(
        r"AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|gh[pousr]_[0-9A-Za-z]{20,}"
        r"|github_pat_[0-9A-Za-z_]{20,}|sk-(?:proj-)?[0-9A-Za-z_-]{20,}"
        r"|xox[baprs]-[0-9A-Za-z-]{10,}"
        r"|-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
}
ARTIFACT_DIRS = {"build", "dist", "node_modules", ".gradle", ".gradle-local", ".cxx",
                 ".externalNativeBuild", "__pycache__", ".pytest_cache"}


def audit():
    listing = subprocess.check_output(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT
    ).decode("utf-8").split("\0")
    findings = []
    scanned = skipped = 0
    for relative in sorted(set(filter(None, listing))):
        path = ROOT / relative
        if not path.is_file():
            continue
        if ARTIFACT_DIRS.intersection(Path(relative).parts):
            findings.append({"file": relative, "kind": "build_or_cache_artifact"})
        with path.open("rb") as stream:
            head = stream.read(8192)
            if b"\0" in head and not head.startswith((b"\xff\xfe", b"\xfe\xff")):
                skipped += 1
                continue
            raw = head + stream.read()
        try:
            text = raw.decode("utf-16" if raw.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")
        except UnicodeError:
            skipped += 1
            continue
        scanned += 1
        for number, line in enumerate(text.splitlines(), 1):
            for kind, pattern in PATTERNS.items():
                if pattern.search(line):
                    findings.append({"file": relative, "line": number, "kind": kind})
    return {"scope": "current tracked and non-ignored working-tree text; excludes Git history",
            "text_files": scanned, "binary_or_unsupported_files": skipped, "findings": findings}


if __name__ == "__main__":
    result = audit()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(bool(result["findings"]))
