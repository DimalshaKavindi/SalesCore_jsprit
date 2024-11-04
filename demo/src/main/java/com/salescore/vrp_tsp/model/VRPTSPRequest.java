package com.salescore.vrp_tsp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VRPTSPRequest {

    private List<Vehicle> vehicles;
    private List<VehicleType> vehicleTypes;
    private List<VrpService> services;
    private Configuration configuration;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vehicle {
        private String vehicleId; // Updated from vehicle_id
        private String typeId; // Updated from type_id
        private StartAddress startAddress;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StartAddress {
            private String locationId; // Updated from location_id
            private double lon;
            private double lat;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleType {
        private String typeId; // Updated from type_id
        private int capacity;
        private String profile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VrpService {
        private String id;
        private String name;
        private Address address;
        private int size;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Address {
            private String locationId; // Updated from location_id
            private double lon;
            private double lat;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Configuration {
        private Routing routing;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Routing {
            private boolean calcPoints; // Updated from calc_points
            private boolean considerTraffic; // Updated from consider_traffic
            private List<String> snapPreventions;
        }
    }
}