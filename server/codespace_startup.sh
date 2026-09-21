#!/usr/bin/env bash
set -e

echo "=== [VPS Browser Cloud] Iniciando entorno seguro nativo (Sin sobrecarga de Docker) ==="

# 1. Detener cualquier instancia previa
pkill -f "cloud_bridge.py" 2>/dev/null || true

# 2. Iniciar el Cloud Bridge nativo (100% Python standard library, zero pip/apt dependencies)
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
nohup python3 "$DIR/cloud_bridge.py" 3000 </dev/null >/tmp/cloud_bridge.log 2>&1 &

# 3. Watchdog en segundo plano para garantizar alta disponibilidad
(
    while true; do
        sleep 5
        if ! pgrep -f "cloud_bridge.py" >/dev/null 2>&1; then
            nohup python3 "$DIR/cloud_bridge.py" 3000 </dev/null >>/tmp/cloud_bridge.log 2>&1 &
        fi
    done
) </dev/null >/dev/null 2>&1 &

# 4. Asegurar visibilidad pública de puertos en Dev Tunnels
set_public_ports() {
    if command -v gh >/dev/null 2>&1; then
        if [ -n "$CODESPACE_NAME" ]; then
            gh codespace ports visibility 3000:public -c "$CODESPACE_NAME" 2>/dev/null || true
        else
            gh codespace ports visibility 3000:public 2>/dev/null || true
        fi
    fi
}

set_public_ports

(
    for attempt in {1..5}; do
        sleep 2
        set_public_ports
    done
) >/dev/null 2>&1 &

echo "=== [VPS Browser Cloud] Servidor activo (Nativo + Seguro + 0% Docker) ==="
