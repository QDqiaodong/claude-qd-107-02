package com.archive.center.controller;

import com.archive.center.entity.Archive;
import com.archive.center.service.ArchiveService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ArchiveController {

    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    @GetMapping("/archives")
    public List<Archive> list(@RequestParam(required = false) Long roomId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer year,
                              @RequestParam(required = false) String keyword) {
        return service.list(roomId, status, year, keyword);
    }

    @PostMapping("/archives")
    public Archive create(@RequestBody Archive input) {
        return service.create(input);
    }

    @PutMapping("/archives/{id}")
    public Archive update(@PathVariable Long id, @RequestBody Archive input) {
        return service.update(id, input);
    }
}
