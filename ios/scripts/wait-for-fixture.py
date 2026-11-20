import sys
import time
import urllib.request
from urllib.error import URLError

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
deadline = time.monotonic() + 20
last_error = None
while time.monotonic() < deadline:
    try:
        with opener.open("http://127.0.0.1:8765/health", timeout=1) as response:
            if response.read() == b"ok":
                print("CI fixture is ready on loopback")
                sys.exit(0)
    except (OSError, URLError) as error:
        last_error = error
    time.sleep(0.2)
raise RuntimeError(f"CI fixture did not become ready: {last_error}")
