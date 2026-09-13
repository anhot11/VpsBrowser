#!/usr/bin/env bash
set -e

# Colores para la salida
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}   🚀 INSTALADOR UNIVERSAL DE VPS BROWSER (FIREFOX)   ${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"

# 1. Comprobar privilegios de root
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}[ERROR] Este script debe ejecutarse como root.${NC}"
  echo -e "Por favor, ejecuta: ${YELLOW}sudo bash $0${NC} o ${YELLOW}curl ... | sudo bash${NC}"
  exit 1
fi

# 2. Detección de Sistema Operativo y Arquitectura
echo -e "\n${BLUE}[1/7] Detectando sistema operativo y arquitectura...${NC}"
ARCH=$(uname -m)
if [ -f /etc/os-release ]; then
    . /etc/os-release
    DISTRO=$NAME
    DISTRO_ID=$ID
    VERSION=$VERSION_ID
else
    DISTRO=$(uname -s)
    DISTRO_ID="unknown"
    VERSION="unknown"
fi

echo -e "  • ${BOLD}Distribución:${NC} $DISTRO ($VERSION)"
echo -e "  • ${BOLD}Arquitectura:${NC} $ARCH"

case "$ARCH" in
    x86_64|amd64)
        echo -e "  • ${GREEN}Arquitectura compatible: x86_64 (Intel/AMD)${NC}"
        ;;
    aarch64|arm64)
        echo -e "  • ${GREEN}Arquitectura compatible: ARM64 (Oracle Cloud Ampere / AWS Graviton / Hetzner ARM)${NC}"
        ;;
    *)
        echo -e "  • ${YELLOW}Arquitectura $ARCH detectada. Las imágenes de LinuxServer.io son multi-arquitectura.${NC}"
        ;;
esac

# 3. Instalación de herramientas base según el gestor de paquetes
echo -e "\n${BLUE}[2/7] Comprobando e instalando utilidades necesarias...${NC}"
install_pkg() {
    if command -v apt-get &>/dev/null; then
        apt-get update -y >/dev/null 2>&1
        apt-get install -y curl wget openssl ca-certificates >/dev/null 2>&1
    elif command -v dnf &>/dev/null; then
        dnf install -y curl wget openssl ca-certificates >/dev/null 2>&1
    elif command -v yum &>/dev/null; then
        yum install -y curl wget openssl ca-certificates >/dev/null 2>&1
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm curl wget openssl ca-certificates >/dev/null 2>&1
    elif command -v apk &>/dev/null; then
        apk add --no-cache curl wget openssl ca-certificates bash >/dev/null 2>&1
    elif command -v zypper &>/dev/null; then
        zypper install -y curl wget openssl ca-certificates >/dev/null 2>&1
    fi
}
install_pkg
echo -e "  • ${GREEN}✓ Herramientas base listas (curl, wget, openssl).${NC}"

# 4. Detección y optimización de memoria RAM y SWAP (crucial para VPS con 512MB / 1GB)
echo -e "\n${BLUE}[3/7] Verificando memoria RAM y memoria SWAP...${NC}"
TOTAL_RAM_MB=$(free -m | awk '/^Mem:/{print $2}')
TOTAL_SWAP_MB=$(free -m | awk '/^Swap:/{print $2}')

echo -e "  • Memoria RAM física: ${BOLD}${TOTAL_RAM_MB} MB${NC}"
echo -e "  • Memoria SWAP actual: ${BOLD}${TOTAL_SWAP_MB} MB${NC}"

if [ "$TOTAL_RAM_MB" -lt 2048 ] && [ "$TOTAL_SWAP_MB" -lt 1024 ]; then
    echo -e "  • ${YELLOW}Tu VPS tiene menos de 2GB de RAM. Creando 2GB de SWAP para garantizar fluidez...${NC}"
    if [ ! -f /swapfile ]; then
        fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
        chmod 600 /swapfile
        mkswap /swapfile >/dev/null
        swapon /swapfile
        if ! grep -q "/swapfile" /etc/fstab; then
            echo '/swapfile none swap sw 0 0' >> /etc/fstab
        fi
        echo -e "  • ${GREEN}✓ SWAP de 2GB activada correctamente.${NC}"
    else
        swapon /swapfile 2>/dev/null || true
    fi
else
    echo -e "  • ${GREEN}✓ Recursos de memoria adecuados.${NC}"
fi

# 5. Instalación y comprobación de Docker y Docker Compose
echo -e "\n${BLUE}[4/7] Comprobando entorno Docker...${NC}"
if ! command -v docker &>/dev/null; then
    echo -e "  • Docker no encontrado. Instalando Docker automáticamente..."
    curl -fsSL https://get.docker.com | sh
fi

# Habilitar e iniciar servicio Docker
if command -v systemctl &>/dev/null; then
    systemctl enable --now docker >/dev/null 2>&1 || true
elif command -v service &>/dev/null; then
    service docker start >/dev/null 2>&1 || true
fi
echo -e "  • ${GREEN}✓ Docker instalado y en ejecución ($(docker --version 2>/dev/null | awk '{print $3}' | tr -d ',')).${NC}"

# Comprobar Docker Compose Plugin
if ! docker compose version &>/dev/null; then
    echo -e "  • Instalando Docker Compose Plugin..."
    if command -v apt-get &>/dev/null; then
        apt-get install -y docker-compose-plugin >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y docker-compose-plugin >/dev/null 2>&1 || true
    elif command -v yum &>/dev/null; then
        yum install -y docker-compose-plugin >/dev/null 2>&1 || true
    fi

    # Si sigue sin estar disponible, descarga el binario oficial
    if ! docker compose version &>/dev/null; then
        mkdir -p /usr/local/lib/docker/cli-plugins
        COMPOSE_VERSION="v2.29.2"
        case "$ARCH" in
            x86_64|amd64) DOCKER_COMPOSE_ARCH="x86_64" ;;
            aarch64|arm64) DOCKER_COMPOSE_ARCH="aarch64" ;;
            *) DOCKER_COMPOSE_ARCH="x86_64" ;;
        esac
        curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${DOCKER_COMPOSE_ARCH}" -o /usr/local/lib/docker/cli-plugins/docker-compose
        chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    fi
fi
echo -e "  • ${GREEN}✓ Docker Compose listo ($(docker compose version 2>/dev/null | awk '{print $4}')).${NC}"

# 6. Apertura de puertos en Firewall (UFW / Firewalld / iptables)
echo -e "\n${BLUE}[5/7] Configurando cortafuegos (Firewall) para puertos 3000 y 3001...${NC}"
if command -v ufw &>/dev/null && ufw status | grep -qw "active"; then
    ufw allow 3000/tcp comment 'VPS Browser HTTP' >/dev/null 2>&1
    ufw allow 3001/tcp comment 'VPS Browser HTTPS' >/dev/null 2>&1
    echo -e "  • ${GREEN}✓ Puertos 3000 y 3001 abiertos en UFW.${NC}"
elif command -v firewall-cmd &>/dev/null && systemctl is-active --quiet firewalld; then
    firewall-cmd --permanent --add-port=3000/tcp >/dev/null 2>&1
    firewall-cmd --permanent --add-port=3001/tcp >/dev/null 2>&1
    firewall-cmd --reload >/dev/null 2>&1
    echo -e "  • ${GREEN}✓ Puertos 3000 y 3001 abiertos en Firewalld.${NC}"
elif command -v iptables &>/dev/null; then
    iptables -I INPUT -p tcp --dport 3000 -j ACCEPT >/dev/null 2>&1 || true
    iptables -I INPUT -p tcp --dport 3001 -j ACCEPT >/dev/null 2>&1 || true
    echo -e "  • ${GREEN}✓ Reglas de iptables añadidas para puertos 3000 y 3001.${NC}"
else
    echo -e "  • ${GREEN}✓ No se detectó cortafuegos activo que bloquee los puertos.${NC}"
fi

# 7. Preparación de carpeta y archivo docker-compose.yml
echo -e "\n${BLUE}[6/7] Configurando directorio y credenciales...${NC}"

# Si el script se ejecuta directamente por curl sin clonar el repo:
DEPLOY_DIR="/opt/vps-browser"
mkdir -p "$DEPLOY_DIR/config"
cd "$DEPLOY_DIR"

PASS=$(openssl rand -hex 8)

cat <<EOF > docker-compose.yml
version: '3.8'

services:
  vps-firefox:
    image: lscr.io/linuxserver/firefox:latest
    container_name: vps-firefox
    security_opt:
      - seccomp:unconfined
    environment:
      - PUID=1000
      - PGID=1000
      - TZ=Etc/UTC
      - CUSTOM_USER=admin
      - PASSWORD=$PASS
    volumes:
      - ./config:/config
    ports:
      - "3000:3000"
      - "3001:3001"
    shm_size: "2gb"
    restart: unless-stopped
EOF

echo -e "  • ${GREEN}✓ Configuración generada en $DEPLOY_DIR/docker-compose.yml${NC}"

# 8. Despliegue del Contenedor
echo -e "\n${BLUE}[7/7] Descargando imagen y levantando el contenedor Firefox...${NC}"
docker compose pull
docker compose up -d

# Detección de IP Pública
PUBLIC_IP=$(curl -s --max-time 3 https://api.ipify.org || curl -s --max-time 3 https://icanhazip.com || hostname -I | awk '{print $1}')

echo ""
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "${GREEN}${BOLD}    🎉 ¡VPS BROWSER FIREFOX INSTALADO CON ÉXITO!      ${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "Usa estos datos de conexión en la aplicación Android:"
echo ""
echo -e "  • ${BOLD}IP / Host:${NC}          ${YELLOW}$PUBLIC_IP${NC}"
echo -e "  • ${BOLD}Puerto HTTP:${NC}        ${YELLOW}3000${NC}"
echo -e "  • ${BOLD}Puerto HTTPS:${NC}       ${YELLOW}3001${NC}"
echo -e "  • ${BOLD}Usuario:${NC}            ${YELLOW}admin${NC}"
echo -e "  • ${BOLD}Contraseña / Token:${NC} ${YELLOW}$PASS${NC}"
echo ""
echo -e "  • ${BOLD}Enlace Web Directo:${NC} ${BLUE}http://$PUBLIC_IP:3000${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "Para reiniciar o ver logs en cualquier momento:"
echo -e "  • Ver estado:  ${BOLD}cd $DEPLOY_DIR && docker compose ps${NC}"
echo -e "  • Ver logs:    ${BOLD}cd $DEPLOY_DIR && docker compose logs -f${NC}"
echo -e "  • Detener:     ${BOLD}cd $DEPLOY_DIR && docker compose down${NC}"
echo -e "  • Iniciar:     ${BOLD}cd $DEPLOY_DIR && docker compose up -d${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}\n"
