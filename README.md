# MicroServers

Proyecto de Cloud Native I (DSY1107, DuocUC). Es un sistema de administración de hosting con frontend en Angular + MSAL y backend de microservicios en Spring Boot, autenticado con Azure AD (Entra ID).

En la Evaluación Parcial 3 le integré RabbitMQ: cuando se crea un pedido, Pedidos publica un evento y Soporte lo procesa de forma asíncrona, con confirmación manual (ACK), reintentos y una cola de mensajes fallidos (DLQ) que genera tickets automáticos. Además, Soporte quedó como microservicio de administración de RabbitMQ, con endpoints REST para crear y eliminar colas, exchanges y bindings.

## Arquitectura

```
                         Azure AD (Entra ID)
                                │ JWT
                                ▼
Angular (MSAL) ──┬──> Pedidos  :8081 ──(publica pedido.creado)──┐
                 ├──> Catálogo :8082                             ▼
                 └──> Soporte  :8083 <──(consume)── RabbitMQ (cluster de 2 nodos)
                                │
                                ▼
                     PostgreSQL (pedidosdb)
```

- **Frontend**: Angular + `@azure/msal-angular`. Inicia sesión con Redirect y el `MsalInterceptor` adjunta el token con el scope que corresponde a cada microservicio (`protectedResourceMap`).
- **Backend**: 3 microservicios Spring Boot independientes. Cada uno valida el JWT con `oauth2ResourceServer().jwt()`.
- **Mensajería**: RabbitMQ 4.2 en un cluster de 2 nodos con Docker.
- **Base de datos**: PostgreSQL compartida por los 3 microservicios.

## Microservicios

| Servicio | Carpeta | Puerto | Endpoint principal | Scope para escritura |
|---|---|---|---|---|
| Pedidos | `pedidos-backend/` | 8081 | `/api/pedidos` | `Pedidos.Create` |
| Catálogo | `catalogo-backend/` | 8082 | `/api/catalogo` | `Catalogo.Manage` |
| Soporte | `soporte-backend/` | 8083 | `/api/soporte`, `/api/rabbitmq` | `Soporte.Create` |

- **Pedidos** guarda el pedido y publica el evento en RabbitMQ (productor).
- **Catálogo** administra los planes de hosting. No usa mensajería.
- **Soporte** consume los pedidos, registra las notificaciones para el cliente, maneja los tickets y administra los recursos de RabbitMQ.

## Mensajería con RabbitMQ

### Recursos

Toda la configuración está centralizada en una clase `RabbitMQConfig` en cada microservicio que usa mensajería. Ahí están los nombres de exchanges, colas y routing keys como constantes, y los beans de `Queue`, `Exchange` y `Binding`. Las dos clases declaran las colas con los mismos argumentos, porque si no coinciden RabbitMQ rechaza la declaración.

| Recurso | Tipo | Para qué |
|---|---|---|
| `pedidos.exchange` | Direct exchange | Recibe los eventos de pedidos |
| `pedidos.creados.queue` | Cola durable | Pedidos creados, pendientes de procesar |
| `pedidos.dlx` | Direct exchange (DLX) | Recibe los mensajes rechazados |
| `pedidos.creados.dlq` | Cola durable (DLQ) | Pedidos que no se pudieron procesar |

```
pedidos.exchange --(pedido.creado)--> pedidos.creados.queue
                                            │ NACK sin reencolar
                                            ▼
pedidos.dlx --(pedido.fallido)--> pedidos.creados.dlq
```

El mensaje viaja como texto plano con el formato `pedidoId|clienteEmail|producto|cantidad`. Lo dejé así de simple para no depender de la configuración de los conversores JSON.

### Flujo de un pedido

1. El usuario crea un pedido desde el frontend (`POST /api/pedidos`).
2. Pedidos lo guarda en la base de datos y publica el evento `pedido.creado` (`PedidoEventPublisher`). Si RabbitMQ falla al publicar, el error queda en el log pero el pedido igual se guarda.
3. Soporte lo recibe en `PedidoCreadoConsumer`, valida el mensaje y la regla de negocio (de 1 a 10 servidores por pedido), guarda una notificación para el cliente y confirma con ACK.
4. Si el pedido no se pudo procesar, termina en la DLQ. Ahí lo toma `PedidoDlqConsumer`, que registra el error en los logs y crea un ticket automático para que soporte lo revise.

### ACK manual y manejo de errores

Los consumidores usan ACK manual (`AcknowledgeMode.MANUAL`, prefetch 1). Según el resultado, el consumidor decide qué hacer:

| Situación | Ejemplo | Qué hace |
|---|---|---|
| Procesado bien | Pedido normal | `basicAck`: RabbitMQ elimina el mensaje |
| Error no recuperable | Formato inválido, o más de 10 servidores | `basicNack` sin reencolar: va directo a la DLQ |
| Error técnico, primer intento | Falla momentánea de la base de datos | `basicNack` reencolando: se reintenta una vez |
| Error técnico, ya reintentado | Sigue fallando | `basicNack` sin reencolar: va a la DLQ |

Para saber si es el primer intento uso la marca `redelivered` que pone RabbitMQ. Si el consumidor de la DLQ no logra crear el ticket, espera 5 segundos y devuelve el mensaje a la DLQ, así no se pierde.

### Organización del código (Soporte)

```
soporte_backend/
├── config/        RabbitMQConfig, RabbitListenerConfig (ACK manual), RabbitAdminConfig, SecurityConfig
├── messaging/
│   ├── pedidos/   PedidoCreadoConsumer, PedidoCreadoMensaje
│   └── dlq/       PedidoDlqConsumer
├── service/       NotificacionService, TicketService, RabbitAdminService, ListenerMGService
├── controller/    TicketController, RabbitAdminController, ListenerController
├── dto/           Requests y respuestas con validación
├── exception/     Excepciones y GlobalExceptionHandler
├── model/         Ticket, Notificacion
└── repository/
```

Separé la mensajería de la lógica de negocio: los consumidores solo se encargan de leer el mensaje y hacer ACK o NACK, y la lógica está en los servicios, que no saben nada de RabbitMQ.

## Administración de RabbitMQ

Soporte expone endpoints REST para administrar RabbitMQ sin entrar al panel. Todos requieren un JWT válido de Entra ID. La lógica está encapsulada en `RabbitAdminService`, que usa `RabbitAdmin`.

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/rabbitmq/queues` | Crea una cola. Body: `{"nombre": "...", "durable": true}` |
| GET | `/api/rabbitmq/queues/{nombre}` | Muestra la cola con sus mensajes y consumidores |
| DELETE | `/api/rabbitmq/queues/{nombre}` | Elimina una cola |
| POST | `/api/rabbitmq/queues/{nombre}/purge` | Vacía una cola |
| POST | `/api/rabbitmq/exchanges` | Crea un exchange. Body: `{"nombre": "...", "tipo": "direct\|topic\|fanout\|headers", "durable": true}` |
| DELETE | `/api/rabbitmq/exchanges/{nombre}` | Elimina un exchange |
| POST | `/api/rabbitmq/bindings` | Crea un binding. Body: `{"cola": "...", "exchange": "...", "routingKey": "..."}` |
| DELETE | `/api/rabbitmq/bindings?cola=...&exchange=...&routingKey=...` | Elimina un binding |
| GET | `/api/listeners` | Lista los listeners activos |
| POST | `/api/listeners/{id}/pause` y `/resume` | Pausa o reanuda un listener |

### Validaciones

- **Nombres:** solo letras, números, punto, guion y guion bajo, hasta 255 caracteres, y no pueden empezar con `amq.` porque RabbitMQ reserva ese prefijo.
- **Tipo de exchange:** solo `direct`, `topic`, `fanout` o `headers`.
- **Estado de RabbitMQ:** antes de crear o eliminar, el servicio revisa si el recurso existe.
- **Recursos protegidos:** las colas y exchanges del flujo de pedidos no se pueden eliminar desde la API.

Los errores responden siempre con el mismo formato (`timestamp`, `status`, `error`, `detalles`):

| Situación | Código |
|---|---|
| Recurso creado | 201 |
| Parámetros inválidos o JSON mal formado | 400 |
| La cola o el exchange no existe | 404 |
| Ya existe, o es un recurso protegido | 409 |
| RabbitMQ no disponible | 503 |

## Cómo levantarlo en local

Necesitas Docker Desktop, Java 17 o superior, Maven y Node.js con Angular CLI.

**1. RabbitMQ y PostgreSQL**, desde la raíz del repo:

```bash
docker compose up -d
```

Esto levanta los dos nodos de RabbitMQ (`rabbitmq-1` en los puertos 5672/15672 y `rabbitmq-2` en 5673/15673), que forman el cluster solos con `rabbitmq.conf`, y PostgreSQL en el puerto 5432. El panel de RabbitMQ queda en `http://localhost:15672`.

**2. Microservicios**, cada uno en su propia terminal:

```bash
cd pedidos-backend && mvn spring-boot:run
cd catalogo-backend && mvn spring-boot:run
cd soporte-backend && mvn spring-boot:run
```

Por defecto se conectan a la base local. Para usar otra base de datos, como RDS, se pueden definir las variables `DB_URL` y `DB_PASSWORD`.

**3. Frontend**:

```bash
cd frontend-cloudnative
npm install
ng serve
```

Queda en `http://localhost:4200`. Las URL de cada microservicio están en `src/environments/environment.development.ts`.

## Autenticación

- **Frontend (público)**: app registrada en Azure AD, client ID `15dedad7-7204-44f7-9e0d-6589e6ced496`.
- **Backend (API)**: app registrada en Azure AD, client ID `0e7d4bab-2455-4de8-bb75-c49e17617089`, con 3 scopes expuestos (`Pedidos.Create`, `Catalogo.Manage`, `Soporte.Create`), uno por microservicio.
- **Tenant**: `785588af-9f25-4dc1-a2d9-afbe56ee1787`.

Las rutas de lectura solo piden estar autenticado. Las de escritura piden además el scope del microservicio correspondiente.

## Despliegue en AWS (EP1)

En la evaluación anterior desplegué los 3 microservicios en una EC2 (t3.micro, AWS Academy), detrás de un API Gateway con JWT Authorizer validando contra Azure AD, y con RDS PostgreSQL como base de datos.

Con 1 GB de RAM, correr las 3 JVM al mismo tiempo dejaba la memoria al límite, y eso generó errores 503 intermitentes desde API Gateway. Probé los microservicios directo en la instancia y respondían bien, así que el problema era de recursos del ambiente de laboratorio y no de la aplicación. Lo mitigué con un swap de 1 GB. Para un nuevo despliegue usaría una instancia más grande y límites de memoria por contenedor.

## Mejoras pendientes

- Usar un usuario de RabbitMQ propio para las aplicaciones en vez de `admin`, y guardar las contraseñas en un gestor de secretos (AWS Secrets Manager o Azure Key Vault). Las credenciales que están en el repo son solo de desarrollo local.
- Restringir los endpoints de administración de RabbitMQ con un rol de administrador en Entra ID.
- Usar colas quorum para que los mensajes queden replicados en los dos nodos del cluster.
