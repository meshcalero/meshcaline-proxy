package org.meshcaline.proxy.admin.controller;

import org.meshcaline.proxy.admin.model.ConfigurationEntryDTO;
import org.meshcaline.proxy.configuration.service.ConfigurationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ConfigurationService configurationService;

    public AdminController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/mappings")
    public Flux<ConfigurationEntryDTO.Out> listMappings() {
        return configurationService.listMappings().map(ConfigurationEntryDTO.Out::out);
    }

    @PostMapping("/mappings")
    public Mono<ConfigurationEntryDTO.Out> addMapping(
            @RequestBody ConfigurationEntryDTO.In entry,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after
    ) {
        //TODO: Handling for before and after
        return configurationService.addMapping(entry.in()).map(ConfigurationEntryDTO.Out::out);
    }

    @DeleteMapping("/mappings/{id}")
    public Mono<Void> deleteMapping(@PathVariable String id) {
        return configurationService.deleteMapping(id);
    }

    @PutMapping("/mappings/{id}")
    public Mono<ConfigurationEntryDTO.Out> updateMapping(@PathVariable String id, @RequestBody ConfigurationEntryDTO.In entry) {
        return configurationService.updateMapping(entry.in(id)).map(ConfigurationEntryDTO.Out::out);
    }

    @PostMapping("mappings/operations/move")
    public Mono<Void> moveMapping(
            @RequestParam String id,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after
    ) {
        //TODO
        return Mono.empty();
    }
}
