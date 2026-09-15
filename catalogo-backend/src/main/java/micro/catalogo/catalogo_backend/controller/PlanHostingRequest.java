package micro.catalogo.catalogo_backend.controller;

public record PlanHostingRequest(
        String nombre,
        String tipoServidor,
        Integer ram,
        Integer slots,
        Double precio,
        Boolean disponible
) {
}
