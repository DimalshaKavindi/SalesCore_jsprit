package com.salescore.vrp_tsp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TspRequest {
    private List<Location> locations;
    private Vehicle vehicle;  // Add vehicle information

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String locationId;
        private double lon;
        private double lat;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vehicle {
        private String vehicleId;
        private Location location;

    }
}