#!/usr/bin/env bash
set -e

echo "=== [VPS Browser Cloud] Iniciando entorno seguro nativo (Sin sobrecarga de Docker) ==="

# 1. Detener Docker si estuviese corriendo para liberar 100% de CPU y RAM
if command -v docker >/dev/null 2>&1; then
    docker stop vps-firefox 2>/dev/null || true
    docker rm vps-firefox 2>/dev/null || true
fi

# 2. Instalar dependencias nativas de Python ultraligeras (toma ~2 segundos)
if ! python3 -c "import websockets" >/dev/null 2>&1; then
    echo "Instalando módulo nativo websockets para bridge cloud..."
    sudo apt-get update -y && sudo apt-get install -y python3-websockets 2>/dev/null || pip3 install --no-cache-dir websockets 2>/dev/null || true
fi

# 3. Iniciar el Cloud Bridge nativo en puerto 3000
echo "Iniciando VPS Browser Cloud Bridge en puerto 3000..."
pkill -f "cloud_bridge.py" 2>/dev/null || true
nohup python3 server/cloud_bridge.py 3000 >/tmp/cloud_bridge.log 2>&1 &

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

echo "Configurando visibilidad pública de puerto 3000 en Dev Tunnels..."
set_public_ports

(
    for attempt in {1..10}; do
        sleep 2
        set_public_ports
    done
) >/dev/null 2>&1 &

echo "=== [VPS Browser Cloud] Servidor activo (Nativo + Seguro + 0% Docker) ==="
