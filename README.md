# 🌐 VPS Browser (Android Client + Cloud Browser Server)

**VPS Browser** te permite navegar por Internet de forma segura y privada desde tu teléfono Android, ejecutando el navegador (Firefox o Chromium con aceleración KasmVNC / WebSockets) directamente en tu propio servidor VPS mediante la imagen oficial recomendada de **LinuxServer.io** (`lscr.io/linuxserver/firefox`). 

De esta forma:
- El navegador real corre en tu VPS (tu IP real del teléfono nunca se expone).
- Mucho más ligero en consumo de memoria RAM en VPS modestas gracias a Firefox (`linuxserver/firefox`).
- Puedes acceder a páginas de escritorio, paneles de administración, herramientas de desarrollo (F12 DevTools).
- Cuentas con emulación táctil directa o modo Trackpad/Ratón virtual con clic derecho y accesos rápidos (Ctrl, Alt, Esc, Tab).
- Monitoreo de latencia y ping en tiempo real en la aplicación.

---

## 📱 Características de la App Android

- **⚡ Auto-Instalador en 1 Toque desde la App**: ¡No necesitas abrir ninguna terminal! Introduces la IP y contraseña SSH de tu VPS en la app, y la APK instala Docker, optimiza la memoria RAM con SWAP, abre los puertos y levanta Firefox automáticamente con terminal en vivo en la pantalla de tu celular.
- **Perfiles de Servidores VPS**: Guarda múltiples servidores VPS y conéctate con un solo toque.
- **Barra de navegación (Omnibox)**: Escribe cualquier URL o término de búsqueda directamente.
- **Modo Táctil vs Modo Ratón**:
  - *Modo Táctil*: Toca e interactúa como una pantalla táctil normal.
  - *Modo Ratón*: Trackpad virtual con cursor en pantalla, botón de Clic Izquierdo y Clic Derecho (para menús contextuales de escritorio).
- **Barra de Teclas de Escritorio**: Teclas rápidas `Esc`, `Tab`, `Ctrl`, `Alt`, `Enter` y `F12`.
- **Modo Pantalla Completa Inmersiva**: Oculta barras de herramientas para aprovechar el 100% de la pantalla del móvil.
- **Indicador de Estado y Ping**: Consulta en tiempo real la latencia a tu VPS en milisegundos.


---

## 🚀 Despliegue del Servidor en tu VPS (1 Solo Comando)

Puedes instalarlo en **cualquier VPS** (Ubuntu, Debian, CentOS, AlmaLinux, Rocky Linux, Oracle Cloud ARM/AMD, Amazon Linux, etc.) con una sola línea:

```bash
curl -fsSL https://raw.githubusercontent.com/anhot11/VpsBrowser/main/server/deploy.sh | sudo bash
```

### 🧠 ¿Qué hace el instalador de forma automática?
1. **Detecta el Sistema Operativo y la Arquitectura:** Compatible con Intel/AMD (`x86_64`) y ARM64 (`aarch64` como Oracle Cloud Ampere o AWS Graviton).
2. **Instala dependencias base:** Detecta si tu VPS usa `apt`, `dnf`, `yum`, `pacman`, `apk` o `zypper` e instala las utilidades necesarias (`curl`, `wget`, `openssl`).
3. **Optimización de Memoria (RAM y SWAP):** Si tu VPS tiene menos de 2GB de RAM, crea y activa automáticamente un archivo **SWAP de 2GB** para evitar cierres o falta de memoria (OOM).
4. **Instalación y arranque de Docker:** Si Docker o Docker Compose no están instalados, los descarga, configura y arranca el servicio.
5. **Apertura de Puertos en el Cortafuegos (Firewall):** Abre automáticamente los puertos `3000` (HTTP) y `3001` (HTTPS) en `ufw`, `firewalld` o `iptables`.
6. **Generación de credenciales seguras y despliegue:** Genera una clave aleatoria y arranca **Firefox (`lscr.io/linuxserver/firefox:latest`)** en segundo plano.

---

*(Opcional: Si prefieres clonar el repositorio manualmente:)*
```bash
git clone https://github.com/anhot11/VpsBrowser.git
cd VpsBrowser/server
sudo ./deploy.sh
```

---

## ⚙️ Compilación Automática con GitHub Actions

Este repositorio incluye un flujo de trabajo de GitHub Actions (`.github/workflows/build-apk.yml`) que:
1. Configura el entorno con Java 17 y el SDK de Android.
2. Descarga dependencias y compila el APK con `./gradlew assembleDebug`.
3. Sube el APK compilado como un **Artefacto descargable** listo para instalar en tu móvil.

---

## 🛠️ Estructura del Proyecto

```
├── .github/workflows/build-apk.yml  # Automatización de compilación CI/CD
├── app/                             # Código fuente Android (Kotlin + Material Design 3)
│   ├── src/main/java/...            # MainActivity, ProfilesActivity, SettingsActivity
│   └── src/main/res/...             # Layouts, recursos, temas y vectores
├── server/                          # Servidor de navegador para la VPS
│   ├── docker-compose.yml           # Contenedor Chromium optimizado
│   └── deploy.sh                    # Script de instalación automática en 1 click
├── build.gradle.kts                 # Configuración raíz de Gradle
├── settings.gradle.kts              # Configuración de módulos
└── gradlew                          # Gradle Wrapper ejecutable
```
