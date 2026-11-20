import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=8765)
parser.add_argument("--root", type=Path, default=Path("ios/build/fixtures"))
options = parser.parse_args()
base = f"http://127.0.0.1:{options.port}"

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            return self.reply(b"ok", "text/plain")
        if path == "/config.json":
            payload = {"sites": [{"key": "fixture", "name": "CI Fixture Source", "type": 1, "api": base + "/api", "playUrl": base + "/parser?url="}]}
            return self.reply(json.dumps(payload).encode(), "application/json")
        if path == "/api":
            video = {"vod_id": "fixture-1", "vod_name": "CI Fixture Video", "vod_pic": base + "/poster.png", "vod_remarks": "CI generated test media", "vod_year": 2026, "vod_content": "<p>CI-only generated media. Not bundled in the app.</p>", "vod_play_from": "Fixture Line", "vod_play_url": "Episode 1$fixture-video"}
            return self.reply(json.dumps({"class": [{"type_id": 1, "type_name": "Fixture"}], "list": [video], "page": 1, "pagecount": 1}).encode(), "application/json")
        if path == "/parser":
            page = f'<html><body><video muted autoplay playsinline src="{base}/test.mp4"></video></body></html>'
            return self.reply(page.encode(), "text/html")
        if path in {"/test.mp4", "/poster.png"}:
            data = (options.root / path.lstrip("/")).read_bytes()
            mime = "video/mp4" if path.endswith("mp4") else "image/png"
            match = re.fullmatch(r"bytes=(\d+)-(\d*)", self.headers.get("Range", ""))
            if match:
                start = int(match[1])
                end = min(int(match[2]) if match[2] else len(data) - 1, len(data) - 1)
                if start > end:
                    return self.reply(b"", mime, status=416)
                return self.reply(data[start:end + 1], mime, status=206, extra={"Content-Range": f"bytes {start}-{end}/{len(data)}", "Accept-Ranges": "bytes"})
            return self.reply(data, mime, extra={"Accept-Ranges": "bytes"})
        self.reply(b"not found", "text/plain", status=404)

    def reply(self, data, mime, status=200, extra=None):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        for name, value in (extra or {}).items():
            self.send_header(name, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

ThreadingHTTPServer(("127.0.0.1", options.port), Handler).serve_forever()
