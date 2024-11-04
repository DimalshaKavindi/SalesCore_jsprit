package com.salescore.vrp_tsp.controller;

import com.salescore.vrp_tsp.model.VRPTSPRequest;
import org.springframework.http.ResponseEntity;
import com.salescore.vrp_tsp.service.TSPService;
import com.salescore.vrp_tsp.service.VRPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/api/optimize")
public class VRPTSPController {

    @Autowired
    private VRPService vrpService;
    private TSPService tspService;

    @PostMapping("/solvevrp")
    public ResponseEntity<String> solveVrp(@RequestBody VRPTSPRequest vrptspRequest) {
        // Pass the request to the service to handle VRP solving
        System.out.println("Received VRP Request: " + vrptspRequest);
        String solution = vrpService.solveVrp(vrptspRequest);
        return ResponseEntity.ok(solution);
    }

}


//@PostMapping("/solvetsp")
//    public ResponseEntity<String> solveTSP(@RequestBody VRPTSPRequest vrptspRequest) {
//        // Pass the request to the service to handle VRP solving
//        System.out.println("Received VRP Request: " + vrptspRequest);
//        String solution = vrpService.solveVrp(vrptspRequest);
//        return ResponseEntity.ok(solution);
//    }
