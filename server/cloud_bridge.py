#!/usr/bin/env python3
"""
VPS Browser Cloud Bridge
Pure Python standard library (RFC 6455 WebSocket & HTTP bridge).
Zero external dependencies (no pip, no apt). Works on any Python 3.7+.
Listens on port 3000 (forwarded as public by GitHub Codespaces Dev Tunnels).
"""
import asyncio
import base64
import hashlib
import struct
import sys
import urllib.parse

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000

async def read_ws_frame(reader):
    try:
        header = await reader.readexactly(2)
    except (asyncio.IncompleteReadError, ConnectionResetError, OSError):
        return 8, b""

    b1, b2 = header[0], header[1]
    fin = (b1 & 0x80) != 0
    opcode = b1 & 0x0F
    is_masked = (b2 & 0x80) != 0
    payload_len = b2 & 0x7F

    if payload_len == 126:
        try:
            ext = await reader.readexactly(2)
        except (asyncio.IncompleteReadError, ConnectionResetError, OSError):
            return 8, b""
        payload_len = struct.unpack(">H", ext)[0]
    elif payload_len == 127:
        try:
            ext = await reader.readexactly(8)
        except (asyncio.IncompleteReadError, ConnectionResetError, OSError):
            return 8, b""
        payload_len = struct.unpack(">Q", ext)[0]

    mask = None
    if is_masked:
        try:
            mask = await reader.readexactly(4)
        except (asyncio.IncompleteReadError, ConnectionResetError, OSError):
            return 8, b""

    try:
        payload = await reader.readexactly(payload_len)
    except (asyncio.IncompleteReadError, ConnectionResetError, OSError):
        return 8, b""

    if is_masked and mask:
        unmasked = bytearray(payload_len)
        for i in range(payload_len):
            unmasked[i] = payload[i] ^ mask[i % 4]
        payload = bytes(unmasked)

    return opcode, payload

def make_ws_frame(data, opcode=2):
    length = len(data)
    b1 = 0x80 | (opcode & 0x0F)
    if length < 126:
        header = bytes([b1, length])
    elif length <= 0xFFFF:
        header = bytes([b1, 126]) + struct.pack(">H", length)
    else:
        header = bytes([b1, 127]) + struct.pack(">Q", length)
    return header + data

async def handle_client(reader, writer):
    try:
        raw_req = b""
        while b"\r\n\r\n" not in raw_req:
            chunk = await reader.read(4096)
            if not chunk:
                writer.close()
                return
            raw_req += chunk
            if len(raw_req) > 65536:
                writer.close()
                return

        head, _ = raw_req.split(b"\r\n\r\n", 1)
        lines = head.decode("utf-8", errors="replace").split("\r\n")
        req_line = lines[0]
        headers = {}
        for line in lines[1:]:
            if ":" in line:
                k, v = line.split(":", 1)
                headers[k.strip().lower()] = v.strip()

        # Handle HTTP health check probes
        if headers.get("upgrade", "").lower() != "websocket":
            body = b"<!DOCTYPE html><html><body><h1>VPS Browser Cloud Bridge Active</h1></body></html>\n"
            resp = (
                b"HTTP/1.1 200 OK\r\n"
                b"Content-Type: text/html; charset=utf-8\r\n"
                b"Content-Length: " + str(len(body)).encode("ascii") + b"\r\n"
                b"Connection: close\r\n\r\n" + body
            )
            writer.write(resp)
            await writer.drain()
            writer.close()
            return

        # Parse target host and port from WebSocket URL (/tunnel?host=...&port=...)
        parts = req_line.split(" ")
        target_path = parts[1] if len(parts) > 1 else ""
        parsed = urllib.parse.urlparse(target_path)
        qs = urllib.parse.parse_qs(parsed.query)
        target_host = qs.get("host", [""])[0]
        target_port_str = qs.get("port", [""])[0]

        if not target_host or not target_port_str:
            writer.close()
            return

        target_port = int(target_port_str)

        # Complete WebSocket handshake
        ws_key = headers.get("sec-websocket-key", "")
        accept_raw = hashlib.sha1((ws_key + GUID).encode("utf-8")).digest()
        accept_str = base64.b64encode(accept_raw).decode("ascii")

        handshake_resp = (
            b"HTTP/1.1 101 Switching Protocols\r\n"
            b"Upgrade: websocket\r\n"
            b"Connection: Upgrade\r\n"
            b"Sec-WebSocket-Accept: " + accept_str.encode("ascii") + b"\r\n\r\n"
        )
        writer.write(handshake_resp)
        await writer.drain()

        # Connect to destination TCP endpoint from inside Cloud VM
        try:
            target_reader, target_writer = await asyncio.wait_for(
                asyncio.open_connection(target_host, target_port),
                timeout=12.0
            )
        except Exception:
            writer.write(make_ws_frame(b"", opcode=8))
            await writer.drain()
            writer.close()
            return

        # Bi-directional stream forwarding
        async def client_to_target():
            try:
                while True:
                    opcode, payload = await read_ws_frame(reader)
                    if opcode == 8: # Close frame
                        break
                    elif opcode in (1, 2): # Text or Binary frame
                        target_writer.write(payload)
                        await target_writer.drain()
                    elif opcode == 9: # Ping frame
                        writer.write(make_ws_frame(payload, opcode=10))
                        await writer.drain()
            except Exception:
                pass
            finally:
                try: target_writer.close()
                except Exception: pass

        async def target_to_client():
            try:
                while True:
                    data = await target_reader.read(32768)
                    if not data:
                        break
                    frame = make_ws_frame(data, opcode=2)
                    writer.write(frame)
                    await writer.drain()
            except Exception:
                pass
            finally:
                try:
                    writer.write(make_ws_frame(b"", opcode=8))
                    await writer.drain()
                except Exception:
                    pass
                try: writer.close()
                except Exception: pass

        await asyncio.gather(client_to_target(), target_to_client(), return_exceptions=True)

    except Exception:
        pass
    finally:
        try: writer.close()
        except Exception: pass

async def main():
    print(f"=== [VPS Browser Cloud Bridge] Starting native tunnel on 0.0.0.0:{PORT} ===")
    server = await asyncio.start_server(handle_client, "0.0.0.0", PORT)
    print(f"=== [VPS Browser Cloud Bridge] Listening on 0.0.0.0:{PORT} (Zero dependencies) ===")
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    asyncio.run(main())
