package com.archive.center.controller;

import com.archive.center.entity.RoomCheck;
import com.archive.center.service.CheckService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CheckController {

    private final CheckService service;

    public CheckController(CheckService service) {
        this.service = service;
    }

    @GetMapping("/checks")
    public List<RoomCheck> list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkDate,
            @RequestParam(required = false) String result) {
        return service.list(roomId, checkDate, result);
    }

    @PostMapping("/checks")
    public RoomCheck create(@RequestBody RoomCheck input) {
        return service.create(input);
    }
}
