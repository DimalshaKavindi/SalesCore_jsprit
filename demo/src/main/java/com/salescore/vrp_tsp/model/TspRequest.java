package com.salescore.vrp_tsp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.apache.commons.math3.analysis.function.Add;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TspRequest {
    private Vehicle vehicle;
    private List<ServiceLocation> services;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vehicle {
        private String vehicleId;
        private StartAddress startAddress;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StartAddress{
            private String locationId;
            private String locationName;
            private double lon;
            private double lat;
        }

    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLocation {
        private String id;
        private Address address;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Address {
            private String locationId;
            private String name;
            private double lon;
            private double lat;
        }
    }
}