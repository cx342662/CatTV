import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "quality_report.py"

class QualityReportTest(unittest.TestCase):
    def generate(self, xml=None, lint=None):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            if xml is not None:
                results = root / "app/build/test-results/testDebugUnitTest"
                results.mkdir(parents=True)
                (results / "TEST-fixture.xml").write_text(xml, encoding="utf-8")
            if lint is not None:
                reports = root / "app/build/reports"
                reports.mkdir(parents=True)
                (reports / "lint-results-debug.xml").write_text(lint, encoding="utf-8")
            summary = root / "summary.md"
            env = dict(os.environ, GITHUB_STEP_SUMMARY=str(summary), COMPILE_RESULT="success", QUALITY_SECONDS="12")
            result = subprocess.run([sys.executable, str(SCRIPT)], cwd=root, env=env,
                                    capture_output=True, text=True, check=True)
            return summary.read_text(encoding="utf-8"), result.stdout

    def test_counts_and_failed_names_are_visible(self):
        report, log = self.generate('<testsuite tests="4" failures="1" errors="1" skipped="1"><testcase classname="Fixture" name="bad"><failure message="bad value"/></testcase></testsuite>')
        self.assertIn("total=4, passed=1, failed=2, skipped=1", report)
        self.assertIn("Fixture.bad", report)
        self.assertIn("Fixture.bad", log)
        self.assertIn("12 seconds", report)

    def test_missing_results_are_not_reported_as_success(self):
        report, _ = self.generate()
        self.assertIn("No JUnit results found", report)
        self.assertIn("report unavailable", report)

    def test_lint_and_untrusted_messages_are_escaped(self):
        report, _ = self.generate('<testsuite tests="1" failures="0" errors="0" skipped="0"/>',
                                 '<issues><issue severity="Error" id="Fixture" message="&lt;tag&gt;|bad"/></issues>')
        self.assertIn("1 findings", report)
        self.assertIn("&lt;tag>/bad", report)

if __name__ == "__main__":
    unittest.main()
