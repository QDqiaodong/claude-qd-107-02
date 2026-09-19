package com.archive.center.controller;

import com.archive.center.entity.Retrieval;
import com.archive.center.service.RetrievalService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RetrievalController {

    private final RetrievalService service;

    public RetrievalController(RetrievalService service) {
        this.service = service;
    }

    @GetMapping("/retrievals")
    public List<Retrieval> list(@RequestParam(required = false) Long archiveId,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String visitor) {
        return service.list(archiveId, status, visitor);
    }

    @PostMapping("/retrievals")
    public Retrieval create(@RequestBody Retrieval input) {
        return service.create(input);
    }

    @PostMapping("/retrievals/{id}/giveback")
    public Retrieval giveBack(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate) {
        return service.giveBack(id, returnDate);
    }
}
