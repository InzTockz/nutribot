# NutriBot — Seguridad y configuración (Sprint 4)

## Dos perspectivas, dos puertas

| Perspectiva | Frontend | API | Acceso |
|---|---|---|---|
| Cliente | `/chat` (o el script `nutribot.js` en la web de Battilana) | `/api/public/**` | Libre, sin cuenta |
| Personal | `/login` y `/admin/**` | `/api/admin/**` | Token JWT + rol |

## Roles

| Recurso | Cliente | ASESOR | ADMIN |
|---|---|---|---|
| Chat, valorar, WhatsApp, dejar sus datos | ✓ | ✓ | ✓ |
| Consultas pendientes (ver, atender, exportar) | 401 | ✓ | ✓ |
| Clasificación y derivación | 401 | ✓ | ✓ |
| Base de conocimiento | 401 | 403 | ✓ |
| Métricas, reporte y revisión | 401 | 403 | ✓ |

Ocultar menús en Angular es solo comodidad: quien protege los datos es el backend (`SecurityConfig`).

## Secretos fuera del código

Las claves viven en el archivo **`.env`** de esta carpeta, que **no se sube a Git** (está en `.gitignore`).
Lo leen `docker-compose.yml` (MySQL) y Spring Boot (`spring.config.import` en `application.yml`).
Para un equipo nuevo: copiar `.env.example` como `.env` y completar los valores.

| Variable | Uso |
|---|---|
| `MYSQL_ROOT_PASSWORD`, `DB_USER`, `DB_PASSWORD` | Base de datos |
| `NUTRIBOT_JWT_SECRET` | Firma de los tokens (mínimo 32 caracteres) |
| `NUTRIBOT_JWT_HORAS` | Duración de la sesión (por defecto 8) |
| `NUTRIBOT_ADMIN_USER` / `NUTRIBOT_ADMIN_PASSWORD` | Administrador inicial (se crea solo si no existe) |
| `NUTRIBOT_ASESOR_USER` / `NUTRIBOT_ASESOR_PASSWORD` | Asesor inicial (opcional) |
| `NUTRIBOT_CORS_ORIGINS` | Orígenes permitidos, separados por comas |

## Decisiones de seguridad

- Contraseñas con **BCrypt**; nunca en texto plano.
- Token **JWT HMAC-SHA256**, 8 h; el rol y el estado se leen de la base en cada petición
  (desactivar un usuario corta su sesión de inmediato).
- El frontend guarda el token en `sessionStorage` y lo envía **solo** a `/api/admin/**` y `/api/auth/me`.
- Login con mensaje genérico ("Usuario o contraseña incorrectos") y tiempo de respuesta igual exista o no el usuario.
- API sin sesiones en el servidor (STATELESS); CSRF desactivado porque no se usan cookies.
- El cliente que deja sus datos (HU09) solo recibe la confirmación, nunca datos de la bitácora (Ley N.° 29733).

## Rotar las claves de MySQL

Las claves `root123` / `nutribot123` ya estuvieron en el historial de GitHub: conviene cambiarlas.
MySQL solo aplica `MYSQL_PASSWORD` al crear el volumen, así que el cambio se hace dentro de la base:

```powershell
docker exec -it nutribot-mysql mysql -uroot -p
```
```sql
ALTER USER 'nutribot'@'%' IDENTIFIED BY 'NUEVA_CLAVE';
ALTER USER 'root'@'%' IDENTIFIED BY 'NUEVA_CLAVE_ROOT';
ALTER USER 'root'@'localhost' IDENTIFIED BY 'NUEVA_CLAVE_ROOT';
```
Luego actualizar `DB_PASSWORD` y `MYSQL_ROOT_PASSWORD` en `.env` y reiniciar el backend.

## Integrar el chat en la web de Battilana

```html
<script src="https://SERVIDOR-DE-NUTRIBOT/nutribot.js" defer></script>
```
Prueba local: `http://localhost:4200/demo-web.html`. Agregar el dominio de la web en `NUTRIBOT_CORS_ORIGINS`.
