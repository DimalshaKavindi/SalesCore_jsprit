package com.salescore.vrp_tsp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VrpRequest {

    private List<Vehicle> vehicles;
    private List<VehicleType> vehicleTypes;
    private List<VrpService> services;
    private Configuration configuration;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vehicle {
        private String vehicleId;
        private String typeId;
        private StartAddress startAddress;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StartAddress {
            private String locationId;
            private double lon;
            private double lat;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleType {
        private String typeId;
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
            private String locationId;
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
            private boolean calcPoints;
            private boolean considerTraffic;
            private List<String> snapPreventions;
        }
    }
}