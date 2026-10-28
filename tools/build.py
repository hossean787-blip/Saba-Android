"""Build the standalone offline HTML using only Python's standard library."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
template = (root / "src/index.template.html").read_text(encoding="utf-8")
for marker, filename in [("/* SABA_STYLES */", "styles.css"), ("/* SABA_SCRIPT */", "app.js")]:
    if template.count(marker) != 1:
        raise ValueError(f"Expected exactly one build marker: {marker}")
    template = template.replace(marker, (root / "src" / filename).read_text(encoding="utf-8"))
(root / "Saba_Offline_App.html").write_text(template, encoding="utf-8")
print("Built Saba_Offline_App.html")
