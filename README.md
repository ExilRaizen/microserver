# Pedidos360 (Microservers)

Encargo final (EP1) de Cloud Native I (DSY1107, DuocUC). Sistema con frontend Angular + MSAL y backend de microservicios Spring Boot, autenticado con Azure AD (Entra ID) y desplegado en AWS.

## Arquitectura

```
Angular (MSAL) ──> AWS API Gateway (JWT Authorizer) ──> EC2 (3 microservicios Spring Boot) ──> RDS PostgreSQL
                              │
                              └── valida el JWT emitido por Azure AD antes de reenviar la request
```

- **Frontend**: Angular + `@azure/msal-angular`, login/logout con Redirect, `MsalInterceptor` adjuntando el token correcto según el endpoint (`protectedResourceMap`).
- **API Gateway**: HTTP API con un JWT Authorizer validando tokens contra el tenant de Azure AD. Cada microservicio tiene sus propias rutas (GET/POST) y las de escritura exigen el scope correspondiente.
- **Backend**: 3 microservicios Spring Boot independientes, cada uno valida el JWT de nuevo con `oauth2ResourceServer().jwt()` (Spring Security).
- **Base de datos**: una instancia RDS PostgreSQL (`pedidosdb`) compartida por los 3 microservicios.

## Microservicios

| Servicio | Carpeta | Puerto | Endpoint principal | Scope requerido |
|---|---|---|---|---|
| Pedidos | `pedidos-backend/` | 8081 | `/api/pedidos` | `Pedidos.Create` |
| Catálogo | `catalogo-backend/` | 8082 | `/api/catalogo` | `Catalogo.Manage` |
| Soporte | `soporte-backend/` | 8083 | `/api/soporte` | `Soporte.Create` |

Los tres reutilizan el mismo registro de aplicación en Azure AD (`pedidos-backend`), cada uno expuesto como un scope distinto dentro de esa misma API.

## Cómo levantarlo en local

Cada microservicio es un proyecto Maven independiente. Desde la carpeta de cada uno:

```bash
export DB_PASSWORD="<password de la RDS>"
mvn spring-boot:run
```

El frontend, desde `frontend-cloudnative/`:

```bash
npm install
ng serve
```

Por defecto corre en `http://localhost:4200` y apunta a la API desplegada en AWS (ver `src/environments/environment.development.ts`).

### Variables de entorno necesarias

- `DB_PASSWORD`: password de la instancia RDS. No está hardcodeada en el repo por seguridad — hay que setearla en cada sesión de terminal antes de levantar cualquier microservicio.

## Despliegue en AWS

Los 3 microservicios corren sobre una misma instancia EC2 (t3.micro, Amazon Learner Lab), cada uno como un proceso Java independiente en su puerto:

```bash
export DB_PASSWORD="<password>"
nohup java -Xmx220m -jar <servicio>-backend-0.0.1-SNAPSHOT.jar > <servicio>.log 2>&1 &
```

API Gateway enruta cada path (`/api/pedidos`, `/api/catalogo`, `/api/soporte`) a su puerto correspondiente en la EC2, con el JWT Authorizer adjunto a cada ruta.

**Importante**: la instancia no tiene Elastic IP asignada, así que la IP pública cambia cada vez que se detiene y se vuelve a iniciar el lab. Cuando eso pasa hay que actualizar las 3 integraciones de API Gateway con la IP nueva antes de que el frontend vuelva a funcionar contra el ambiente desplegado.

## Problema conocido: errores 503 intermitentes

La instancia EC2 usada (t3.micro, cuenta de AWS Academy) tiene 1 GB de RAM. Corriendo los 3 microservicios Spring Boot al mismo tiempo, cada uno como su propia JVM, la memoria disponible queda muy al límite — en algunos casos esto generó errores `503 Service Unavailable` desde API Gateway porque la instancia no lograba responder a tiempo.

Confirmé que no es un problema de la aplicación: probando cada microservicio directo en la instancia (sin pasar por API Gateway) respondían de inmediato y de forma correcta. El origen es la limitación de recursos del ambiente de laboratorio gratuito, no un error de autenticación, de la lógica de los microservicios ni de la validación del JWT.

Como mitigación agregué un swap file de 1 GB a la instancia, lo que reduce bastante la probabilidad de que esto ocurra. En un ambiente real esto se resolvería con una instancia de mayor capacidad, separando los microservicios en instancias distintas, o con auto-scaling.

## Autenticación

- **Frontend (público)**: app registrada en Azure AD, client ID `15dedad7-7204-44f7-9e0d-6589e6ced496`.
- **Backend (API)**: app registrada en Azure AD, client ID `0e7d4bab-2455-4de8-bb75-c49e17617089`, con 3 scopes expuestos (`Pedidos.Create`, `Catalogo.Manage`, `Soporte.Create`), uno por microservicio.
- **Tenant**: `785588af-9f25-4dc1-a2d9-afbe56ee1787`.

Todas las rutas de lectura requieren solo estar autenticado; las de escritura (POST) requieren además el scope específico del microservicio correspondiente.
