#!/usr/bin/env python3
"""
CodeAgent Local APK HTTP Server & Terminal QR Code Generator
Serves the built Android APK over the local network and prints an ANSI QR code
directly in the terminal for instant scanning and installation from Android devices.
"""

import argparse
import datetime
import http.server
import os
import re
import socket
import sys
import urllib.parse

# ANSI terminal colors
COLOR_RESET = "\033[0m"
COLOR_BOLD = "\033[1m"
COLOR_GREEN = "\033[32m"
COLOR_YELLOW = "\033[33m"
COLOR_BLUE = "\033[34m"
COLOR_CYAN = "\033[36m"
COLOR_RED = "\033[31m"


def get_local_ips():
    """Detects reachable local network IPv4 addresses."""
    ips = []
    # 1. Connect dummy socket to external IP to determine default route interface IP
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        primary = s.getsockname()[0]
        s.close()
        if primary and not primary.startswith("127."):
            ips.append(primary)
    except Exception:
        pass

    # 2. Inspect host interfaces
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except Exception:
        pass

    return ips if ips else ["127.0.0.1"]


def print_qr_code(url: str):
    """Renders QR code directly in the terminal using ANSI blocks."""
    # Method 1: Python qrcode module
    try:
        import qrcode

        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=1,
            border=2,
        )
        qr.add_data(url)
        qr.make(fit=True)

        print(f"\n{COLOR_BOLD}Scan this QR code with your Android phone's camera:{COLOR_RESET}\n")
        # Invert=True gives black modules on white background, standard for dark terminals
        qr.print_ascii(invert=True)
        return True
    except ImportError:
        pass

    # Method 2: System qrencode command
    import subprocess
    import shutil

    if shutil.which("qrencode"):
        try:
            print(f"\n{COLOR_BOLD}Scan this QR code with your Android phone's camera:{COLOR_RESET}\n")
            subprocess.run(["qrencode", "-t", "ANSIUTF8", url], check=True)
            return True
        except Exception:
            pass

    # Fallback message
    print(f"\n{COLOR_YELLOW}[NOTE] Install 'qrcode' for terminal QR rendering:{COLOR_RESET}")
    print(f"       pip install qrcode\n")
    return False


def build_landing_page(apk_name: str, apk_size_str: str, build_time_str: str, download_url: str) -> bytes:
    """Generates a modern, clean HTML5 mobile landing page."""
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Install CodeAgent</title>
    <style>
        :root {{
            --bg: #0D1117;
            --surface: #161B22;
            --border: #30363D;
            --text: #E6EDF3;
            --text-secondary: #8B949E;
            --accent: #238636;
            --accent-hover: #2EA043;
            --accent-glow: rgba(35, 134, 54, 0.4);
            --card-bg: #21262D;
            --primary: #58A6FF;
        }}
        * {{ box-sizing: border-box; margin: 0; padding: 0; }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 20px;
        }}
        .card {{
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 20px;
            max-width: 440px;
            width: 100%;
            padding: 32px 24px;
            box-shadow: 0 16px 36px rgba(0, 0, 0, 0.6);
            text-align: center;
        }}
        .icon-wrapper {{
            width: 76px;
            height: 76px;
            margin: 0 auto 20px;
            background: linear-gradient(135deg, #1F6FEB, #238636);
            border-radius: 22px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 8px 24px var(--accent-glow);
        }}
        .icon-wrapper svg {{
            width: 44px;
            height: 44px;
            fill: #ffffff;
        }}
        h1 {{
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 6px;
            letter-spacing: -0.5px;
        }}
        .subtitle {{
            font-size: 14px;
            color: var(--text-secondary);
            margin-bottom: 24px;
        }}
        .meta-box {{
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 14px 16px;
            margin-bottom: 24px;
            text-align: left;
            font-size: 13px;
        }}
        .meta-row {{
            display: flex;
            justify-content: space-between;
            padding: 4px 0;
        }}
        .meta-label {{ color: var(--text-secondary); }}
        .meta-value {{ font-family: monospace; font-weight: 600; color: var(--text); }}
        .download-btn {{
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            background: var(--accent);
            color: #ffffff;
            text-decoration: none;
            font-size: 16px;
            font-weight: 600;
            padding: 16px 20px;
            border-radius: 12px;
            transition: all 0.2s ease;
            box-shadow: 0 4px 16px var(--accent-glow);
        }}
        .download-btn:hover, .download-btn:active {{
            background: var(--accent-hover);
            transform: translateY(-1px);
        }}
        .download-btn svg {{
            width: 20px;
            height: 20px;
            fill: currentColor;
        }}
        .instructions {{
            margin-top: 24px;
            text-align: left;
            background: rgba(33, 38, 45, 0.5);
            border-radius: 10px;
            padding: 14px;
            font-size: 12px;
            color: var(--text-secondary);
            line-height: 1.6;
        }}
        .instructions ol {{
            padding-left: 18px;
            margin-top: 6px;
        }}
        .instructions li {{ margin-bottom: 4px; }}
        .badge {{
            display: inline-block;
            background: rgba(88, 166, 255, 0.15);
            color: var(--primary);
            font-size: 11px;
            font-weight: 600;
            padding: 3px 8px;
            border-radius: 20px;
            margin-bottom: 12px;
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="icon-wrapper">
            <svg viewBox="0 0 24 24"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>
        </div>
        <span class="badge">Local Dev Build</span>
        <h1>CodeAgent Android</h1>
        <p class="subtitle">AI Coding Agent for Android</p>

        <div class="meta-box">
            <div class="meta-row">
                <span class="meta-label">Package:</span>
                <span class="meta-value">{apk_name}</span>
            </div>
            <div class="meta-row">
                <span class="meta-label">File Size:</span>
                <span class="meta-value">{apk_size_str}</span>
            </div>
            <div class="meta-row">
                <span class="meta-label">Built:</span>
                <span class="meta-value">{build_time_str}</span>
            </div>
        </div>

        <a href="{download_url}" class="download-btn" download>
            <svg viewBox="0 0 24 24"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/></svg>
            Download &amp; Install APK
        </a>

        <div class="instructions">
            <strong>Installation Steps:</strong>
            <ol>
                <li>Tap <strong>Download</strong> above to save the APK.</li>
                <li>When complete, tap <strong>Open</strong> in browser notifications.</li>
                <li>Allow <em>Install unknown apps</em> for your browser if prompted.</li>
            </ol>
        </div>
    </div>
</body>
</html>
"""
    return html.encode("utf-8")


class ApkRequestHandler(http.server.BaseHTTPRequestHandler):
    """Custom HTTP handler serving the APK with Range request support and a landing page."""

    server_version = "CodeAgentInstaller/1.0"

    def __init__(self, apk_path: str, apk_filename: str, *args, **kwargs):
        self.apk_path = apk_path
        self.apk_filename = apk_filename
        self.apk_size = os.path.getsize(apk_path)
        mtime = os.path.getmtime(apk_path)
        dt = datetime.datetime.fromtimestamp(mtime)
        self.build_time_str = dt.strftime("%Y-%m-%d %H:%M")
        size_mb = self.apk_size / (1024 * 1024)
        self.apk_size_str = f"{size_mb:.1f} MB"
        super().__init__(*args, **kwargs)

    def log_message(self, format, *args):
        # Override standard log to output custom styled logs
        client_ip = self.client_address[0]
        ts = datetime.datetime.now().strftime("%H:%M:%S")
        msg = format % args
        sys.stderr.write(f"[{ts}] {COLOR_BLUE}{client_ip}{COLOR_RESET} » {msg}\n")

    def do_HEAD(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.lower()
        if path in ("/", "/index.html"):
            content = build_landing_page(
                self.apk_filename, self.apk_size_str, self.build_time_str, f"/{self.apk_filename}"
            )
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
        else:
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Disposition", f'attachment; filename="{self.apk_filename}"')
            self.send_header("Content-Length", str(self.apk_size))
            self.send_header("Accept-Ranges", "bytes")
            self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        clean_path = parsed.path.strip("/")

        # Serve Web Landing Page
        if clean_path in ("", "index.html", "index"):
            content = build_landing_page(
                self.apk_filename, self.apk_size_str, self.build_time_str, f"/{self.apk_filename}"
            )
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
            return

        # Serve APK for download
        # Match /download or /<any>.apk
        if clean_path in ("download", "apk") or clean_path.endswith(".apk"):
            self.serve_apk_file()
            return

        # Fallback redirect to /
        self.send_response(302)
        self.send_header("Location", "/")
        self.end_headers()

    def serve_apk_file(self):
        range_header = self.headers.get("Range")
        client_ip = self.client_address[0]
        user_agent = self.headers.get("User-Agent", "Unknown")

        is_android = "Android" in user_agent

        try:
            with open(self.apk_path, "rb") as f:
                if range_header:
                    # Parse Range: bytes=start-end
                    match = re.match(r"bytes=(\d+)-(\d*)", range_header)
                    if match:
                        start = int(match.group(1))
                        end = int(match.group(2)) if match.group(2) else self.apk_size - 1
                        end = min(end, self.apk_size - 1)
                        length = end - start + 1

                        f.seek(start)
                        self.send_response(206)
                        self.send_header("Content-Type", "application/vnd.android.package-archive")
                        self.send_header("Content-Disposition", f'attachment; filename="{self.apk_filename}"')
                        self.send_header("Content-Range", f"bytes {start}-{end}/{self.apk_size}")
                        self.send_header("Content-Length", str(length))
                        self.send_header("Accept-Ranges", "bytes")
                        self.end_headers()

                        bytes_sent = 0
                        chunk_size = 64 * 1024
                        while bytes_sent < length:
                            read_len = min(chunk_size, length - bytes_sent)
                            buf = f.read(read_len)
                            if not buf:
                                break
                            self.wfile.write(buf)
                            bytes_sent += len(buf)
                        return

                # Full download
                device_desc = f" ({COLOR_GREEN}Android Device{COLOR_RESET})" if is_android else ""
                print(
                    f"{COLOR_GREEN}[DOWNLOAD STARTED]{COLOR_RESET} Client {COLOR_BOLD}{client_ip}{COLOR_RESET}{device_desc} downloading {self.apk_filename} ({self.apk_size_str})"
                )

                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", f'attachment; filename="{self.apk_filename}"')
                self.send_header("Content-Length", str(self.apk_size))
                self.send_header("Accept-Ranges", "bytes")
                self.end_headers()

                chunk_size = 128 * 1024
                total_sent = 0
                while True:
                    chunk = f.read(chunk_size)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    total_sent += len(chunk)

                print(
                    f"{COLOR_GREEN}[DOWNLOAD COMPLETE]{COLOR_RESET} Successfully sent {self.apk_size_str} to {COLOR_BOLD}{client_ip}{COLOR_RESET}!"
                )
        except (ConnectionResetError, BrokenPipeError):
            print(f"{COLOR_YELLOW}[CLIENT DISCONNECTED]{COLOR_RESET} Download interrupted by client {client_ip}.")
        except Exception as e:
            print(f"{COLOR_RED}[ERROR]{COLOR_RESET} Error transferring APK: {e}")


def run_server(apk_path: str, port: int, bind_addr: str = "0.0.0.0", override_ip: str = ""):
    if not os.path.exists(apk_path):
        print(f"{COLOR_RED}[ERROR]{COLOR_RESET} APK not found at: {apk_path}")
        sys.exit(1)

    apk_filename = os.path.basename(apk_path)
    apk_size_mb = os.path.getsize(apk_path) / (1024 * 1024)

    # Determine advertised host
    local_ips = get_local_ips()
    host_ip = override_ip if override_ip else (local_ips[0] if local_ips else "127.0.0.1")

    # Bind server, fallback to next available port if requested is busy
    actual_port = port
    server = None
    max_tries = 10

    handler_factory = lambda *args, **kwargs: ApkRequestHandler(apk_path, apk_filename, *args, **kwargs)

    for i in range(max_tries):
        try:
            server = http.server.ThreadingHTTPServer((bind_addr, actual_port), handler_factory)
            break
        except OSError as e:
            if e.errno == 98 or "address already in use" in str(e).lower():
                actual_port += 1
            else:
                raise

    if server is None:
        print(f"{COLOR_RED}[ERROR]{COLOR_RESET} Could not bind to ports {port}-{port + max_tries - 1}.")
        sys.exit(1)

    web_url = f"http://{host_ip}:{actual_port}/"
    direct_apk_url = f"http://{host_ip}:{actual_port}/{apk_filename}"

    print(f"\n{COLOR_BOLD}======================================================{COLOR_RESET}")
    print(f"{COLOR_BOLD}           CodeAgent Local Install Server             {COLOR_RESET}")
    print(f"{COLOR_BOLD}======================================================{COLOR_RESET}")
    print(f"  {COLOR_GREEN}✔ APK Package:{COLOR_RESET}    {COLOR_BOLD}{apk_filename}{COLOR_RESET} ({apk_size_mb:.1f} MB)")
    print(f"  {COLOR_GREEN}✔ APK Path:{COLOR_RESET}       {apk_path}")
    print(f"  {COLOR_GREEN}✔ Server Port:{COLOR_RESET}    {actual_port}")
    if len(local_ips) > 1:
        print(f"  {COLOR_BLUE}ℹ Available IPs:{COLOR_RESET}  {', '.join(local_ips)}")

    # Print the QR code
    print_qr_code(web_url)

    print(f"{COLOR_BOLD}Access URLs:{COLOR_RESET}")
    print(f"  {COLOR_CYAN}➜ Web Install Page :{COLOR_RESET}  {COLOR_BOLD}{web_url}{COLOR_RESET}")
    print(f"  {COLOR_CYAN}➜ Direct APK Link   :{COLOR_RESET}  {COLOR_BOLD}{direct_apk_url}{COLOR_RESET}")
    print(f"  {COLOR_CYAN}➜ Localhost Link    :{COLOR_RESET}  http://localhost:{actual_port}/")
    print(f"\n{COLOR_YELLOW}Ensure your Android phone is connected to the same Wi-Fi network.{COLOR_RESET}")
    print(f"Press {COLOR_BOLD}Ctrl+C{COLOR_RESET} to stop the server.\n")
    print(f"{COLOR_BOLD}------------------------------------------------------{COLOR_RESET}")
    print(f"{COLOR_BOLD}Connection Log:{COLOR_RESET}")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\n\n{COLOR_GREEN}[SUCCESS] Server stopped.{COLOR_RESET}")
    finally:
        server.server_close()


def main():
    parser = argparse.ArgumentParser(
        description="Serve Android APK over local Wi-Fi with a terminal QR code."
    )
    parser.add_argument("--apk", required=True, help="Path to APK file")
    parser.add_argument("--port", type=int, default=8080, help="Server port (default: 8080)")
    parser.add_argument("--bind", default="0.0.0.0", help="Bind IP address (default: 0.0.0.0)")
    parser.add_argument("--ip", default="", help="Override advertised IP address in QR code")
    args = parser.parse_args()

    run_server(apk_path=args.apk, port=args.port, bind_addr=args.bind, override_ip=args.ip)


if __name__ == "__main__":
    main()
