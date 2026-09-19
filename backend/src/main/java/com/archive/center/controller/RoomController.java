package com.archive.center.controller;

import com.archive.center.entity.Room;
import com.archive.center.service.RoomService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @GetMapping("/rooms")
    public List<Room> list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String keyword) {
        return service.list(status, keyword);
    }

    @PostMapping("/rooms")
    public Room create(@RequestBody Room input) {
        return service.create(input);
    }

    @PutMapping("/rooms/{id}")
    public Room update(@PathVariable Long id, @RequestBody Room input) {
        return service.update(id, input);
    }
}
