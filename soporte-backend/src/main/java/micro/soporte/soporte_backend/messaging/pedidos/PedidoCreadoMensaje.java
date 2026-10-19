package micro.soporte.soporte_backend.messaging.pedidos;

import micro.soporte.soporte_backend.config.RabbitMQConfig;
import micro.soporte.soporte_backend.exception.PedidoInvalidoException;

import java.util.regex.Pattern;

/**
 * Representa el evento "pedido creado" que publica Pedidos.
 * Formato del mensaje: pedidoId|clienteEmail|producto|cantidad
 */
public record PedidoCreadoMensaje(Long pedidoId, String clienteEmail, String producto, Integer cantidad) {

    private static final int CANTIDAD_CAMPOS = 4;

    /**
     * Convierte el texto del mensaje en un objeto.
     * Si el texto no cumple el formato, lanza PedidoInvalidoException.
     */
    public static PedidoCreadoMensaje desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new PedidoInvalidoException("El mensaje está vacío");
        }

        String[] campos = texto.split(Pattern.quote(RabbitMQConfig.SEPARADOR_MENSAJE), -1);
        if (campos.length != CANTIDAD_CAMPOS) {
            throw new PedidoInvalidoException("Formato inválido, se esperaban "
                    + CANTIDAD_CAMPOS + " campos y llegaron " + campos.length);
        }

        try {
            Long pedidoId = Long.parseLong(campos[0].trim());
            String clienteEmail = campos[1].trim();
            String producto = campos[2].trim();
            Integer cantidad = Integer.parseInt(campos[3].trim());

            if (clienteEmail.isEmpty() || producto.isEmpty()) {
                throw new PedidoInvalidoException("El cliente y el producto no pueden estar vacíos");
            }
            return new PedidoCreadoMensaje(pedidoId, clienteEmail, producto, cantidad);
        } catch (NumberFormatException e) {
            throw new PedidoInvalidoException("El id del pedido y la cantidad deben ser números");
        }
    }
}