"""Verify the packaged HTTP flow; --image also starts and checks a real Docker container."""

import argparse
import io
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile


def request(base_url, path, data=None, content_type=None):
    headers = {"Content-Type": content_type} if content_type else {}
    with urllib.request.urlopen(
        urllib.request.Request(base_url + path, data=data, headers=headers), timeout=90
    ) as response:
        return response.read(), response.headers


def multipart(fields, file=None):
    boundary = "watermark-" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
        )
    if file is not None:
        parts.extend(
            [
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sample.xlsx"\r\n'
                "Content-Type: application/octet-stream\r\n\r\n".encode(),
                file,
                b"\r\n",
            ]
        )
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def verify(base_url):
    deadline = time.monotonic() + 120
    while True:
        try:
            page, _ = request(base_url, "/")
            break
        except (OSError, urllib.error.URLError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)
    assert "Excel 水印工坊" in page.decode(), "Homepage is missing"
    for path in ("/app.js", "/style.css"):
        content, _ = request(base_url, path)
        assert content, f"Missing static asset: {path}"

    sample, _ = request(base_url, "/api/sample")
    data, content_type = multipart({}, sample)
    info, _ = request(base_url, "/api/inspect", data, content_type)
    assert len(json.loads(info)["sheets"]) == 2, "Sample upload was not parsed"

    # The Chinese text exercises AWT/font rendering in the Linux runtime image.
    settings = {"text": "中文水印 · 容器验证", "type": "TEXT"}
    data, content_type = multipart(settings)
    preview, headers = request(base_url, "/api/preview", data, content_type)
    assert headers.get_content_type() == "image/png"
    assert preview.startswith(b"\x89PNG\r\n\x1a\n"), "Watermark preview is not PNG"

    namespace = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    for mode in ("SCREEN", "PRINT", "BOTH"):
        data, content_type = multipart({**settings, "visibility": mode}, sample)
        output, headers = request(base_url, "/api/watermark", data, content_type)
        assert "attachment" in headers.get("Content-Disposition", ""), "Not a download"
        with zipfile.ZipFile(io.BytesIO(output)) as workbook:
            for index in (1, 2):
                sheet = ET.fromstring(workbook.read(f"xl/worksheets/sheet{index}.xml"))
                assert (sheet.find(namespace + "picture") is not None) == (mode != "PRINT")
                assert (sheet.find(namespace + "legacyDrawingHF") is not None) == (mode != "SCREEN")
            assert b"SUM(D2:D86)" in workbook.read("xl/worksheets/sheet2.xml"), "Formula changed"
        print(f"PASS: {mode} upload and Excel download", flush=True)
    print("PASS: page, static assets, sample, upload, Chinese PNG preview and all watermark modes", flush=True)


def verify_container(image, platform):
    container = subprocess.check_output(
        ["docker", "run", "--detach", "--platform", platform, "--publish", "127.0.0.1::18092", image],
        text=True,
    ).strip()
    try:
        bindings = json.loads(subprocess.check_output(
            ["docker", "inspect", "--format", "{{json .NetworkSettings.Ports}}", container], text=True
        ))
        assert set(bindings) == {"18092/tcp"}, "Image must expose exactly one port"
        font = subprocess.check_output(
            ["docker", "exec", container, "fc-match", "--format=%{family}", "Noto Sans CJK SC"], text=True
        )
        assert "Noto Sans CJK SC" in font, "Chinese font is missing"
        port = bindings["18092/tcp"][0]["HostPort"]
        verify(f"http://127.0.0.1:{port}")
        subprocess.run(["docker", "exec", container, "curl", "--fail", "--silent", "http://127.0.0.1:18092/"],
                       check=True, stdout=subprocess.DEVNULL)
    except Exception:
        subprocess.run(["docker", "logs", container], check=False)
        raise
    finally:
        subprocess.run(["docker", "rm", "--force", container], check=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--image")
    source.add_argument("--base-url")
    parser.add_argument("--platform", default="linux/amd64")
    args = parser.parse_args()
    if args.image:
        verify_container(args.image, args.platform)
    else:
        verify(args.base_url.rstrip("/"))
