# ============================================================
# Administración de Kafka desde la línea de comandos
# ============================================================
# Usa las herramientas que vienen dentro del contenedor kafka-1,
# así que solo necesita Docker (no hace falta token ni Java).
#
# Ejemplos, desde la raíz del repo:
#   .\scripts\kafka-admin.ps1 topics
#   .\scripts\kafka-admin.ps1 describe pedidos.eventos
#   .\scripts\kafka-admin.ps1 crear prueba.topic 3 3
#   .\scripts\kafka-admin.ps1 retencion prueba.topic 86400000
#   .\scripts\kafka-admin.ps1 particiones prueba.topic 6
#   .\scripts\kafka-admin.ps1 eliminar prueba.topic
#   .\scripts\kafka-admin.ps1 grupos
#   .\scripts\kafka-admin.ps1 grupo soporte-analitica
#   .\scripts\kafka-admin.ps1 leer pedidos.eventos.DLT
# ============================================================

param(
    [Parameter(Position = 0)] [string] $Accion = "ayuda",
    [Parameter(Position = 1)] [string] $Nombre,
    [Parameter(Position = 2)] [string] $Valor,
    [Parameter(Position = 3)] [string] $Replicas = "3"
)

$Contenedor = "kafka-1"
$Broker = "kafka-1:29092"

function Kafka {
    param([string] $Herramienta, [string[]] $Argumentos)
    docker exec $Contenedor $Herramienta --bootstrap-server $Broker @Argumentos
}

function Requiere([string] $Dato, [string] $Descripcion) {
    if (-not $Dato) {
        Write-Host "Falta $Descripcion" -ForegroundColor Red
        exit 1
    }
}

switch ($Accion) {
    "topics" {
        Kafka "kafka-topics" @("--list")
    }
    "describe" {
        Requiere $Nombre "el nombre del tópico"
        Kafka "kafka-topics" @("--describe", "--topic", $Nombre)
        Kafka "kafka-configs" @("--describe", "--entity-type", "topics", "--entity-name", $Nombre)
    }
    "crear" {
        Requiere $Nombre "el nombre del tópico"
        $Particiones = if ($Valor) { $Valor } else { "3" }
        Kafka "kafka-topics" @("--create", "--topic", $Nombre, "--partitions", $Particiones,
            "--replication-factor", $Replicas, "--config", "min.insync.replicas=2")
    }
    "retencion" {
        Requiere $Nombre "el nombre del tópico"
        Requiere $Valor "la retención en milisegundos"
        Kafka "kafka-configs" @("--alter", "--entity-type", "topics", "--entity-name", $Nombre,
            "--add-config", "retention.ms=$Valor")
    }
    "particiones" {
        Requiere $Nombre "el nombre del tópico"
        Requiere $Valor "el nuevo total de particiones"
        Kafka "kafka-topics" @("--alter", "--topic", $Nombre, "--partitions", $Valor)
    }
    "eliminar" {
        Requiere $Nombre "el nombre del tópico"
        $Protegidos = @("pedidos.eventos", "pedidos.eventos.DLT", "catalogo.planes")
        if ($Protegidos -contains $Nombre -or $Nombre.StartsWith("_")) {
            Write-Host "'$Nombre' es un tópico del sistema y no se elimina" -ForegroundColor Red
            exit 1
        }
        Kafka "kafka-topics" @("--delete", "--topic", $Nombre)
    }
    "grupos" {
        Kafka "kafka-consumer-groups" @("--list")
    }
    "grupo" {
        Requiere $Nombre "el nombre del grupo"
        # Offsets y lag por partición
        Kafka "kafka-consumer-groups" @("--describe", "--group", $Nombre)
        # Miembros del grupo y cuántas particiones tiene cada uno
        Kafka "kafka-consumer-groups" @("--describe", "--group", $Nombre, "--members")
    }
    "leer" {
        Requiere $Nombre "el nombre del tópico"
        # Muestra los mensajes desde el inicio con su partición, offset y key; sale tras 5 s sin mensajes nuevos
        Kafka "kafka-console-consumer" @("--topic", $Nombre, "--from-beginning", "--timeout-ms", "5000",
            "--property", "print.partition=true", "--property", "print.offset=true", "--property", "print.key=true")
    }
    default {
        Write-Host "Acciones: topics, describe, crear, retencion, particiones, eliminar, grupos, grupo, leer"
        Write-Host "Ejemplo: .\scripts\kafka-admin.ps1 grupo soporte-analitica"
    }
}
