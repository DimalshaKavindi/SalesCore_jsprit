package com.salescore.vrp_tsp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.salescore.vrp_tsp.model.TspRequest;
import com.salescore.vrp_tsp.model.SolutionResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TspServiceDuration {
    private final GraphHopper graphHopper;

    public TspServiceDuration() {
        try {
            graphHopper = new GraphHopper();
            graphHopper.setGraphHopperLocation("target/routing-graph-cache-duration");
            graphHopper.setOSMFile("src/main/resources/osm/cambodia-latest.osm.pbf");
            graphHopper.setProfiles(new Profile("car").setWeighting("fastest").setTurnCosts(true));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper for duration: " + e.getMessage(), e);
        }
    }

    public String solveTspDuration(TspRequest tspRequest) {
        List<TspRequest.Location> locations = tspRequest.getLocations();
        TspRequest.Vehicle vehicle = tspRequest.getVehicle();

        if (locations.size() < 1 || vehicle == null) {
            throw new IllegalArgumentException("TSP requires at least one location and a vehicle with a start and end location.");
        }

        // Add vehicle's start location as the first location in the route
        List<TspRequest.Location> orderedLocations = new ArrayList<>();
        TspRequest.Location vehicleStart = new TspRequest.Location("vehicleStart", vehicle.getLocation().getLon(), vehicle.getLocation().getLat());
        orderedLocations.add(vehicleStart);

        // Use the locations list as the rest of the waypoints
        orderedLocations.addAll(locations);

        // Add vehicle's end location as the last location
        TspRequest.Location vehicleEnd = new TspRequest.Location("vehicleEnd", vehicle.getLocation().getLon(), vehicle.getLocation().getLat());
        orderedLocations.add(vehicleEnd);

        return solveTspWithTimeOptimization(orderedLocations, vehicle.getVehicleId());
    }

    private String solveTspWithTimeOptimization(List<TspRequest.Location> orderedLocations, String vehicleId) {
        List<TspRequest.Location> remainingLocations = new ArrayList<>(orderedLocations);
        List<TspRequest.Location> finalOrder = new ArrayList<>();

        // Set up the vehicle start and end locations separately
        TspRequest.Location vehicleStart = remainingLocations.get(0); // First element (vehicle start)
        TspRequest.Location vehicleEnd = remainingLocations.remove(remainingLocations.size() - 1); // Last element (vehicle end)

        // Start with the vehicle start location
        finalOrder.add(vehicleStart);
        remainingLocations.remove(0);

        // Build the route by finding nearest neighbor based on travel duration
        TspRequest.Location currentLocation = vehicleStart;
        while (!remainingLocations.isEmpty()) {
            TspRequest.Location nearest = findNearestLocationByDuration(currentLocation, remainingLocations);
            finalOrder.add(nearest);
            remainingLocations.remove(nearest);
            currentLocation = nearest;
        }

        // Add the vehicle end location to complete the route
        finalOrder.add(vehicleEnd);

        // Calculate the total travel duration
        double totalDuration = calculateTotalDuration(finalOrder);

        return formatSolutionResponse(finalOrder, vehicleId, totalDuration);
    }

    private TspRequest.Location findNearestLocationByDuration(TspRequest.Location from, List<TspRequest.Location> locations) {
        TspRequest.Location nearest = null;
        double minDuration = Double.MAX_VALUE;
        for (TspRequest.Location location : locations) {
            double duration = calculateDuration(from, location);
            if (duration < minDuration) {
                minDuration = duration;
                nearest = location;
            }
        }
        return nearest;
    }

    private double calculateDuration(TspRequest.Location start, TspRequest.Location end) {
        GHRequest request = new GHRequest(start.getLat(), start.getLon(), end.getLat(), end.getLon())
                .setProfile("car")
                .setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating duration: " + response.getErrors());
        }

        return response.getBest().getTime() / 1000.0; // Duration in seconds
    }

    private double calculateTotalDuration(List<TspRequest.Location> locations) {
        double totalDuration = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            totalDuration += calculateDuration(locations.get(i - 1), locations.get(i));
        }
        return totalDuration;
    }

    private String formatSolutionResponse(List<TspRequest.Location> finalOrder, String vehicleId, double totalDuration) {
        SolutionResponse response = new SolutionResponse();
        response.solution = new SolutionResponse.Solution();
        response.solution.routes = new ArrayList<>();
        response.solution.distance = 0; // Optional distance calculation
        response.solution.time = (int) totalDuration; // Total travel time
        response.solution.no_vehicles = 1; // Assuming one vehicle is used
        response.solution.costs = 0; // Add cost calculations here if needed

        SolutionResponse.Route route = new SolutionResponse.Route();
        route.vehicle_id = vehicleId;
        route.activities = new ArrayList<>();

        TspRequest.Location previousLocation = null;
        double cumulativeDuration = 0.0;

        for (TspRequest.Location location : finalOrder) {
            SolutionResponse.Activity activity = new SolutionResponse.Activity();
            activity.type = "visit";
            activity.location_id = location.getLocationId();
            activity.lat = location.getLat();
            activity.lon = location.getLon();

            if (previousLocation != null) {
                double segmentDuration = calculateDuration(previousLocation, location);
                cumulativeDuration += segmentDuration;
                activity.duration = segmentDuration;
            } else {
                activity.duration = 0.0;
            }

            route.activities.add(activity);
            previousLocation = location;
        }

        route.duration = cumulativeDuration;
        response.solution.routes.add(route);

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON response: " + e.getMessage(), e);
        }
    }
}