package com.salescore.vrp_tsp.model;

import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
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
        private Long startTime; // Earliest start time for the vehicle
        private Long endTime;   // Latest arrival time for the vehicle

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
        private TimeWindow timewindow;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TimeWindow {
            private Long startTime; // Earliest service time
            private Long endTime;
        }
           // Latest service time

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
