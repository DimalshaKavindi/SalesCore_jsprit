package com.salescore.vrp_tsp.controller;

import com.salescore.vrp_tsp.model.TspRequest;
import com.salescore.vrp_tsp.service.TspServiceDistance;
import com.salescore.vrp_tsp.service.TspServiceDuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tsp")
public class TspController {

    private final TspServiceDistance tspService;
    private final TspServiceDuration tspServiceDuration;

    @Autowired
    public TspController(TspServiceDistance tspService, TspServiceDuration tspServiceDuration) {
        this.tspService = tspService;
        this.tspServiceDuration = tspServiceDuration;
    }

    @PostMapping("/solve")
    public ResponseEntity<String> solveTsp(@RequestParam String method, @RequestBody TspRequest tspRequest) {
        if ("distance".equalsIgnoreCase(method)) {
            String solution = tspService.solveTsp(tspRequest);
            return ResponseEntity.ok(solution);
        } else if ("duration".equalsIgnoreCase(method)) {
            String solution = tspServiceDuration.solveTspDuration(tspRequest);
            return ResponseEntity.ok(solution);
        } else {
            return ResponseEntity.badRequest().body("Invalid method. Use 'distance' or 'duration'.");
        }
    }
}