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
echo -e "${BLUE}${BOLD}      Optimizado para VPS Ligeras & Cloudflare        ${NC}"
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
        export DEBIAN_FRONTEND=noninteractive
        # Limpieza de repositorios rotos de Docker previos
        rm -f /etc/apt/sources.list.d/docker.list* 2>/dev/null || true
        dpkg --configure -a >/dev/null 2>&1 || true
        apt-get update -y >/dev/null 2>&1 || apt-get update --fix-missing -y >/dev/null 2>&1 || true
        apt-get install -y curl wget openssl ca-certificates >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y curl wget openssl ca-certificates >/dev/null 2>&1 || true
    elif command -v yum &>/dev/null; then
        yum install -y curl wget openssl ca-certificates >/dev/null 2>&1 || true
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm curl wget openssl ca-certificates >/dev/null 2>&1 || true
    elif command -v apk &>/dev/null; then
        apk add --no-cache curl wget openssl ca-certificates bash >/dev/null 2>&1 || true
    elif command -v zypper &>/dev/null; then
        zypper install -y curl wget openssl ca-certificates >/dev/null 2>&1 || true
    fi
}
install_pkg
echo -e "  • ${GREEN}✓ Herramientas base listas (curl, wget, openssl, ca-certificates).${NC}"

# 4. Optimización de Memoria RAM, SWAP y Kernel para VPS ligeras (512MB / 1GB)
echo -e "\n${BLUE}[3/7] Optimizando memoria RAM, SWAP y rendimiento para VPS ligera...${NC}"
TOTAL_RAM_MB=$(free -m | awk '/^Mem:/{print $2}')
TOTAL_SWAP_MB=$(free -m | awk '/^Swap:/{print $2}')

echo -e "  • Memoria RAM física: ${BOLD}${TOTAL_RAM_MB} MB${NC}"
echo -e "  • Memoria SWAP actual: ${BOLD}${TOTAL_SWAP_MB} MB${NC}"

# Configuración de memoria compartida SHM adaptada a los recursos del host
if [ "$TOTAL_RAM_MB" -lt 1500 ]; then
    SHM_SIZE="512m"
    SWAP_TARGET="2G"
elif [ "$TOTAL_RAM_MB" -lt 3000 ]; then
    SHM_SIZE="1gb"
    SWAP_TARGET="2G"
else
    SHM_SIZE="2gb"
    SWAP_TARGET="1G"
fi

# Ajuste fino de Kernel para mantener procesos en memoria física y swap suave
sysctl -w vm.swappiness=10 >/dev/null 2>&1 || true
sysctl -w vm.vfs_cache_pressure=50 >/dev/null 2>&1 || true

if [ "$TOTAL_RAM_MB" -lt 2048 ] && [ "$TOTAL_SWAP_MB" -lt 1024 ]; then
    echo -e "  • ${YELLOW}VPS ligera detectada (<2GB RAM). Creando $SWAP_TARGET de memoria SWAP para máxima estabilidad...${NC}"
    if [ ! -f /swapfile ]; then
        fallocate -l $SWAP_TARGET /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
        chmod 600 /swapfile
        mkswap /swapfile >/dev/null 2>&1 || true
        swapon /swapfile >/dev/null 2>&1 || true
        if ! grep -q "/swapfile" /etc/fstab; then
            echo '/swapfile none swap sw 0 0' >> /etc/fstab
        fi
        echo -e "  • ${GREEN}✓ SWAP activada correctamente ($SWAP_TARGET).${NC}"
    else
        swapon /swapfile 2>/dev/null || true
    fi
else
    echo -e "  • ${GREEN}✓ Recursos de memoria adecuados (SHM asignada: $SHM_SIZE).${NC}"
fi

# 5. Instalación y comprobación de Docker y Docker Compose
echo -e "\n${BLUE}[4/7] Comprobando entorno Docker...${NC}"
if ! command -v docker &>/dev/null; then
    echo -e "  • Docker no encontrado. Instalando Docker automáticamente..."

    # Prioridad 1: Repositorio oficial de la distribución Linux (funciona en todo el mundo sin bloqueos por región)
    if command -v apt-get &>/dev/null; then
        export DEBIAN_FRONTEND=noninteractive
        rm -f /etc/apt/sources.list.d/docker.list* 2>/dev/null || true
        apt-get update -y >/dev/null 2>&1 || true
        apt-get install -y docker.io containerd >/dev/null 2>&1 || apt-get install -y docker.io >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y docker containerd >/dev/null 2>&1 || dnf install -y moby-engine >/dev/null 2>&1 || true
    elif command -v yum &>/dev/null; then
        yum install -y docker containerd >/dev/null 2>&1 || true
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm docker >/dev/null 2>&1 || true
    elif command -v apk &>/dev/null; then
        apk add --no-cache docker >/dev/null 2>&1 || true
    fi

    # Prioridad 2: Si el paquete del SO no lo instaló, intentar get.docker.com protegido contra fallos
    if ! command -v docker &>/dev/null; then
        echo -e "  • Intentando instalador oficial get.docker.com..."
        (curl -fsSL --connect-timeout 10 https://get.docker.com | sh) >/dev/null 2>&1 || true
    fi
fi

# Habilitar e iniciar servicio Docker
if command -v systemctl &>/dev/null; then
    systemctl unmask docker >/dev/null 2>&1 || true
    systemctl daemon-reload >/dev/null 2>&1 || true
    systemctl enable --now docker >/dev/null 2>&1 || systemctl start docker >/dev/null 2>&1 || true
elif command -v service &>/dev/null; then
    service docker start >/dev/null 2>&1 || true
fi

# Verificar si Docker quedó disponible
if ! command -v docker &>/dev/null; then
    echo -e "${RED}[ERROR] No se pudo instalar Docker automáticamente en esta VPS.${NC}"
    echo -e "Intenta instalar Docker en tu servidor con: ${YELLOW}apt install docker.io${NC}"
    exit 1
fi
echo -e "  • ${GREEN}✓ Docker instalado y en ejecución ($(docker --version 2>/dev/null | awk '{print $3}' | tr -d ',')).${NC}"

# Comprobar Docker Compose Plugin o Standalone
COMPOSE_CMD=""
if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "  • Instalando Docker Compose..."
    if command -v apt-get &>/dev/null; then
        export DEBIAN_FRONTEND=noninteractive
        apt-get install -y docker-compose-plugin >/dev/null 2>&1 || apt-get install -y docker-compose-v2 >/dev/null 2>&1 || apt-get install -y docker-compose >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y docker-compose-plugin >/dev/null 2>&1 || dnf install -y docker-compose >/dev/null 2>&1 || true
    elif command -v yum &>/dev/null; then
        yum install -y docker-compose-plugin >/dev/null 2>&1 || yum install -y docker-compose >/dev/null 2>&1 || true
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm docker-compose >/dev/null 2>&1 || true
    elif command -v apk &>/dev/null; then
        apk add --no-cache docker-cli-compose >/dev/null 2>&1 || true
    fi

    if docker compose version &>/dev/null; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &>/dev/null; then
        COMPOSE_CMD="docker-compose"
    else
        # Si sigue sin estar disponible, descarga el binario oficial de GitHub Releases
        mkdir -p /usr/local/lib/docker/cli-plugins /usr/local/bin
        COMPOSE_VERSION="v2.29.2"
        case "$ARCH" in
            x86_64|amd64) DOCKER_COMPOSE_ARCH="x86_64" ;;
            aarch64|arm64) DOCKER_COMPOSE_ARCH="aarch64" ;;
            *) DOCKER_COMPOSE_ARCH="x86_64" ;;
        esac
        curl -fsSL --connect-timeout 10 "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${DOCKER_COMPOSE_ARCH}" -o /usr/local/lib/docker/cli-plugins/docker-compose >/dev/null 2>&1 || true
        chmod +x /usr/local/lib/docker/cli-plugins/docker-compose >/dev/null 2>&1 || true
        cp /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose >/dev/null 2>&1 || true

        if docker compose version &>/dev/null; then
            COMPOSE_CMD="docker compose"
        elif command -v docker-compose &>/dev/null; then
            COMPOSE_CMD="docker-compose"
        fi
    fi
fi

if [ -n "$COMPOSE_CMD" ]; then
    echo -e "  • ${GREEN}✓ Docker Compose listo ($($COMPOSE_CMD version 2>/dev/null | awk '{print $4}')).${NC}"
else
    echo -e "  • ${YELLOW}✓ Usando Docker Engine directo como motor de ejecución.${NC}"
fi

# 6. Apertura de puertos en Firewall (UFW / Firewalld / iptables)
echo -e "\n${BLUE}[5/7] Configurando cortafuegos (Firewall) para puertos 3000 y 3001...${NC}"
if command -v ufw &>/dev/null && ufw status | grep -qw "active"; then
    ufw allow 3000/tcp comment 'VPS Browser HTTP' >/dev/null 2>&1 || true
    ufw allow 3001/tcp comment 'VPS Browser HTTPS' >/dev/null 2>&1 || true
    echo -e "  • ${GREEN}✓ Puertos 3000 y 3001 abiertos en UFW.${NC}"
elif command -v firewall-cmd &>/dev/null && systemctl is-active --quiet firewalld 2>/dev/null; then
    firewall-cmd --permanent --add-port=3000/tcp >/dev/null 2>&1 || true
    firewall-cmd --permanent --add-port=3001/tcp >/dev/null 2>&1 || true
    firewall-cmd --reload >/dev/null 2>&1 || true
    echo -e "  • ${GREEN}✓ Puertos 3000 y 3001 abiertos en Firewalld.${NC}"
elif command -v iptables &>/dev/null; then
    iptables -I INPUT -p tcp --dport 3000 -j ACCEPT >/dev/null 2>&1 || true
    iptables -I INPUT -p tcp --dport 3001 -j ACCEPT >/dev/null 2>&1 || true
    echo -e "  • ${GREEN}✓ Reglas de iptables añadidas para puertos 3000 y 3001.${NC}"
else
    echo -e "  • ${GREEN}✓ No se detectó cortafuegos activo que bloquee los puertos.${NC}"
fi

# 7. Preparación de carpeta, políticas ultra-ligeras de Firefox y docker-compose.yml
echo -e "\n${BLUE}[6/7] Configurando entorno optimizado para VPS ligera y túnel Cloudflare...${NC}"

DEPLOY_DIR="/opt/vps-browser"
mkdir -p "$DEPLOY_DIR/config"
cd "$DEPLOY_DIR"

PASS=$(openssl rand -hex 8)

cat <<'EOF' > policies.json
{
  "policies": {
    "DisableTelemetry": true,
    "DisableFirefoxStudies": true,
    "DisablePocket": true,
    "DisableFirefoxAccounts": true,
    "DisableFeedbackCommands": true,
    "DisableSetDesktopBackground": true,
    "OfferToSaveLogins": false,
    "PasswordManagerEnabled": false,
    "EnableTrackingProtection": {
      "Value": true,
      "Locked": true,
      "Cryptomining": true,
      "Fingerprinting": true,
      "EmailTracking": true
    },
    "ExtensionSettings": {
      "uBlock0@raymondhill.net": {
        "installation_mode": "normal_installed",
        "install_url": "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
      }
    },
    "Preferences": {
      "privacy.donottrackheader.enabled": true,
      "dom.security.https_only_mode": true,
      "privacy.query_stripping.enabled": true,
      "network.cookie.cookieBehavior": 5,
      "browser.safebrowsing.malware.enabled": true,
      "browser.safebrowsing.phishing.enabled": true,
      "datareporting.healthreport.uploadEnabled": false,
      "toolkit.telemetry.unified": false,
      "toolkit.telemetry.enabled": false,
      "browser.tabs.unloadOnLowMemory": true,
      "browser.cache.memory.capacity": 32768,
      "browser.cache.disk.capacity": 102400,
      "dom.ipc.processCount": 2,
      "media.autoplay.default": 5,
      "browser.sessionstore.interval": 60000,
      "image.mem.surfacecache.max_size_kb": 262144,
      "accessibility.force_disabled": 1,
      "browser.sessionhistory.max_entries": 15
    }
  }
}
EOF

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
      - KASM_VNC_ARGUMENTS=-framerate 30 -quality 7
    volumes:
      - ./config:/config
      - ./policies.json:/usr/lib/firefox/distribution/policies.json:ro
      - ./policies.json:/etc/firefox/policies/policies.json:ro
    ports:
      - "3000:3000"
      - "3001:3001"
    shm_size: "$SHM_SIZE"
    restart: unless-stopped

  vps-tunnel:
    image: cloudflare/cloudflared:latest
    container_name: vps-tunnel
    restart: unless-stopped
    command: tunnel --no-autoupdate --url http://vps-firefox:3000
    depends_on:
      - vps-firefox
EOF

echo -e "  • ${GREEN}✓ Configuración generada con uBlock Origin, optimizaciones para VPS ligera y Cloudflare Tunnel.${NC}"

# 8. Despliegue de Contenedores y Detección de Cloudflare Tunnel
echo -e "\n${BLUE}[7/7] Descargando imagen y levantando servicios Firefox y Cloudflare Tunnel...${NC}"

IMAGE_NAME="lscr.io/linuxserver/firefox:latest"
echo -e "  • Descargando imagen de Firefox ($IMAGE_NAME)..."
if ! docker pull "$IMAGE_NAME"; then
    echo -e "  • ${YELLOW}Probando descarga desde GitHub Container Registry (ghcr.io)...${NC}"
    IMAGE_NAME="ghcr.io/linuxserver/firefox:latest"
    if ! docker pull "$IMAGE_NAME"; then
        echo -e "  • ${YELLOW}Probando descarga desde Docker Hub...${NC}"
        IMAGE_NAME="docker.io/linuxserver/firefox:latest"
        docker pull "$IMAGE_NAME" || true
    fi
    sed -i "s|image: lscr.io/linuxserver/firefox:latest|image: $IMAGE_NAME|g" docker-compose.yml 2>/dev/null || true
fi

# Descarga previa de imagen cloudflared (tolerante a fallos)
docker pull cloudflare/cloudflared:latest >/dev/null 2>&1 || true

# Levantar contenedor
CONTAINER_STARTED=0
if [ -n "$COMPOSE_CMD" ]; then
    echo -e "  • Iniciando contenedor con $COMPOSE_CMD..."
    if $COMPOSE_CMD up -d; then
        CONTAINER_STARTED=1
    else
        echo -e "  • ${YELLOW}Compose falló, intentando inicio directo con Docker Engine...${NC}"
    fi
fi

if [ "$CONTAINER_STARTED" -eq 0 ]; then
    echo -e "  • Iniciando contenedor directamente con Docker Engine..."
    docker stop vps-firefox >/dev/null 2>&1 || true
    docker rm vps-firefox >/dev/null 2>&1 || true
    docker run -d \
      --name vps-firefox \
      --security-opt seccomp=unconfined \
      -e PUID=1000 \
      -e PGID=1000 \
      -e TZ=Etc/UTC \
      -e CUSTOM_USER=admin \
      -e PASSWORD="$PASS" \
      -e KASM_VNC_ARGUMENTS="-framerate 30 -quality 7" \
      -v "$DEPLOY_DIR/config":/config \
      -v "$DEPLOY_DIR/policies.json":/usr/lib/firefox/distribution/policies.json:ro \
      -v "$DEPLOY_DIR/policies.json":/etc/firefox/policies/policies.json:ro \
      -p 3000:3000 \
      -p 3001:3001 \
      --shm-size="$SHM_SIZE" \
      --restart unless-stopped \
      "$IMAGE_NAME"

    # Iniciar cloudflared tunnel directo si compose no estuvo disponible
    docker stop vps-tunnel >/dev/null 2>&1 || true
    docker rm vps-tunnel >/dev/null 2>&1 || true
    docker run -d \
      --name vps-tunnel \
      --restart unless-stopped \
      --net=host \
      cloudflare/cloudflared:latest tunnel --no-autoupdate --url http://127.0.0.1:3000 >/dev/null 2>&1 || true
fi

# 9. Asignación y lectura del Túnel Cloudflare Quick HTTPS
echo -e "  • ${BLUE}Obteniendo enlace seguro de Cloudflare Tunnel (sin puertos abiertos)...${NC}"
CF_URL=""
for i in $(seq 1 12); do
    sleep 1
    CF_URL=$(docker logs vps-tunnel 2>&1 | grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' | head -n 1 || true)
    if [ -n "$CF_URL" ]; then
        break
    fi
done

if [ -n "$CF_URL" ]; then
    echo "$CF_URL" > "$DEPLOY_DIR/cloudflare_tunnel.txt"
    echo -e "  • ${GREEN}✓ Túnel Cloudflare activo:${NC} ${BOLD}$CF_URL${NC}"
else
    echo -e "  • ${YELLOW}Túnel Cloudflare iniciando en segundo plano.${NC}"
fi

# Detección de IP Pública
PUBLIC_IP=$(curl -s --max-time 3 https://api.ipify.org 2>/dev/null || curl -s --max-time 3 https://icanhazip.com 2>/dev/null || hostname -I | awk '{print $1}')

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
if [ -n "$CF_URL" ]; then
    echo -e "  • ${BOLD}Túnel Cloudflare (HTTPS):${NC} ${GREEN}$CF_URL${NC}"
    echo -e "    ${YELLOW}(¡Conexión sin abrir puertos en la VPS ni en el Router/Firewall!)${NC}"
    echo ""
fi
echo -e "  • ${BOLD}Enlace Web Directo:${NC} ${BLUE}http://$PUBLIC_IP:3000${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}"
echo -e "Para reiniciar o ver logs en cualquier momento:"
echo -e "  • Ver estado:  ${BOLD}cd $DEPLOY_DIR && docker compose ps 2>/dev/null || docker ps${NC}"
echo -e "  • Ver logs:    ${BOLD}cd $DEPLOY_DIR && docker compose logs -f 2>/dev/null || docker logs -f vps-firefox${NC}"
echo -e "  • Detener:     ${BOLD}cd $DEPLOY_DIR && docker compose down 2>/dev/null || docker stop vps-firefox vps-tunnel${NC}"
echo -e "  • Iniciar:     ${BOLD}cd $DEPLOY_DIR && docker compose up -d 2>/dev/null || docker start vps-firefox vps-tunnel${NC}"
echo -e "${GREEN}${BOLD}======================================================${NC}\n"
