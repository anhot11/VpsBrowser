#!/usr/bin/env bash
set -e

echo "=== [VPS Browser Cloud] Iniciando entorno seguro en GitHub Codespaces ==="

# Esperar a que el daemon de Docker esté disponible
for i in {1..30}; do
    if docker info >/dev/null 2>&1; then
        break
    fi
    echo "Esperando que Docker esté disponible ($i/30)..."
    sleep 1
done

# Iniciar o arrancar contenedor Firefox Desktop en puerto 3000
if [ ! "$(docker ps -q -f name=vps-firefox)" ]; then
    if [ "$(docker ps -aq -f status=exited -f name=vps-firefox)" ]; then
        echo "Reanudando contenedor vps-firefox..."
        docker start vps-firefox
    else
        echo "Descargando e iniciando vps-firefox..."
        docker run -d \
          --name vps-firefox \
          --shm-size="2gb" \
          -p 3000:3000 \
          -e PUID=1000 \
          -e PGID=1000 \
          -e TZ=Etc/UTC \
          --restart unless-stopped \
          lscr.io/linuxserver/firefox:latest
    fi
else
    echo "vps-firefox ya está en ejecución."
fi

# Iniciar proxy HTTP/SOCKS ligero en puerto 8080 para navegación móvil
if [ ! "$(docker ps -q -f name=vps-proxy)" ]; then
    if [ "$(docker ps -aq -f status=exited -f name=vps-proxy)" ]; then
        docker start vps-proxy || true
    else
        docker run -d \
          --name vps-proxy \
          -p 8080:8888 \
          --restart unless-stopped \
          monstrenyatko/tinyproxy:latest || true
    fi
fi

# Función para asegurar visibilidad pública de puertos 3000 y 8080 en GitHub Dev Tunnels
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

# Lanzar vigilancia en segundo plano para reasegurar visibilidad pública mientras Dev Tunnels vincula los puertos
(
    for attempt in {1..20}; do
        sleep 3
        set_public_ports
    done
) >/dev/null 2>&1 &

echo "=== [VPS Browser Cloud] Servidor activo en puerto 3000 y 8080 (Público) ==="

