#!/usr/bin/env bash

echo "=== [VPS Browser Cloud] Iniciando entorno seguro nativo (Sin sobrecarga de Docker) ==="

# 1. Configurar proxy nativo ultra-rápido en puerto 8080 (sin Docker)
if ! command -v tinyproxy >/dev/null 2>&1; then
    echo "Instalando proxy nativo ultraligero..."
    sudo apt-get update -y && sudo apt-get install -y tinyproxy
fi

if [ -f /etc/tinyproxy/tinyproxy.conf ]; then
    sudo sed -i 's/^Port .*/Port 8080/' /etc/tinyproxy/tinyproxy.conf
    sudo sed -i 's/^Allow /#Allow /' /etc/tinyproxy/tinyproxy.conf
    sudo sed -i 's/^Listen /#Listen /' /etc/tinyproxy/tinyproxy.conf
    sudo service tinyproxy restart 2>/dev/null || sudo systemctl restart tinyproxy 2>/dev/null || tinyproxy -c /etc/tinyproxy/tinyproxy.conf 2>/dev/null || true
fi

# 2. Si Docker está instalado y disponible, mantener Firefox de respaldo en puerto 3000
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    if [ ! "$(docker ps -q -f name=vps-firefox 2>/dev/null)" ]; then
        if [ "$(docker ps -aq -f status=exited -f name=vps-firefox 2>/dev/null)" ]; then
            docker start vps-firefox || true
        fi
    fi
fi

# 3. Asegurar visibilidad pública de puertos en Dev Tunnels
set_public_ports() {
    if command -v gh >/dev/null 2>&1; then
        if [ -n "$CODESPACE_NAME" ]; then
            gh codespace ports visibility 3000:public -c "$CODESPACE_NAME" 2>/dev/null || true
            gh codespace ports visibility 8080:public -c "$CODESPACE_NAME" 2>/dev/null || true
        else
            gh codespace ports visibility 3000:public 2>/dev/null || true
            gh codespace ports visibility 8080:public 2>/dev/null || true
        fi
    fi
}

echo "Configurando visibilidad pública de puertos..."
set_public_ports

(
    for attempt in {1..15}; do
        sleep 3
        set_public_ports
    done
) >/dev/null 2>&1 &

echo "=== [VPS Browser Cloud] Servidor activo (Nativo + Seguro) ==="
