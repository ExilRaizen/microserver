# MicroServers

Proyecto de Cloud Native I (DSY1107, DuocUC). Es un sistema de administración de hosting con frontend en Angular + MSAL y backend de microservicios en Spring Boot, autenticado con Azure AD (Entra ID).

En la Evaluación Parcial 3 le integré RabbitMQ: cuando se crea un pedido, Pedidos publica un evento y Soporte lo procesa de forma asíncrona, con confirmación manual (ACK), reintentos y una cola de mensajes fallidos (DLQ) que genera tickets automáticos. Además, Soporte quedó como microservicio de administración de RabbitMQ, con endpoints REST para crear y eliminar colas, exchanges y bindings.

En la Evaluación Parcial 5 le agregué Kafka como complemento, sin tocar el flujo de RabbitMQ. Kafka se usa para el streaming de eventos: cada pedido queda en un historial que leen la analítica de Soporte y el control de stock de Catálogo, y Catálogo publica el estado de sus planes para que Pedidos tenga una copia local y rechace pedidos sin stock. Soporte también administra Kafka (tópicos, configuraciones y grupos de consumidores).

## Arquitectura

```
                         Azure AD (Entra ID)
                                │ JWT
                                ▼
Angular (MSAL) ──┬──> Pedidos  :8081 ──(pedido.creado)──> RabbitMQ (cluster de 2 nodos) ──> Soporte
                 ├──> Catálogo :8082                                                         ▲
                 └──> Soporte  :8083                                                         │
                                                                                             │
                 Kafka (Zookeeper + 3 brokers)                                               │
   Pedidos ──────> pedidos.eventos ──┬──> Soporte  (grupo soporte-analitica) ────────────────┘
                                     └──> Catálogo (grupo catalogo-stock)
   Catálogo ─────> catalogo.planes ─────> Pedidos  (grupo pedidos-catalogo)
   Soporte/Catálogo ──(errores)──> pedidos.eventos.DLT ──> Soporte (grupo soporte-errores)

                     PostgreSQL (pedidosdb), compartida por los 3 microservicios
```

- **Frontend**: Angular + `@azure/msal-angular`. Inicia sesión con Redirect y el `MsalInterceptor` adjunta el token con el scope que corresponde a cada microservicio (`protectedResourceMap`).
- **Backend**: 3 microservicios Spring Boot independientes. Cada uno valida el JWT con `oauth2ResourceServer().jwt()`.
- **Mensajería**: RabbitMQ 4.2 en un cluster de 2 nodos con Docker.
- **Streaming**: Kafka 3.5 (Confluent 7.5) con Zookeeper y 3 brokers, más Kafka-UI para administrarlo.
- **Base de datos**: PostgreSQL compartida por los 3 microservicios.

## Microservicios

| Servicio | Carpeta | Puerto | Endpoint principal | Scope para escritura |
|---|---|---|---|---|
| Pedidos | `pedidos-backend/` | 8081 | `/api/pedidos` | `Pedidos.Create` |
| Catálogo | `catalogo-backend/` | 8082 | `/api/catalogo` | `Catalogo.Manage` |
| Soporte | `soporte-backend/` | 8083 | `/api/soporte`, `/api/rabbitmq`, `/api/kafka`, `/api/analitica` | `Soporte.Create` |

- **Pedidos** revisa el stock en su copia local del catálogo, guarda el pedido y publica el evento en RabbitMQ y en Kafka.
- **Catálogo** administra los planes de hosting y su stock. Descuenta stock con cada pedido y publica el estado de los planes en Kafka.
- **Soporte** consume los pedidos, registra las notificaciones para el cliente, maneja los tickets, arma la analítica de ventas y administra RabbitMQ y Kafka.

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

## Streaming con Kafka

RabbitMQ y Kafka conviven y cada uno hace lo que le corresponde:

- **RabbitMQ** sigue a cargo de la tarea "procesar este pedido una vez": la notificación al cliente, con ACK manual y DLQ. Una vez confirmado, el mensaje desaparece.
- **Kafka** guarda los eventos como un historial que se puede releer. Varios servicios leen el mismo evento de forma independiente, cada uno con su propio avance (offset), y un servicio nuevo puede leer desde el principio.

### Tópicos

Cada tópico lo declara el microservicio dueño del flujo, con `TopicBuilder` en su `KafkaConfig`, y Spring lo crea al arrancar si no existe. En los brokers dejé `auto.create.topics.enable=false`, así que un tópico mal escrito da error en vez de crearse con valores por defecto.

| Tópico | Dueño | Particiones | Réplicas | min.insync | Limpieza | Retención | Por qué |
|---|---|---|---|---|---|---|---|
| `pedidos.eventos` | Pedidos | 3 | 3 | 2 | `delete` | 7 días | Historial de pedidos. El registro oficial está en la base de datos, así que basta con una semana para reprocesar |
| `pedidos.eventos.DLT` | Pedidos | 3 | 3 | 2 | `delete` | 30 días | Eventos que fallaron. Se guardan más tiempo para alcanzar a revisarlos. Tiene las mismas particiones porque el error se reenvía a la misma partición |
| `catalogo.planes` | Catálogo | 3 | 3 | 2 | `compact` | Último estado de cada plan | Funciona como una tabla: Kafka conserva el último evento de cada plan y borra los anteriores. Un plan eliminado se marca con un tombstone (valor vacío) |

- **3 particiones** para que hasta 3 consumidores de un mismo grupo trabajen en paralelo.
- **Factor de replicación 3** (una copia en cada broker) con **`min.insync.replicas=2`**: junto con `acks=all` en el productor, un evento se confirma solo cuando está en al menos 2 brokers. Se puede caer un broker sin perder datos ni dejar de escribir.

### Keys y orden

- En `pedidos.eventos` la key es el **nombre del plan**. Kafka manda todos los eventos con la misma key a la misma partición, así que los pedidos de un mismo plan se procesan en orden. Eso importa para descontar stock.
- En `catalogo.planes` la key es el **id del plan**. Así la compactación sabe qué eventos son del mismo plan y deja solo el último.

### DTO y serialización

Los eventos viajan como JSON con `JacksonJsonSerializer`. El productor no manda cabeceras con el nombre de la clase (`spring.json.add.type.headers=false`), y cada consumidor tiene su propia copia del DTO y la indica con `spring.json.value.default.type`. Así ningún microservicio depende del código de otro, solo del formato JSON.

| DTO | Tópico | Campos |
|---|---|---|
| `PedidoCreadoEvento` | `pedidos.eventos` | `eventoId`, `pedidoId`, `clienteEmail`, `producto`, `cantidad`, `fechaCreacion` |
| `PlanActualizadoEvento` | `catalogo.planes` | `planId`, `nombre`, `tipoServidor`, `ram`, `slots`, `precio`, `disponible`, `stock`, `fechaActualizacion` |

El productor es **idempotente** (`enable.idempotence=true`): si reintenta un envío, Kafka no lo guarda dos veces.

### Productores

| Productor | Microservicio | Tópico | Cuándo publica |
|---|---|---|---|
| `PedidoKafkaPublisher` | Pedidos | `pedidos.eventos` | Al crear un pedido |
| `PlanKafkaPublisher` | Catálogo | `catalogo.planes` | Al crear, editar o eliminar un plan, al cambiar su stock y al arrancar (publica el catálogo completo) |

Los envíos son asíncronos. Si Kafka no está disponible, el error queda en el log pero la petición del usuario no falla, porque el dato ya quedó guardado en la base de datos.

### Consumidores y grupos

| Grupo | Microservicio | Tópico | Consumidores | Qué hace |
|---|---|---|---|---|
| `soporte-analitica` | Soporte | `pedidos.eventos` | 3 (uno por partición) | Suma cada pedido a la analítica por plan y por cliente |
| `catalogo-stock` | Catálogo | `pedidos.eventos` | 3 (uno por partición) | Descuenta el stock del plan. Si llega a 0, lo marca como no disponible y publica el cambio |
| `pedidos-catalogo` | Pedidos | `catalogo.planes` | 1 (asignación manual de las 3 particiones) | Mantiene una copia en memoria del catálogo para validar el stock al crear pedidos |
| `soporte-errores` | Soporte | `pedidos.eventos.DLT` | 1 | Crea un ticket por cada evento que falló |

- **Dentro de un grupo** los consumidores se reparten las particiones (balanceo de carga). Si uno se cae, Kafka reasigna sus particiones a los demás. Uso `CooperativeStickyAssignor` para que al entrar o salir un consumidor solo se muevan las particiones necesarias.
- **Entre grupos** cada uno recibe todos los eventos con su propio offset. Por eso la analítica y el stock leen el mismo pedido sin estorbarse.
- El offset se confirma después de procesar cada evento (`enable-auto-commit=false`, `ack-mode=record`). Si el servicio se cae a mitad de camino, el evento se vuelve a leer.
- Como un evento puede llegar dos veces, la analítica y el stock guardan el `eventoId` en una tabla (`soporte_evento_procesado` y `catalogo_evento_procesado`) y omiten los duplicados.
- El consumidor de `pedidos-catalogo` no espera a que Kafka le reparta particiones: se asigna las 3 directamente y parte desde el offset 0 cada vez que arranca, porque su copia está en memoria. Así no depende de un rebalanceo (que puede tardar hasta 45 segundos si la instancia anterior se cortó de golpe) y el catálogo queda cargado apenas sube el servicio. Como el tópico está compactado, relee un evento por plan.

Los consumidores solo leen el evento, lo validan y llaman a un servicio (`AnaliticaService`, `StockService`). La lógica de negocio no sabe nada de Kafka.

### Validación de stock en Pedidos

Antes de guardar un pedido, Pedidos busca el plan en su copia local del catálogo:

- Si el plan no está disponible o no le alcanza el stock, responde **409** y el pedido no se crea.
- Si el producto no está en el catálogo (por ejemplo, un servicio especial acordado con el cliente), se acepta.
- Si faltan el producto o la cantidad, responde **400**.

La copia es eventualmente consistente: puede ir unos milisegundos atrás de Catálogo. A cambio, Pedidos no depende de que Catálogo esté arriba para funcionar.

### Manejo de errores

Soporte y Catálogo usan el mismo `DefaultErrorHandler`:

| Situación | Ejemplo | Qué pasa |
|---|---|---|
| Procesado bien | Pedido normal | Se confirma el offset |
| JSON que no se puede leer | Texto que no es JSON | `ErrorHandlingDeserializer` evita que se caiga el consumidor. Va directo al DLT, sin reintentos |
| Evento inválido | Cantidad 0 o sin producto | `EventoInvalidoException`, que está marcada como no reintentable. Va directo al DLT |
| Error técnico | La base de datos no responde | 3 reintentos con espera de 1 s, 2 s y 4 s. Si sigue fallando, va al DLT |

Cada reintento queda en el log con el intento, la partición y el offset (`[KAFKA-REINTENTO]`). El `DeadLetterPublishingRecoverer` publica el evento en `pedidos.eventos.DLT` en la misma partición y le agrega cabeceras con el error y el grupo que falló. `PedidoDltKafkaConsumer` (Soporte) lo lee como texto, porque puede ser un JSON roto, y crea un ticket con ese detalle.

En Pedidos, si falla un evento de `catalogo.planes`, se reintenta 2 veces y después se omite con un log. No necesita DLT porque el próximo cambio de ese plan trae su estado completo.

### Organización del código

```
pedidos_backend/messaging/kafka/    PedidoKafkaPublisher, PedidoCreadoEvento,
                                    PlanCatalogoKafkaConsumer, PlanActualizadoEvento, CatalogoSnapshot
catalogo_backend/messaging/kafka/   PlanKafkaPublisher, PlanActualizadoEvento,
                                    PedidoStockKafkaConsumer, PedidoCreadoEvento
catalogo_backend/service/           StockService
soporte_backend/messaging/kafka/    PedidoEventoKafkaConsumer, PedidoDltKafkaConsumer, PedidoCreadoEvento
soporte_backend/service/            AnaliticaService, KafkaAdminService
```

## Administración de Kafka

Soporte expone endpoints REST para administrar Kafka con `AdminClient`. Todos requieren un JWT válido de Entra ID. La lógica está en `KafkaAdminService`.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/kafka/topics` | Lista los tópicos con sus particiones y réplicas |
| GET | `/api/kafka/topics/{nombre}` | Muestra cada partición (líder, réplicas, ISR) y la configuración del tópico |
| POST | `/api/kafka/topics` | Crea un tópico. Body: `{"nombre": "...", "particiones": 3, "replicas": 3, "cleanupPolicy": "delete", "retentionMs": 604800000}` (solo `nombre` es obligatorio) |
| PUT | `/api/kafka/topics/{nombre}/config` | Cambia la configuración en caliente. Body: `{"retentionMs": ..., "cleanupPolicy": "...", "minInsyncReplicas": 2}` (solo lo que se quiere cambiar) |
| POST | `/api/kafka/topics/{nombre}/partitions` | Aumenta las particiones. Body: `{"total": 6}` |
| DELETE | `/api/kafka/topics/{nombre}` | Elimina un tópico |
| GET | `/api/kafka/consumer-groups` | Lista los grupos de consumidores y su estado |
| GET | `/api/kafka/consumer-groups/{grupo}` | Muestra los miembros del grupo, las particiones de cada uno y el lag por partición |

- **Nombres:** letras, números, punto, guion y guion bajo, hasta 249 caracteres.
- **Límites:** de 1 a 12 particiones y de 1 a 3 réplicas (hay 3 brokers). Kafka no permite bajar particiones, así que eso responde 400.
- **Tópicos protegidos:** `pedidos.eventos`, `pedidos.eventos.DLT`, `catalogo.planes` y los internos (los que empiezan con `_`) no se pueden eliminar. Su configuración sí se puede cambiar.
- Los errores usan el mismo formato que RabbitMQ: 400 si Kafka rechaza los datos, 404 si no existe, 409 si ya existe o está protegido, y 503 si Kafka no responde en 10 segundos.

El **lag** de un grupo es cuántos eventos le faltan por leer: el último offset de la partición menos el offset que confirmó el grupo. Si crece, los consumidores no dan abasto.

También dejé el script `scripts/kafka-admin.ps1`, que hace lo mismo con las herramientas de línea de comandos que vienen en el contenedor de Kafka. Sirve para revisar el cluster sin token:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-admin.ps1 topics
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-admin.ps1 describe pedidos.eventos
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-admin.ps1 grupo soporte-analitica
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-admin.ps1 leer pedidos.eventos.DLT
```

### Analítica

| Método | Ruta | Qué devuelve |
|---|---|---|
| GET | `/api/analitica/planes` | Pedidos y unidades vendidas por producto, del más vendido al menos vendido |
| GET | `/api/analitica/clientes` | Pedidos, unidades y último producto por cliente |

La analítica agrupa por nombre de producto, así que también cuenta productos especiales que no están en el catálogo.

## Cómo levantarlo en local

Necesitas Docker Desktop, Java 17 o superior, Maven y Node.js con Angular CLI.

**1. RabbitMQ, Kafka y PostgreSQL**, desde la raíz del repo:

```bash
docker compose up -d
```

Esto levanta:

- Los dos nodos de RabbitMQ (`rabbitmq-1` en los puertos 5672/15672 y `rabbitmq-2` en 5673/15673), que forman el cluster solos con `rabbitmq.conf`. El panel queda en `http://localhost:15672`.
- Zookeeper (2181) y 3 brokers de Kafka (`kafka-1`, `kafka-2` y `kafka-3` en 9092, 9093 y 9094). Cada broker tiene dos listeners: uno interno (`kafka-N:29092`) para hablar entre ellos y con Kafka-UI, y otro (`localhost:909X`) para los microservicios que corren fuera de Docker.
- Kafka-UI en `http://localhost:8080`, para ver brokers, tópicos, mensajes y grupos de consumidores.
- PostgreSQL en el puerto 5432.

Para apagar todo sin perder los datos: `docker compose stop`.

**2. Microservicios**, cada uno en su propia terminal:

```bash
cd pedidos-backend && mvn spring-boot:run
cd catalogo-backend && mvn spring-boot:run
cd soporte-backend && mvn spring-boot:run
```

Conviene levantar primero Pedidos y Catálogo, porque crean los tópicos que leen los demás. Por defecto se conectan a la base local y a los brokers en `localhost`. Para usar otra base de datos, como RDS, se pueden definir las variables `DB_URL` y `DB_PASSWORD`, y para otros brokers, `KAFKA_SERVERS`.

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
- Restringir también los endpoints de administración de Kafka con un rol de administrador, y activar autenticación (SASL) y TLS en los brokers.
- Migrar Kafka a modo KRaft (sin Zookeeper), que es el modo por defecto desde Kafka 4.
