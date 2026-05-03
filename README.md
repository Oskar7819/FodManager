# FodManager

**FodManager** es una aplicación Android desarrollada como proyecto final del ciclo de **Desarrollo de Aplicaciones Multiplataforma (DAM)**. Su objetivo es digitalizar la gestión de inspecciones e incidencias **FOD** (*Foreign Object Debris / Foreign Object Damage*) en un entorno de hangar aeronáutico.

La aplicación permite registrar inspecciones, crear incidencias FOD, adjuntar evidencias fotográficas, consultar información por roles y mantener una trazabilidad clara de lo ocurrido en cada aeronave.

---

## Índice

- [Descripción del proyecto](#descripción-del-proyecto)
- [Objetivo](#objetivo)
- [Funcionalidades principales](#funcionalidades-principales)
- [Roles de usuario](#roles-de-usuario)
- [Tecnologías utilizadas](#tecnologías-utilizadas)
- [Arquitectura general](#arquitectura-general)
- [Instalación y ejecución](#instalación-y-ejecución)
- [Configuración de Supabase](#configuración-de-supabase)
- [Estado actual del proyecto](#estado-actual-del-proyecto)
- [Mejoras futuras](#mejoras-futuras)
- [Autor](#autor)

---

## Descripción del proyecto

FodManager nace como una solución para sustituir registros manuales o poco centralizados por una herramienta digital orientada al control de objetos extraños en aeronaves y zonas de trabajo.

En un entorno aeronáutico, un objeto extraño puede suponer un riesgo para la seguridad, provocar daños, retrasos o pérdida de trazabilidad. Por este motivo, la aplicación se centra en facilitar el registro de inspecciones y el seguimiento de incidencias FOD desde un dispositivo Android.

La aplicación permite que cada usuario vea y realice acciones según su rol. Algunos perfiles tienen una visión global del sistema, mientras que otros trabajan únicamente sobre su aeronave asignada.

---

## Objetivo

El objetivo principal de FodManager es **digitalizar y agilizar la gestión de inspecciones e incidencias FOD** en un hangar aeronáutico.

Con esta aplicación se busca:

- Mejorar la trazabilidad de las inspecciones.
- Registrar incidencias FOD con información clara.
- Adjuntar evidencias fotográficas.
- Controlar el acceso mediante roles.
- Consultar información de aeronaves, usuarios, inspecciones e incidencias.
- Ofrecer una visión rápida del estado general mediante un dashboard.

---

## Funcionalidades principales

- Inicio de sesión con correo y contraseña.
- Validación de usuario activo después del login.
- Control de permisos según rol.
- Dashboard con información resumida en tarjetas.
- Consulta de las últimas incidencias registradas.
- Consulta de inspecciones globales.
- Gestión de aeronaves.
- Gestión de usuarios según jerarquía de permisos.
- Registro de inspecciones FOD por zona.
- Bloqueo de inspecciones duplicadas para una misma zona en el mismo día.
- Registro de incidencias FOD asociadas a inspecciones.
- Captura y almacenamiento de fotografías como evidencia.
- Visualización de imágenes de incidencias a pantalla completa.
- Corrección de orientación y mejora en la visualización de fotografías.
- Listados mediante tarjetas y RecyclerView.
- Comunicación con Supabase para autenticación, datos y almacenamiento.
- Uso de Edge Functions para acciones sensibles, como la creación segura de usuarios.

---

## Roles de usuario

La aplicación trabaja con distintos roles para adaptar la información y las acciones disponibles.

| Rol | Descripción general |
| --- | --- |
| **Administrador** | Perfil con visión global y permisos amplios de gestión. Puede gestionar usuarios, aeronaves, inspecciones e incidencias. |
| **Head Plant** | Perfil de consulta global orientado a supervisar el estado general del sistema. |
| **Focal Point FOD** | Perfil centrado en la supervisión FOD y en el seguimiento de incidencias. |
| **Mando GP4** | Perfil operativo asociado normalmente a una aeronave. Puede crear operarios y consultar información de su ámbito. |
| **Operario** | Perfil encargado de registrar inspecciones e incidencias FOD sobre su aeronave asignada. |
| **Quality** | Perfil orientado a la revisión de inspecciones, incidencias y evidencias asociadas. |

---

## Tecnologías utilizadas

| Tecnología | Uso en el proyecto |
| --- | --- |
| **Kotlin** | Lenguaje principal de desarrollo de la aplicación Android. |
| **XML** | Diseño de las interfaces visuales de la app. |
| **Android SDK / AndroidX** | Base de desarrollo móvil, actividades, fragmentos, permisos y navegación. |
| **Material Design** | Componentes visuales como tarjetas, botones, campos de texto y navegación inferior. |
| **RecyclerView** | Listados de aeronaves, usuarios, inspecciones e incidencias. |
| **Coroutines** | Ejecución de operaciones asíncronas sin bloquear la interfaz. |
| **Supabase Auth** | Inicio de sesión y gestión de sesiones. |
| **Supabase PostgREST** | Comunicación entre la app y las tablas de la base de datos. |
| **Supabase Storage** | Almacenamiento de imágenes de incidencias FOD. |
| **PostgreSQL** | Base de datos relacional utilizada por Supabase. |
| **Supabase Edge Functions** | Lógica segura en servidor, especialmente para creación de usuarios. |
| **Glide** | Carga de imágenes desde Supabase Storage. |
| **PhotoView** | Visualización de imágenes con zoom. |
| **Gradle** | Compilación y gestión de dependencias del proyecto Android. |
| **Git / GitHub** | Control de versiones y almacenamiento del repositorio. |

---

## Arquitectura general

El proyecto se apoya en tres bloques principales:

```text
Aplicación Android
        |
        | Kotlin + XML
        |
        v
Supabase Backend
        |
        | Auth + PostgREST + Storage + Edge Functions
        |
        v
Base de datos PostgreSQL
```

La app Android actúa como cliente principal. Desde ella, el usuario inicia sesión, navega por los módulos, registra inspecciones e incidencias y consulta los datos disponibles.

Supabase se utiliza como backend para centralizar la autenticación, la persistencia de datos, el almacenamiento de imágenes y la ejecución de lógica segura en servidor.

---



## Instalación y ejecución

### Requisitos previos

Antes de ejecutar el proyecto es necesario tener instalado:

- Android Studio.
- JDK compatible con el proyecto.
- Android SDK.
- Una cuenta/proyecto en Supabase.
- Conexión a internet para acceder al backend.

### Pasos

1. Clonar el repositorio:

```bash
git clone https://github.com/tu-usuario/FodManager.git
```

2. Entrar en la carpeta del proyecto:

```bash
cd FodManager
```

3. Abrir el proyecto con Android Studio.

4. Esperar a que Gradle sincronice las dependencias.

5. Configurar los datos de Supabase en el archivo correspondiente del proyecto.

6. Ejecutar la aplicación en un emulador o dispositivo Android físico.

---

## Compilación por consola

En Linux o macOS:

```bash
./gradlew assembleDebug
```

En Windows:

```bash
gradlew.bat assembleDebug
```

---

## Configuración de Supabase

El proyecto necesita un backend de Supabase configurado con:

- Autenticación de usuarios mediante email y contraseña.
- Tablas principales para usuarios, aeronaves, inspecciones e incidencias FOD.
- Storage para guardar imágenes de incidencias.
- Edge Function para la creación controlada de usuarios.

> Importante: no se debe subir al repositorio ninguna clave privada como `service_role`. La clave de servicio debe mantenerse únicamente en el entorno seguro de Supabase o en variables de entorno del backend.

---

## Seguridad

La creación de usuarios no se realiza directamente desde la app con permisos de administrador. Para esta acción se utiliza una Edge Function de Supabase que:

- Comprueba que la petición incluye autorización.
- Valida la sesión del usuario que realiza la acción.
- Comprueba que el usuario creador está activo.
- Limita qué roles puede crear cada perfil.
- Exige aeronave asignada para roles operativos cuando corresponde.
- Crea el usuario en Supabase Auth y después su perfil en la tabla de usuarios.

Este enfoque evita incluir claves sensibles dentro de la aplicación Android.

---

## Estado actual del proyecto

El proyecto se encuentra en una versión funcional para el alcance del TFG.

Incluye:

- Login y validación de usuario.
- Navegación principal mediante fragments.
- Dashboard con información organizada en tarjetas.
- Gestión de usuarios.
- Gestión de aeronaves.
- Registro y consulta de inspecciones.
- Registro y consulta de incidencias FOD.
- Evidencias fotográficas.
- Visualización mejorada de imágenes.
- Backend conectado con Supabase.

---



## Mejoras futuras

Algunas posibles ampliaciones del proyecto son:

- Añadir analítica para detectar zonas con más incidencias FOD.
- Incorporar notificaciones automáticas.
- Crear una versión de escritorio para consulta desde ordenador.
- Exportar informes en PDF o Excel.
- Añadir gráficos de evolución de incidencias.
- Mejorar la gestión de estados y seguimiento de cierre.
- Ampliar la configuración de permisos por rol.

---

## Autor

**Óscar González**  
Proyecto final del ciclo de **Desarrollo de Aplicaciones Multiplataforma (DAM)**.

---

## Uso académico

Este repositorio forma parte de un proyecto académico. Su finalidad principal es demostrar el desarrollo de una aplicación Android funcional conectada a un backend real, aplicando autenticación, base de datos, almacenamiento remoto, control de roles y gestión de incidencias FOD.
