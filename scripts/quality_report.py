import os
import pathlib
import xml.etree.ElementTree as ET

root = pathlib.Path("app/build")
tests = failures = errors = skipped = 0
details = []
for path in sorted(root.glob("test-results/testDebugUnitTest/TEST-*.xml")):
    suite = ET.parse(path).getroot()
    tests += int(suite.get("tests", 0))
    failures += int(suite.get("failures", 0))
    errors += int(suite.get("errors", 0))
    skipped += int(suite.get("skipped", 0))
    for case in suite.findall("testcase"):
        for kind in ("failure", "error"):
            for failure in case.findall(kind):
                details.append((case.get("classname", "") + "." + case.get("name", ""), failure.get("message", "")))
lint = root / "reports/lint-results-debug.xml"
issues = ET.parse(lint).getroot().findall("issue") if lint.exists() else []
lines = ["## Android quality report", "",
         "| Check | Result |", "| --- | --- |",
         "| Compile | " + os.getenv("COMPILE_RESULT", "unknown") + " |",
         "| Tests | total=" + str(tests) + ", passed=" + str(tests-failures-errors-skipped) + ", failed=" + str(failures+errors) + ", skipped=" + str(skipped) + " |",
         "| Lint | " + (str(len(issues)) + " findings" if lint.exists() else "report unavailable") + " |",
         "| Duration | " + os.getenv("QUALITY_SECONDS", "unknown") + " seconds |", ""]
def clean(value):
    return value.replace("\n", " ").replace("|", "/").replace("<", "&lt;")[:1000]
for name, message in details:
    lines.append("- FAIL " + clean(name) + ": " + clean(message))
for issue in issues:
    lines.append("- Lint " + clean(issue.get("severity", "")) + " " + clean(issue.get("id", "")) + ": " + clean(issue.get("message", "")))
if not tests:
    lines.append("- No JUnit results found; inspect the build log.")
with open(os.environ.get("GITHUB_STEP_SUMMARY", "quality-summary.md"), "a", encoding="utf-8") as output:
    output.write("\n".join(lines) + "\n")

print("\n".join(lines))
