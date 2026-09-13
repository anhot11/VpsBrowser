#!/bin/bash
set -e

echo "=================================================="
echo "      Despliegue de Servidor VPS Browser         "
echo "=================================================="

# Check root
if [ "$EUID" -ne 0 ]; then
  echo "Por favor ejecuta este script como root (sudo ./deploy.sh)"
  exit 1
fi

# Detect public IP
PUBLIC_IP=$(curl -s https://api.ipify.org || hostname -I | awk '{print $1}')

echo "[1/4] Comprobando Docker..."
if ! command -v docker &> /dev/null; then
    echo "Docker no está instalado. Instalando Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
fi

echo "[2/4] Comprobando Docker Compose..."
if ! docker compose version &> /dev/null; then
    echo "Instalando plugin docker compose..."
    apt-get update && apt-get install -y docker-compose-plugin || yum install -y docker-compose-plugin
fi

echo "[3/4] Configurando contraseñas y puertos..."
PASS=$(openssl rand -hex 8)

sed -i "s/PASSWORD=ChangeMeSecure123!/PASSWORD=$PASS/g" docker-compose.yml 2>/dev/null || true

echo "[4/4] Levantando contenedor Chromium VPS..."
docker compose pull
docker compose up -d

echo ""
echo "=================================================="
echo "    VPS BROWSER DESPLEGADO CON ÉXITO!            "
echo "=================================================="
echo "Introduce estos datos en la aplicación Android:"
echo ""
echo "  • Dirección Host / IP: $PUBLIC_IP"
echo "  • Puerto HTTP:        3000"
echo "  • Puerto HTTPS:       3001"
echo "  • Usuario:            admin"
echo "  • Contraseña:         $PASS"
echo ""
echo "URL de acceso directo desde navegador: http://$PUBLIC_IP:3000"
echo "=================================================="
