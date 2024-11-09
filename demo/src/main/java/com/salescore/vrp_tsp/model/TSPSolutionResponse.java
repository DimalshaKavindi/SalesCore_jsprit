package com.salescore.vrp_tsp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TSPSolutionResponse {
    public Solution solution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Solution {
        public int costs;
        public double distance;
        public int time;
        public int no_vehicles;
        public List<Route> routes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Route {
        public String vehicle_id;
        public double distance;
        public double duration;
        public List<Activity> activities;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Activity {
        public String type;
        public String id;
        public Address address;
        public double distance;
        public double duration;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Address{
            private String locationId;
            private String locationName;
            private double lat;
            private double lon;
        }
    }
}