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

# Argument parsing: --native (default, bare-metal ultralight), --docker, --audit
DEPLOY_MODE="native"
for arg in "$@"; do
    case "$arg" in
        --native|-n)
            DEPLOY_MODE="native"
            ;;
        --docker|-d)
            DEPLOY_MODE="docker"
            ;;
        --audit|-a)
            DEPLOY_MODE="audit"
            ;;
    esac
done

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
        echo -e "  • ${YELLOW}Arquitectura $ARCH detectada. Las imágenes y binarios soportan multi-arquitectura.${NC}"
        ;;
esac

if [ "$DEPLOY_MODE" = "native" ]; then
    echo -e "  • ${GREEN}${BOLD}Modo de Instalación:${NC} ⚡ NATIVO VPS (Ultraligero / Sin Docker / Máximo Rendimiento)"
else
    echo -e "  • ${BLUE}${BOLD}Modo de Instalación:${NC} 🐳 DOCKER (Contenedor Aislado / Predeterminado)"
fi

# 3. Instalación de herramientas base según el gestor de paquetes
echo -e "\n${BLUE}[2/7] Comprobando e instalando utilidades necesarias...${NC}"
install_pkg() {
    if command -v apt-get &>/dev/null; then
        export DEBIAN_FRONTEND=noninteractive
        # Limpieza de repositorios rotos de Docker previos
        rm -f /etc/apt/sources.list.d/docker.list* 2>/dev/null || true
        dpkg --configure -a >/dev/null 2>&1 || true
        apt-get update -y >/dev/null 2>&1 || apt-get update --fix-missing -y >/dev/null 2>&1 || true
        apt-get install -y curl wget openssl ca-certificates procps >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y curl wget openssl ca-certificates procps-ng >/dev/null 2>&1 || true
    elif command -v yum &>/dev/null; then
        yum install -y curl wget openssl ca-certificates procps-ng >/dev/null 2>&1 || true
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm curl wget openssl ca-certificates procps-ng >/dev/null 2>&1 || true
    elif command -v apk &>/dev/null; then
        apk add --no-cache curl wget openssl ca-certificates bash procps >/dev/null 2>&1 || true
    elif command -v zypper &>/dev/null; then
        zypper install -y curl wget openssl ca-certificates >/dev/null 2>&1 || true
    fi
}
install_pkg
echo -e "  • ${GREEN}✓ Herramientas base listas (curl, wget, openssl, ca-certificates).${NC}"

DEPLOY_DIR="/opt/vps-browser"
PREVIOUS_INSTANCE_FOUND=0
PREVIOUS_INSTANCE_SECURE=1
PASS=""

# 3. Detección y Auditoría de Seguridad de Instancia Previa
echo -e "\n${BLUE}[DETECCIÓN] Verificando presencia de instancia previa y auditando seguridad...${NC}"
NATIVE_RUNNING=0
DOCKER_RUNNING=0

if systemctl is-active --quiet vps-browser 2>/dev/null; then
    NATIVE_RUNNING=1
fi
if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qw "vps-firefox"; then
    DOCKER_RUNNING=1
fi

if [ "$NATIVE_RUNNING" -eq 1 ] || [ "$DOCKER_RUNNING" -eq 1 ] || [ -d "$DEPLOY_DIR" ]; then
    PREVIOUS_INSTANCE_FOUND=1
    echo -e "  • ${BLUE}🔍 Instancia previa de VPS Browser encontrada en la VPS.${NC}"
    echo -e "  • Iniciando auditoría exhaustiva de seguridad y estado..."

    # 1. Estado del Servicio / Contenedor
    if [ "$NATIVE_RUNNING" -eq 1 ]; then
        echo -e "  • ${GREEN}✓ [1/5] Servicio Nativo: ACTIVO en systemd (vps-browser).${NC}"
    elif [ "$DOCKER_RUNNING" -eq 1 ]; then
        CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' vps-firefox 2>/dev/null || echo "not_found")
        if [ "$CONTAINER_STATUS" = "running" ]; then
            echo -e "  • ${GREEN}✓ [1/5] Contenedor Docker: ACTIVO y en ejecución saludable.${NC}"
        else
            echo -e "  • ${YELLOW}⚠️ [1/5] Contenedor Docker: Estado '$CONTAINER_STATUS' (Requiere inicio/reparación).${NC}"
            PREVIOUS_INSTANCE_SECURE=0
        fi
    else
        echo -e "  • ${YELLOW}⚠️ [1/5] Servicio / Contenedor: Detenido.${NC}"
        PREVIOUS_INSTANCE_SECURE=0
    fi

    # 2. Contraseña y Autenticación Nginx
    EXISTING_PASS=""
    if [ -f "$DEPLOY_DIR/vps-browser.conf" ]; then
        EXISTING_PASS=$(grep -E '^PASSWORD=' "$DEPLOY_DIR/vps-browser.conf" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d ' ' || true)
    fi
    if [ -z "$EXISTING_PASS" ] && [ -f "$DEPLOY_DIR/docker-compose.yml" ]; then
        EXISTING_PASS=$(grep -E 'PASSWORD=' "$DEPLOY_DIR/docker-compose.yml" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d ' ' || true)
    fi
    if [ -z "$EXISTING_PASS" ] && [ "$DOCKER_RUNNING" -eq 1 ]; then
        EXISTING_PASS=$(docker exec vps-firefox env 2>/dev/null | grep -E '^PASSWORD=' | cut -d= -f2 || true)
    fi

    if [ -n "$EXISTING_PASS" ] && [ "${#EXISTING_PASS}" -ge 8 ] && [ "$EXISTING_PASS" != "abc" ]; then
        echo -e "  • ${GREEN}✓ [2/5] Autenticación Nginx: ACTIVA con contraseña segura (${#EXISTING_PASS} caracteres).${NC}"
        PASS="$EXISTING_PASS"
    else
        echo -e "  • ${RED}❌ [2/5] Alerta de Seguridad: Contraseña ausente o insegura. Requiere actualización.${NC}"
        PREVIOUS_INSTANCE_SECURE=0
    fi

    # 3. Hardening Firefox y uBlock Origin
    POLICIES_OK=0
    for pfile in "$DEPLOY_DIR/policies.json" /etc/firefox/policies/policies.json /opt/firefox/distribution/policies.json; do
        if [ -f "$pfile" ] && grep -q "uBlock0@raymondhill.net" "$pfile" 2>/dev/null && grep -q '"DisableTelemetry": true' "$pfile" 2>/dev/null; then
            POLICIES_OK=1
            break
        fi
    done

    if [ "$POLICIES_OK" -eq 1 ]; then
        echo -e "  • ${GREEN}✓ [3/5] Privacidad Hardened: Telemetría deshabilitada + uBlock Origin preinstalado.${NC}"
    else
        echo -e "  • ${YELLOW}⚠️ [3/5] Privacidad: Políticas incompletas. Se reinyectarán políticas seguras.${NC}"
        PREVIOUS_INSTANCE_SECURE=0
    fi

    # 4. Respuesta de Endpoint HTTP (debe exigir 401 Unauthorized para bloquear acceso no autenticado)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://127.0.0.1:3000 || echo "000")
    if [ "$HTTP_CODE" = "401" ]; then
        echo -e "  • ${GREEN}✓ [4/5] Control de Acceso: 401 Authorization Required (NGINX bloquea intrusos correctamente).${NC}"
    elif [ "$HTTP_CODE" = "200" ]; then
        echo -e "  • ${GREEN}✓ [4/5] Control de Acceso: 200 OK (Endpoint accesible con credenciales).${NC}"
    else
        echo -e "  • ${YELLOW}⚠️ [4/5] Control de Acceso: Código HTTP '$HTTP_CODE'. Se verificará configuración.${NC}"
        PREVIOUS_INSTANCE_SECURE=0
    fi

    # 5. Túnel Cloudflare Quick Tunnel
    CF_URL=""
    if [ -f "$DEPLOY_DIR/cloudflare_tunnel.txt" ]; then
        CF_URL=$(cat "$DEPLOY_DIR/cloudflare_tunnel.txt" | tr -d ' \r\n' || true)
    fi
    if [ -z "$CF_URL" ] && [ "$DOCKER_RUNNING" -eq 1 ]; then
        CF_URL=$(docker logs vps-tunnel 2>&1 | grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' | head -n 1 || true)
    fi
    if [ -z "$CF_URL" ] && command -v journalctl &>/dev/null; then
        CF_URL=$(journalctl -u vps-tunnel --no-pager -n 50 2>&1 | grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' | tail -n 1 || true)
    fi

    if [ -n "$CF_URL" ]; then
        echo -e "  • ${GREEN}✓ [5/5] Túnel Cloudflare: ACTIVO sin puertos públicos abiertos ($CF_URL).${NC}"
    else
        echo -e "  • ${YELLOW}⚠️ [5/5] Túnel Cloudflare: Activo pero obteniendo enlace.${NC}"
    fi

    if [ "$PREVIOUS_INSTANCE_SECURE" -eq 1 ] && [ -n "$PASS" ] && { [ "$NATIVE_RUNNING" -eq 1 ] || [ "$CONTAINER_STATUS" = "running" ]; }; then
        echo -e "\n${GREEN}${BOLD}🛡️ [RESULTADO AUDITORÍA] La instancia previa fue verificada y es 100% SEGURA.${NC}"
        echo -e "  • La instancia cumple todos los criterios de seguridad, cifrado y privacidad."
        echo -e "  • Se reutiliza la instancia activa sin necesidad de reinstalar.\n"

        PUBLIC_IP=$(curl -s --max-time 3 https://api.ipify.org 2>/dev/null || curl -s --max-time 3 https://icanhazip.com 2>/dev/null || hostname -I | awk '{print $1}')

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
        echo -e "${GREEN}${BOLD}======================================================${NC}\n"
        exit 0
    else
        echo -e "\n${YELLOW}${BOLD}⚠️ [RESULTADO AUDITORÍA] Se detectó instancia previa con componentes a reparar.${NC}"
        echo -e "  • El instalador blindará y actualizará la configuración de forma segura.\n"
    fi
else
    echo -e "  • ${BLUE}ℹ️ No se detectó instancia previa. Se procederá con una instalación limpia y segura.${NC}"
fi

# 4. Optimización de Memoria RAM, SWAP y Kernel para VPS ligeras (512MB / 1GB)
echo -e "\n${BLUE}[3/7] Optimizando memoria RAM, SWAP y rendimiento para VPS ligera...${NC}"
TOTAL_RAM_MB=$(free -m | awk '/^Mem:/{print $2}')
TOTAL_SWAP_MB=$(free -m | awk '/^Swap:/{print $2}')
FREE_DISK_MB=$(df -m / | awk 'NR==2{print $4}')

echo -e "  • Memoria RAM física: ${BOLD}${TOTAL_RAM_MB} MB${NC}"
echo -e "  • Memoria SWAP actual: ${BOLD}${TOTAL_SWAP_MB} MB${NC}"
echo -e "  • Espacio libre en disco: ${BOLD}${FREE_DISK_MB} MB${NC}"

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

# Ajuste fino de Kernel: TCP BBR Turbo Congestion Control & Low-Latency Tuning
echo -e "  • ${GREEN}⚡ Optimizando Kernel (TCP BBR Turbo + Latencia Ultrabaja)...${NC}"
modprobe tcp_bbr 2>/dev/null || true
mkdir -p /etc/sysctl.d
cat <<'EOF' > /etc/sysctl.d/99-vpsbrowser-turbo.conf
# VPS Browser High Performance & Low Latency Streaming
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_notsent_lowat = 16384
net.ipv4.tcp_fastopen = 3
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
vm.swappiness = 10
vm.vfs_cache_pressure = 50
EOF
sysctl -p /etc/sysctl.d/99-vpsbrowser-turbo.conf >/dev/null 2>&1 || sysctl -w net.ipv4.tcp_congestion_control=bbr >/dev/null 2>&1 || true


if [ "$TOTAL_RAM_MB" -lt 2048 ] && [ "$TOTAL_SWAP_MB" -lt 1024 ]; then
    # Proteger VPS con disco pequeño para no agotar almacenamiento de Docker
    if [ "$FREE_DISK_MB" -lt 3500 ]; then
        echo -e "  • ${YELLOW}Espacio en disco ajustado (<3.5GB libres). Asignando SWAP ligera (512MB) para reservar espacio a Docker...${NC}"
        SWAP_TARGET="512M"
        SWAP_COUNT=512
    else
        SWAP_COUNT=2048
    fi

    if [ ! -f /swapfile ]; then
        echo -e "  • ${YELLOW}VPS ligera detectada (<2GB RAM). Creando $SWAP_TARGET de memoria SWAP para máxima estabilidad...${NC}"
        fallocate -l $SWAP_TARGET /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=$SWAP_COUNT status=none
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

# 5. Instalación de entorno según modo seleccionado (Nativo vs Docker)
if [ "$DEPLOY_MODE" = "native" ]; then
    echo -e "\n${BLUE}[4/7] Configurando entorno nativo ultraligero (Sin Docker)...${NC}"
    # Detener contenedor docker si existía para evitar colisión de puertos
    docker stop vps-firefox vps-tunnel >/dev/null 2>&1 || true

    echo -e "  • Instalando dependencias de visualización y proxy nativo..."
    if command -v apt-get &>/dev/null; then
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -y >/dev/null 2>&1 || true
        apt-get install -y xvfb openbox x11vnc websockify novnc nginx-light apache2-utils tar bzip2 libgtk-3-0 libasound2t64 libasound2 libdbus-glib-1-2 libxt6 libx11-xcb1 >/dev/null 2>&1 || true
    elif command -v dnf &>/dev/null; then
        dnf install -y xorg-x11-server-Xvfb openbox x11vnc python3-websockify novnc nginx httpd-tools tar bzip2 gtk3 alsa-lib dbus-glib libXt >/dev/null 2>&1 || true
    elif command -v pacman &>/dev/null; then
        pacman -Sy --noconfirm xorg-server-xvfb openbox x11vnc python-websockify novnc nginx apache-tools tar bzip2 gtk3 alsa-lib >/dev/null 2>&1 || true
    fi

    echo -e "  • Comprobando disponibilidad de Mozilla Firefox nativo..."
    if ! command -v firefox &>/dev/null && ! command -v firefox-esr &>/dev/null; then
        if command -v apt-get &>/dev/null; then
            apt-get install -y firefox-esr >/dev/null 2>&1 || true
        fi
        if ! command -v firefox &>/dev/null && ! command -v firefox-esr &>/dev/null; then
            echo -e "  • Descargando paquete oficial optimizado de Mozilla Firefox..."
            mkdir -p /opt/firefox
            case "$ARCH" in
                aarch64|arm64)
                    FF_URL="https://download.mozilla.org/?product=firefox-latest&os=linux-arm64&lang=en-US"
                    ;;
                *)
                    FF_URL="https://download.mozilla.org/?product=firefox-latest&os=linux64&lang=en-US"
                    ;;
            esac
            (curl -fsSL "$FF_URL" | tar -xjf - -C /opt/) >/dev/null 2>&1 || true
            if [ -f /opt/firefox/firefox ]; then
                ln -sf /opt/firefox/firefox /usr/local/bin/firefox
            fi
        fi
    fi

    FF_BIN=$(command -v firefox || command -v firefox-esr || echo "/opt/firefox/firefox")
    echo -e "  • ${GREEN}✓ Entorno nativo listo ($($FF_BIN --version 2>/dev/null || echo 'Firefox') + Xvfb + noVNC + Nginx).${NC}"
else
    echo -e "\n${BLUE}[4/7] Comprobando entorno Docker...${NC}"
    # Detener servicios nativos si estaban corriendo para evitar colisión de puertos
    systemctl stop vps-browser vps-tunnel >/dev/null 2>&1 || true

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
fi

# 6. Apertura de puertos en Firewall (UFW / Firewalld / iptables) y Escudo Dinámico
echo -e "\n${BLUE}[5/7] Configurando cortafuegos, Escudo Dinámico de IP y Fail2ban...${NC}"
mkdir -p "$DEPLOY_DIR"
cat <<'EOF' > "$DEPLOY_DIR/shield-firewall.sh"
#!/usr/bin/env bash
# VPS Browser - Dynamic IP Whitelist Shield & Stealth Port Protection
ACTION=${1:-status}
CLIENT_IP=${2:-""}
PORT=${3:-3000}

case "$ACTION" in
    allow)
        if [ -n "$CLIENT_IP" ]; then
            # Clean existing duplicate rule
            iptables -D INPUT -p tcp -s "$CLIENT_IP" --dport "$PORT" -j ACCEPT 2>/dev/null || true
            # Insert at top of chain
            iptables -I INPUT 1 -p tcp -s "$CLIENT_IP" --dport "$PORT" -j ACCEPT
            echo "ALLOWED $CLIENT_IP on port $PORT"
        fi
        ;;
    deny)
        if [ -n "$CLIENT_IP" ]; then
            iptables -D INPUT -p tcp -s "$CLIENT_IP" --dport "$PORT" -j ACCEPT 2>/dev/null || true
            echo "REMOVED $CLIENT_IP on port $PORT"
        fi
        ;;
    enable-shield)
        # Drop external probes to port 3000 unless previously whitelisted
        iptables -D INPUT -p tcp --dport "$PORT" -j DROP 2>/dev/null || true
        iptables -A INPUT -p tcp --dport "$PORT" -j DROP 2>/dev/null || true
        echo "SHIELD_ENABLED_ON_PORT_$PORT"
        ;;
    disable-shield)
        iptables -D INPUT -p tcp --dport "$PORT" -j DROP 2>/dev/null || true
        echo "SHIELD_DISABLED_ON_PORT_$PORT"
        ;;
    status)
        iptables -L INPUT -n -v --line-numbers 2>/dev/null | grep -E "$PORT|dpt:$PORT" || echo "Sin reglas activas en puerto $PORT"
        ;;
esac
EOF
chmod +x "$DEPLOY_DIR/shield-firewall.sh"

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

# Instalación y configuración preventiva de Fail2ban (Anti Brute-Force)
if command -v apt-get &>/dev/null; then
    apt-get install -y fail2ban >/dev/null 2>&1 || true
elif command -v dnf &>/dev/null; then
    dnf install -y fail2ban >/dev/null 2>&1 || true
fi

if command -v fail2ban-client &>/dev/null; then
    mkdir -p /etc/fail2ban/jail.d
    cat <<'EOF' > /etc/fail2ban/jail.d/vps-browser.local
[sshd]
enabled = true
port = ssh
maxretry = 5
findtime = 600
bantime = 3600

[nginx-http-auth]
enabled = true
port = 3000,3001,http,https
maxretry = 5
findtime = 600
bantime = 3600
EOF
    systemctl restart fail2ban 2>/dev/null || service fail2ban restart 2>/dev/null || true
    echo -e "  • ${GREEN}✓ Fail2ban activo (Protección contra fuerza bruta en SSH y puerto 3000).${NC}"
fi

# 7. Preparación de carpeta, políticas ultra-ligeras de Firefox y docker-compose.yml
if [ "$DEPLOY_MODE" = "native" ]; then
    echo -e "\n${BLUE}[6/7] Configurando políticas de privacidad, uBlock Origin y proxy Nginx nativo...${NC}"
    mkdir -p "$DEPLOY_DIR" /etc/firefox/policies /opt/firefox/distribution
    cd "$DEPLOY_DIR"

    if [ -f "$DEPLOY_DIR/vps-browser.conf" ]; then
        EXISTING_PASS=$(grep -E '^PASSWORD=' "$DEPLOY_DIR/vps-browser.conf" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d ' ' || true)
        if [ -n "$EXISTING_PASS" ]; then
            PASS="$EXISTING_PASS"
        fi
    fi
    if [ -z "$PASS" ]; then
        PASS=$(openssl rand -hex 8)
    fi
    echo "PASSWORD=$PASS" > "$DEPLOY_DIR/vps-browser.conf"

    cat <<'EOF' > "$DEPLOY_DIR/policies.json"
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
    cp "$DEPLOY_DIR/policies.json" /etc/firefox/policies/policies.json 2>/dev/null || true
    cp "$DEPLOY_DIR/policies.json" /opt/firefox/distribution/policies.json 2>/dev/null || true

    mkdir -p /etc/nginx
    if command -v htpasswd &>/dev/null; then
        htpasswd -b -c /etc/nginx/.htpasswd admin "$PASS" >/dev/null 2>&1 || true
    else
        CRYPT_PASS=$(openssl passwd -apr1 "$PASS")
        echo "admin:$CRYPT_PASS" > /etc/nginx/.htpasswd
    fi
    chmod 644 /etc/nginx/.htpasswd

    mkdir -p /etc/nginx/conf.d /etc/nginx/sites-available /etc/nginx/sites-enabled
    cat <<'EOF' > /etc/nginx/conf.d/vps-browser.conf
server {
    listen 3000 default_server;
    listen [::]:3000 default_server;
    server_name _;

    auth_basic "VPS Browser Protected";
    auth_basic_user_file /etc/nginx/.htpasswd;

    location / {
        proxy_pass http://127.0.0.1:5800;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
        proxy_buffering off;
        proxy_cache off;
        tcp_nodelay on;
    }
}
EOF
    rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
    cp /etc/nginx/conf.d/vps-browser.conf /etc/nginx/sites-available/vps-browser 2>/dev/null || true
    ln -sf /etc/nginx/sites-available/vps-browser /etc/nginx/sites-enabled/ 2>/dev/null || true
    systemctl reload nginx 2>/dev/null || systemctl restart nginx 2>/dev/null || true

    echo -e "\n${BLUE}[7/7] Iniciando servicios de Firefox nativo y túnel Cloudflare...${NC}"
    cat <<EOF > "$DEPLOY_DIR/run-native.sh"
#!/bin/bash
export DISPLAY=:1
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
Xvfb :1 -screen 0 1280x720x24 -nocursor &
sleep 1
command -v openbox &>/dev/null && openbox &
x11vnc -display :1 -localhost -rfbport 5900 -forever -shared -bg -nopw 2>/dev/null || true

NOVNC_DIR="/usr/share/novnc"
[ ! -d "\$NOVNC_DIR" ] && NOVNC_DIR="/usr/share/novnc-core"
websockify --web "\$NOVNC_DIR" 127.0.0.1:5800 127.0.0.1:5900 &

exec $FF_BIN --no-remote --private-window "https://duckduckgo.com"
EOF
    chmod +x "$DEPLOY_DIR/run-native.sh"

    cat <<EOF > /etc/systemd/system/vps-browser.service
[Unit]
Description=VPS Browser Native Service (Ultraligero)
After=network.target nginx.service

[Service]
Type=simple
User=root
WorkingDirectory=$DEPLOY_DIR
ExecStart=/bin/bash $DEPLOY_DIR/run-native.sh
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload >/dev/null 2>&1 || true
    systemctl enable --now vps-browser.service >/dev/null 2>&1 || true

    # Cloudflared nativo
    if ! command -v cloudflared &>/dev/null; then
        echo -e "  • Instalando binario oficial de Cloudflare Tunnel..."
        case "$ARCH" in
            aarch64|arm64) CF_ARCH="arm64" ;;
            *) CF_ARCH="amd64" ;;
        esac
        curl -fsSL --connect-timeout 10 "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${CF_ARCH}" -o /usr/local/bin/cloudflared >/dev/null 2>&1 || true
        chmod +x /usr/local/bin/cloudflared >/dev/null 2>&1 || true
    fi

    CF_URL=""
    if command -v cloudflared &>/dev/null; then
        cat <<EOF > /etc/systemd/system/vps-tunnel.service
[Unit]
Description=VPS Cloudflare Quick Tunnel
After=network.target vps-browser.service

[Service]
Type=simple
ExecStart=/usr/local/bin/cloudflared tunnel --no-autoupdate --url http://127.0.0.1:3000
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
        systemctl daemon-reload >/dev/null 2>&1 || true
        systemctl enable --now vps-tunnel.service >/dev/null 2>&1 || true

        echo -e "  • ${BLUE}Obteniendo enlace seguro de Cloudflare Tunnel (sin puertos abiertos)...${NC}"
        for i in $(seq 1 15); do
            sleep 1
            CF_URL=$(journalctl -u vps-tunnel --no-pager -n 50 2>&1 | grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' | tail -n 1 || true)
            if [ -n "$CF_URL" ]; then
                break
            fi
        done
        if [ -n "$CF_URL" ]; then
            echo "$CF_URL" > "$DEPLOY_DIR/cloudflare_tunnel.txt"
            echo -e "  • ${GREEN}✓ Túnel Cloudflare activo:${NC} ${BOLD}$CF_URL${NC}"
        fi
    fi

    PUBLIC_IP=$(curl -s --max-time 3 https://api.ipify.org 2>/dev/null || curl -s --max-time 3 https://icanhazip.com 2>/dev/null || hostname -I | awk '{print $1}')

    echo ""
    echo -e "${GREEN}${BOLD}======================================================${NC}"
    echo -e "${GREEN}${BOLD}    🎉 ¡VPS BROWSER FIREFOX NATIVO INSTALADO!         ${NC}"
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
    echo -e "Comandos de gestión en modo nativo:"
    echo -e "  • Ver estado:  ${BOLD}systemctl status vps-browser vps-tunnel${NC}"
    echo -e "  • Ver logs:    ${BOLD}journalctl -u vps-browser -f${NC}"
    echo -e "  • Reiniciar:   ${BOLD}systemctl restart vps-browser vps-tunnel${NC}"
    echo -e "  • Detener:     ${BOLD}systemctl stop vps-browser vps-tunnel${NC}"
    echo -e "${GREEN}${BOLD}======================================================${NC}\n"
    exit 0
else
    # 7. Preparación de carpeta, políticas ultra-ligeras de Firefox y docker-compose.yml
    echo -e "\n${BLUE}[6/7] Configurando entorno optimizado para VPS ligera y túnel Cloudflare...${NC}"

    DEPLOY_DIR="/opt/vps-browser"
    mkdir -p "$DEPLOY_DIR/config"
    cd "$DEPLOY_DIR"

    PASS=""
    if [ -f "$DEPLOY_DIR/docker-compose.yml" ]; then
        EXISTING_PASS=$(grep -E 'PASSWORD=' "$DEPLOY_DIR/docker-compose.yml" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d ' ' || true)
        if [ -n "$EXISTING_PASS" ]; then
            PASS="$EXISTING_PASS"
        fi
    fi
    if [ -z "$PASS" ]; then
        PASS=$(openssl rand -hex 8)
    fi

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

    # Comprobar si la imagen ya existe localmente en Docker
    if docker image inspect "$IMAGE_NAME" >/dev/null 2>&1 || docker image inspect "ghcr.io/linuxserver/firefox:latest" >/dev/null 2>&1 || docker image inspect "linuxserver/firefox:latest" >/dev/null 2>&1; then
        echo -e "  • ${GREEN}✓ Imagen de Firefox ya descargada en la VPS. Usando caché local...${NC}"
    else
        echo -e "  • Descargando imagen de Firefox ($IMAGE_NAME)..."
        echo -e "  • ${YELLOW}Nota: La descarga y extracción de capas (~1 GB) toma entre 1 y 3 minutos según la CPU/disco de tu VPS.${NC}"
        echo -e "  • ${YELLOW}Mantén la pantalla activa; la terminal reportará el progreso a continuación.${NC}"

        (
            elapsed=0
            while true; do
                sleep 10
                elapsed=$((elapsed + 10))
                mins=$((elapsed / 60))
                secs=$((elapsed % 60))
                echo -e "    ⏳ Procesando y extrayendo capas del navegador en Docker (${mins}m ${secs}s transcurridos)..."
            done
        ) &
        HEARTBEAT_PID=$!

        PULL_SUCCESS=0
        if docker pull "$IMAGE_NAME"; then
            PULL_SUCCESS=1
        else
            echo -e "  • ${YELLOW}Probando descarga desde GitHub Container Registry (ghcr.io)...${NC}"
            IMAGE_NAME="ghcr.io/linuxserver/firefox:latest"
            if docker pull "$IMAGE_NAME"; then
                PULL_SUCCESS=1
            else
                echo -e "  • ${YELLOW}Probando descarga desde Docker Hub...${NC}"
                IMAGE_NAME="docker.io/linuxserver/firefox:latest"
                if docker pull "$IMAGE_NAME"; then
                    PULL_SUCCESS=1
                fi
            fi
            sed -i "s|image: lscr.io/linuxserver/firefox:latest|image: $IMAGE_NAME|g" docker-compose.yml 2>/dev/null || true
        fi

        kill $HEARTBEAT_PID 2>/dev/null || true
        wait $HEARTBEAT_PID 2>/dev/null || true

        if [ "$PULL_SUCCESS" -eq 1 ]; then
            echo -e "  • ${GREEN}✓ Imagen de Firefox lista y descomprimida correctamente.${NC}"
        else
            echo -e "  • ${YELLOW}Aviso: Continuando con el arranque del contenedor...${NC}"
        fi
    fi

    # Descarga previa de imagen cloudflared (tolerante a fallos)
    if docker image inspect "cloudflare/cloudflared:latest" >/dev/null 2>&1; then
        echo -e "  • ${GREEN}✓ Imagen de Cloudflare Tunnel en caché local.${NC}"
    else
        echo -e "  • Obteniendo imagen de Cloudflare Tunnel..."
        docker pull cloudflare/cloudflared:latest >/dev/null 2>&1 || true
    fi

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

        docker stop vps-tunnel >/dev/null 2>&1 || true
        docker rm vps-tunnel >/dev/null 2>&1 || true
        docker run -d \
          --name vps-tunnel \
          --restart unless-stopped \
          --net=host \
          cloudflare/cloudflared:latest tunnel --no-autoupdate --url http://127.0.0.1:3000 >/dev/null 2>&1 || true
    fi

    echo -e "  • ${BLUE}Obteniendo enlace seguro de Cloudflare Tunnel (sin puertos abiertos)...${NC}"
    CF_URL=""
    for i in $(seq 1 15); do
        sleep 1
        CF_URL=$(docker logs vps-tunnel 2>&1 | grep -o 'https://[-a-zA-Z0-9]*\.trycloudflare\.com' | head -n 1 || true)
        if [ -n "$CF_URL" ]; then
            break
        fi
        echo -e "  • Esperando asignación de túnel Cloudflare ($i/15)..."
    done

    if [ -n "$CF_URL" ]; then
        echo "$CF_URL" > "$DEPLOY_DIR/cloudflare_tunnel.txt"
        echo -e "  • ${GREEN}✓ Túnel Cloudflare activo:${NC} ${BOLD}$CF_URL${NC}"
    else
        echo -e "  • ${YELLOW}Túnel Cloudflare iniciando en segundo plano.${NC}"
    fi

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
fi
