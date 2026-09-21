#!/usr/bin/env python3
import asyncio
import http
import os
import sys
import urllib.parse

# Ensure websockets is available
try:
    import websockets
except ImportError:
    import subprocess
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--no-cache-dir", "websockets"])
        import websockets
    except Exception as e:
        print(f"Error importing or installing websockets: {e}", file=sys.stderr)
        sys.exit(1)

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000

async def process_request(connection_or_path, headers_or_request=None):
    """
    Handles HTTP GET health check requests to confirm the server is alive immediately (HTTP 200 OK).
    Compatible with both websockets v10 (path, headers) and websockets v13+ (connection, request).
    """
    try:
        headers = headers_or_request.headers if hasattr(headers_or_request, 'headers') else (headers_or_request or {})
        upgrade = headers.get("Upgrade", "") if hasattr(headers, "get") else ""
        if upgrade.lower() != "websocket":
            body = b"<!DOCTYPE html><html><body><h1>VPS Browser Cloud Bridge Active</h1></body></html>"
            if hasattr(connection_or_path, 'respond'):
                return connection_or_path.respond(http.HTTPStatus.OK, body, headers=[("Content-Type", "text/html"), ("Content-Length", str(len(body)))])
            else:
                return (http.HTTPStatus.OK, [("Content-Type", "text/html"), ("Content-Length", str(len(body)))], body)
    except Exception:
        pass
    return None

async def handle_ws(websocket, path=None):
    """
    Bridges binary WebSocket stream with target TCP host:port from within the Cloud VM.
    Guarantees 100% egress from the Cloud VM IP.
    """
    try:
        target_path = path or getattr(websocket, 'path', '') or getattr(getattr(websocket, 'request', None), 'path', '')
        parsed = urllib.parse.urlparse(target_path)
        params = urllib.parse.parse_qs(parsed.query)
        host = params.get('host', [''])[0]
        port_str = params.get('port', [''])[0]

        if not host or not port_str:
            await websocket.close(1008, "Missing host or port")
            return

        port = int(port_str)
        try:
            reader, writer = await asyncio.wait_for(asyncio.open_connection(host, port), timeout=12.0)
        except Exception as e:
            await websocket.close(1011, f"Connect error to {host}:{port}: {e}")
            return

        async def ws_to_tcp():
            try:
                async for message in websocket:
                    if isinstance(message, bytes):
                        writer.write(message)
                        await writer.drain()
                    elif isinstance(message, str):
                        writer.write(message.encode('utf-8'))
                        await writer.drain()
            except Exception:
                pass
            finally:
                try:
                    writer.close()
                    await writer.wait_closed()
                except Exception:
                    pass

        async def tcp_to_ws():
            try:
                while True:
                    data = await reader.read(32768)
                    if not data:
                        break
                    await websocket.send(data)
            except Exception:
                pass
            finally:
                try:
                    await websocket.close()
                except Exception:
                    pass

        await asyncio.gather(ws_to_tcp(), tcp_to_ws(), return_exceptions=True)
    except Exception:
        pass

async def main():
    print(f"=== [VPS Browser Cloud Bridge] Starting native tunnel on port {PORT} ===")
    server = await websockets.serve(
        handle_ws,
        "0.0.0.0",
        PORT,
        process_request=process_request,
        max_size=10 * 1024 * 1024
    )
    print(f"=== [VPS Browser Cloud Bridge] Listening on 0.0.0.0:{PORT} ===")
    await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
