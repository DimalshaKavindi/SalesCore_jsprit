package com.salescore.vrp_tsp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VRPSolutionResponse {
    private Solution solution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Solution {
        private double costs;
        private double distance;
        public int time;
        private int noVehicles;
        private List<Route> routes;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Route {
            private String vehicleId;
            private double distance;
            public double duration;
            private List<Activity> activities;

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Activity {
                private String type; // "start", "service", or "end"
                private String id;
                private Address address;
                private double distance;
                public double duration;
                private int loadBefore;
                private int loadAfter;
                private long arriveTime;
                private long endTime;

            }

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Address {
                private String locationId;
                private String locationName;
                private double lat;
                private double lon;
            }
        }
    }
}
