"""Build an offline single-file copy of the visual study using the standard library."""
import base64
import json
import mimetypes
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parent
output = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "ErgoTube-heavenly.html"


def data_url(path):
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode("ascii")


fonts = (root / "assets/fonts.css").read_text()
fonts = re.sub(r"url\(([^)]+)\)", lambda m: f"url({data_url(root / 'assets' / m[1])})", fonts)
css = (root / "style.css").read_text().replace("assets/grain-trix.png", data_url(root / "assets/grain-trix.png"))
images = {p.name: data_url(p) for p in (root / "assets").glob("*.jpg")}
script = (root / "study.js").read_text()
script = script.replace('src="assets/${f.image}"', 'src="${assetUrl(f.image)}"')
script = script.replace("assets/sea.jpg", images["sea.jpg"])
script = script.replace("assets/${image}", "${assetUrl(image)}")
script = "const embeddedAssets=" + json.dumps(images) + ";\nconst assetUrl=name=>embeddedAssets[name];\n" + script
html = (root / "index.html").read_text()
html = html.replace('<link rel="stylesheet" href="assets/fonts.css">', "<style>\n" + fonts + "\n" + css + "\n</style>")
html = html.replace('<link rel="stylesheet" href="style.css">', "")
html = html.replace('<script src="study.js"></script>', "<script>\n" + script + "\n</script>")
licenses = "\n\n".join(p.read_text() for p in sorted((root / "assets").glob("*.txt")))
html = html.replace("</html>", "<!-- Bundled font and icon licenses:\n" + licenses + "\n-->\n</html>")
# Keep the two local reading links valid wherever the copy is stored.
for name in ("proposal.md", "ASSETS.md"):
    html = html.replace(f'href="{name}"', f'href="{(root / name).as_uri()}"')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(html)
print(f"Saved {output} ({output.stat().st_size:,} bytes)")
