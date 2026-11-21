package micro.soporte.soporte_backend.dto.kafka;

/**
 * Lag de una partición: cuántos eventos le faltan al grupo por leer.
 * lag = último offset escrito en la partición - offset confirmado por el grupo.
 */
public record LagParticion(String topic, int particion, Long offsetConfirmado, long ultimoOffset, Long lag) {
}
