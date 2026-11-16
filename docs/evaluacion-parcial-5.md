# Evaluación Parcial N°5 — DSY1107 Desarrollo Cloud Native I

Encargo en parejas · 12% · Plazo: 2 semanas (encargo + presentación)

## Descripción

- Evalúa la capacidad de integrar sistemas de streaming y analítica.
- Se observa el uso correcto de tópicos, la publicación y consumo de eventos, la justificación del diseño, y cómo Kafka complementa la arquitectura existente **sin afectar las integraciones previas con RabbitMQ**.
- El encargo corresponde al código de los componentes frontend y backend del sistema, entregado vía GitHub.

## Instrucciones específicas

- Los componentes frontend y backend se entregan enviando el código fuente al docente.
- El backend debe compilar, seguir buenas prácticas y responder a pruebas básicas.
- El frontend debe estar completo, modular, sin errores de compilación y con vistas funcionales.
- La integración del backend con la base de datos cloud debe estar configurada con entidades, repositorios y propiedades de conexión.
- El backend debe incluir filtros que validen el JWT recibido desde el IDaaS.
- El frontend debe implementar el login con IDaaS y usar el JWT en las llamadas al backend.
- Solo se sube a los repositorios lo que corresponde a cada tecnología, con los `.gitignore` necesarios.
- Los microservicios deben tener productores y consumidores conectados a colas RabbitMQ.
- Los microservicios deben manejar mensajes no entregados enviándolos a DLQ y registrándolos en logs.
- El microservicio administrador de RabbitMQ debe permitir crear y eliminar colas, exchanges y bindings.
- **Los microservicios deben tener productores y consumidores conectados a tópicos Kafka.**
- **El microservicio administrador de Kafka debe permitir crear, modificar y eliminar tópicos y configuraciones.**
- docker-compose funcional para un clúster de RabbitMQ.
- **docker-compose funcional para una arquitectura Kafka con Zookeeper y brokers.**
- **docker-compose funcional para Kafka-UI, que permita administrar brokers y tópicos.**

Formato: backend en varios microservicios Java con Spring Boot, frontend en Angular, entregados como enlaces a GitHub (AVA + copia al correo del docente).

## Pauta de evaluación

Niveles: Muy bueno 100% · Bueno 80% · Aceptable 60% · Incipiente 30% · No logrado 0%.

| # | Indicador | Peso | Para el 100% |
|---|---|---|---|
| 1 | Configuración completa del cliente Kafka: conexión a los brokers de Docker Compose y propiedades para publicar y consumir en tópicos con particiones y réplicas definidas | 12% | Bootstrap servers, serializers, consumer groups y propiedades avanzadas. Publica y consume en **ambos tópicos** sin cambios adicionales |
| 2 | Productores y consumidores que interactúan con los **dos tópicos**, con estructuras tipadas (DTO) | 12% | DTO estructurados, lógica clara, interacción correcta con ambos tópicos según la arquitectura |
| 3 | Administrador: endpoints o clases para crear, listar o mantener tópicos y configuraciones básicas | 6% | Operaciones completas para crear, listar y administrar tópicos y configuraciones, como una capa de gestión sólida |
| 4 | Productores Kafka que envían eventos a los tópicos con DTO y serializers adecuados | 14% | DTO claros, serializers adecuados y lógica alineada al diseño |
| 5 | Consumidores Kafka con `@KafkaListener` o equivalente, con la lógica de procesamiento separada de la infraestructura de mensajería | 14% | Procesan los eventos respetando los flujos de negocio, con clara separación entre dominio y mensajería |
| 6 | Manejo de errores al procesar eventos: logging, validación de payloads o control de reintentos | 12% | Validaciones, logging contextual y control de reintentos, sin efectos adversos en el sistema |
| 7 | Parámetros de administración de tópicos: retención, cleanup policy, particiones y factor de replicación ajustados a cada flujo | 12% | Correctamente configurados y alineados con los requerimientos de cada flujo de datos |
| 8 | Consumer groups para balancear carga o asegurar procesamiento resiliente, compartiendo o dividiendo responsabilidades entre microservicios | 12% | Permiten balanceo, resiliencia y distribución según las necesidades del sistema |
| 9 | Administrador o scripts para crear, actualizar o inspeccionar tópicos y grupos de consumidores | 6% | Operaciones completas y replicables para tópicos y consumer groups |
