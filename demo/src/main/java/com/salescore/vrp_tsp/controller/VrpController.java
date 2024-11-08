package com.salescore.vrp_tsp.controller;

import com.salescore.vrp_tsp.model.VrpRequest;
import com.salescore.vrp_tsp.service.VrpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vrp")
public class VrpController {

    private final VrpService vrpService;

    @Autowired
    public VrpController(VrpService vrpService) {
        this.vrpService = vrpService;
    }

    @PostMapping("/solve")
    public ResponseEntity<String> solveVrp(@RequestBody VrpRequest vrpRequest) {
        // Pass the request to the service to handle VRP solving
        String solution = vrpService.solveVrp(vrpRequest);
        return ResponseEntity.ok(solution);
    }
}