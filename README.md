# 🌐 VPS Browser (Android Client + Cloud Browser Server)

**VPS Browser** te permite navegar por Internet de forma segura y privada desde tu teléfono Android, ejecutando el navegador (Chromium con aceleración WebRTC/WebSocket) directamente en tu propio servidor VPS. 

De esta forma:
- El navegador real corre en tu VPS (tu IP real del teléfono nunca se expone).
- Puedes acceder a páginas de escritorio, paneles de administración, herramientas de desarrollo (F12 DevTools).
- Cuentas con emulación táctil directa o modo Trackpad/Ratón virtual con clic derecho y accesos rápidos (Ctrl, Alt, Esc, Tab).
- Monitoreo de latencia y ping en tiempo real en la aplicación.

---

## 📱 Características de la App Android

- **Perfiles de Servidores VPS**: Guarda múltiples servidores VPS y conéctate con un solo toque.
- **Barra de navegación (Omnibox)**: Escribe cualquier URL o término de búsqueda directamente.
- **Modo Táctil vs Modo Ratón**:
  - *Modo Táctil*: Toca e interactúa como una pantalla táctil normal.
  - *Modo Ratón*: Trackpad virtual con cursor en pantalla, botón de Clic Izquierdo y Clic Derecho (para menús contextuales de escritorio).
- **Barra de Teclas de Escritorio**: Teclas rápidas `Esc`, `Tab`, `Ctrl`, `Alt`, `Enter` y `F12`.
- **Modo Pantalla Completa Inmersiva**: Oculta barras de herramientas para aprovechar el 100% de la pantalla del móvil.
- **Indicador de Estado y Ping**: Consulta en tiempo real la latencia a tu VPS en milisegundos.

---

## 🚀 Despliegue del Servidor en tu VPS

En tu servidor VPS (Ubuntu, Debian o CentOS), clona el repositorio o copia la carpeta `server/` y ejecuta:

```bash
cd server
sudo ./deploy.sh
```

El script instalará Docker automáticamente (si no lo tienes), generará una contraseña segura y levantará el contenedor de Chromium en el puerto **3000** (HTTP) y **3001** (HTTPS).

O si prefieres usar Docker Compose manualmente:
```bash
cd server
docker compose up -d
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
