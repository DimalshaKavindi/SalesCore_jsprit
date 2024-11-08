package com.salescore.vrp_tsp.model;

import java.util.List;

public class SolutionResponse {
    public Solution solution;

    public static class Solution {
        public int costs;
        public double distance;
        public int time;
        public int no_vehicles;
        public List<Route> routes;
    }

    public static class Route {
        public String vehicle_id;
        public List<Activity> activities;
        public double distance;
        public double duration;
    }

    public static class Activity {
        public String type;
        public String location_id;
        public double lat;
        public double lon;
        public double distance;

        public double duration;
    }
}