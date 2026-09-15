package micro.catalogo.catalogo_backend.controller;

import micro.catalogo.catalogo_backend.model.PlanHosting;
import micro.catalogo.catalogo_backend.repository.PlanHostingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo")
public class PlanHostingController {

    private final PlanHostingRepository planHostingRepository;

    public PlanHostingController(PlanHostingRepository planHostingRepository) {
        this.planHostingRepository = planHostingRepository;
    }

    @GetMapping
    public List<PlanHosting> listar() {
        return planHostingRepository.findAll();
    }

    @GetMapping("/{id}")
    public PlanHosting obtener(@PathVariable Long id) {
        return planHostingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));
    }

    @PreAuthorize("hasAuthority('SCOPE_Catalogo.Manage')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanHosting crear(@RequestBody PlanHostingRequest request) {
        PlanHosting plan = new PlanHosting(
                request.nombre(),
                request.tipoServidor(),
                request.ram(),
                request.slots(),
                request.precio(),
                request.disponible()
        );
        return planHostingRepository.save(plan);
    }

    @PreAuthorize("hasAuthority('SCOPE_Catalogo.Manage')")
    @PutMapping("/{id}")
    public PlanHosting actualizar(@PathVariable Long id, @RequestBody PlanHostingRequest request) {
        PlanHosting plan = planHostingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));
        plan.setNombre(request.nombre());
        plan.setTipoServidor(request.tipoServidor());
        plan.setRam(request.ram());
        plan.setSlots(request.slots());
        plan.setPrecio(request.precio());
        plan.setDisponible(request.disponible());
        return planHostingRepository.save(plan);
    }

    @PreAuthorize("hasAuthority('SCOPE_Catalogo.Manage')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        planHostingRepository.deleteById(id);
    }
}
