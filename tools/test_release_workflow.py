"""Exercise the release workflow's APK checks without building or publishing."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/release.yml"


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        match = re.search(
            r"      - name: Prepare release artifact\n        run: \|\n(.*?)(?=\n      - name:)",
            self.workflow,
            re.DOTALL,
        )
        self.assertIsNotNone(match)
        self.script = textwrap.dedent(match.group(1))
        self.temporary = tempfile.TemporaryDirectory(prefix="release-workflow-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.output = self.root / "app/build/outputs/apk/release"
        self.output.mkdir(parents=True)
        self.apk = self.output / "app-release.apk"
        self.apk.write_bytes(b"apk fixture")
        sdk = self.root / "android sdk"
        analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
        analyzer.parent.mkdir(parents=True)
        analyzer.write_text(
            '#!/usr/bin/env bash\n'
            '[[ "$#" == 3 && "$1" == manifest && "$2" == version-name && -f "$3" ]] || exit 2\n'
            'printf "%s\\n" "$TEST_APK_VERSION"\n'
            'exit "${TEST_ANALYZER_EXIT:-0}"\n',
            encoding="utf-8",
        )
        analyzer.chmod(0o755)
        self.environment = dict(os.environ, ANDROID_HOME=sdk.as_posix())
        self.bash = shutil.which("bash")
        if self.bash is None and os.name == "nt":
            candidate = Path(os.environ.get("ProgramFiles", "")) / "Git/bin/bash.exe"
            if candidate.is_file():
                self.bash = str(candidate)
        self.assertIsNotNone(self.bash, "Bash is required to test the Ubuntu release workflow")

    def prepare(self, tag, version, analyzer_exit=0):
        result = subprocess.run(
            [self.bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", self.script],
            cwd=self.root,
            env=dict(self.environment, GITHUB_REF_NAME=tag, TEST_APK_VERSION=version,
                     TEST_ANALYZER_EXIT=str(analyzer_exit)),
            capture_output=True,
            text=True,
            timeout=15,
        )
        return result, self.output / f"Rimoat-{tag}.apk"

    def test_matching_stable_and_prerelease_apks_are_prepared(self):
        for version in ("1.2.0", "1.2.0-rc1"):
            with self.subTest(version=version):
                result, artifact = self.prepare(f"v{version}", version)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(self.apk.read_bytes(), artifact.read_bytes())

    def test_mismatched_versions_fail_before_preparing_artifact(self):
        for version in ("1.1.0", "1.2", "1.2.0-rc1", ""):
            with self.subTest(version=version):
                result, artifact = self.prepare("v1.2.0", version)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(artifact.exists())

    def test_prerelease_tag_requires_matching_apk_suffix(self):
        result, artifact = self.prepare("v1.2.0-rc1", "1.2.0")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(artifact.exists())

    def test_analyzer_failure_stops_release_even_with_matching_output(self):
        result, artifact = self.prepare("v1.2.0", "1.2.0", analyzer_exit=1)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(artifact.exists())

    def test_missing_apk_stops_release(self):
        self.apk.unlink()
        result, artifact = self.prepare("v1.2.0", "1.2.0")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(artifact.exists())

    def test_release_action_marks_tags_containing_hyphen_as_prereleases(self):
        release_step = self.workflow.split("      - name: Create GitHub Release\n", 1)[1]
        self.assertRegex(release_step, r"(?m)^          prerelease: \$\{\{ contains\(github\.ref_name, '-'\) \}\}$")

    def test_artifact_validation_runs_after_build_and_before_publication(self):
        self.assertLess(self.workflow.index("- name: Build release APK"),
                        self.workflow.index("- name: Prepare release artifact"))
        self.assertLess(self.workflow.index("- name: Prepare release artifact"),
                        self.workflow.index("- name: Create GitHub Release"))


if __name__ == "__main__":
    unittest.main()
