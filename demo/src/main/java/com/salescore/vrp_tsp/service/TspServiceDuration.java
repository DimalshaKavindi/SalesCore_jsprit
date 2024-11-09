package com.salescore.vrp_tsp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.salescore.vrp_tsp.model.TspRequest;
import com.salescore.vrp_tsp.model.TSPSolutionResponse;
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
            String osmFilePath = getClass().getClassLoader().getResource("osm/cambodia-latest.osm.pbf").getPath();
            graphHopper.setOSMFile(osmFilePath);
            graphHopper.setProfiles(new Profile("car").setWeighting("fastest").setTurnCosts(true));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper for duration: " + e.getMessage(), e);
        }
    }

    public String solveTspDuration(TspRequest tspRequest) {
        List<TspRequest.ServiceLocation> services = tspRequest.getServices();
        TspRequest.Vehicle vehicle = tspRequest.getVehicle();

        if (services.isEmpty() || vehicle == null || vehicle.getStartAddress() == null || vehicle.getStartAddress().getLocationId() == null) {
            throw new IllegalArgumentException("TSP requires at least one location and a valid vehicle start location with a location ID.");
        }

        // Add vehicle's start location as the first location in the route
        List<TspRequest.ServiceLocation.Address> orderedLocations = new ArrayList<>();
        TspRequest.Vehicle.StartAddress vehicleStart = vehicle.getStartAddress();
        orderedLocations.add(new TspRequest.ServiceLocation.Address(vehicleStart.getLocationId(), vehicleStart.getLon(), vehicleStart.getLat()));

        // Add service locations as waypoints
        services.forEach(service -> orderedLocations.add(service.getAddress()));

        // Pass services to solveTspWithTimeOptimization
        return solveTspWithTimeOptimization(orderedLocations, vehicle.getVehicleId(), services);
    }

    private String solveTspWithTimeOptimization(List<TspRequest.ServiceLocation.Address> orderedLocations, String vehicleId, List<TspRequest.ServiceLocation> services) {
        List<TspRequest.ServiceLocation.Address> remainingLocations = new ArrayList<>(orderedLocations);
        List<TspRequest.ServiceLocation.Address> finalOrder = new ArrayList<>();

        // Start with the vehicle start location
        TspRequest.ServiceLocation.Address vehicleStart = remainingLocations.get(0);
        finalOrder.add(vehicleStart);
        remainingLocations.remove(0);

        // Build the route by finding nearest neighbor based on travel duration
        TspRequest.ServiceLocation.Address currentLocation = vehicleStart;
        while (!remainingLocations.isEmpty()) {
            TspRequest.ServiceLocation.Address nearest = findNearestLocationByDuration(currentLocation, remainingLocations);
            finalOrder.add(nearest);
            remainingLocations.remove(nearest);
            currentLocation = nearest;
        }

        // Calculate total duration and distance including the last leg
        double totalDuration = calculateTotalDuration(finalOrder);
        double totalDistance = calculateTotalDistance(finalOrder);

        // Pass services to formatSolutionResponse
        return formatSolutionResponse(finalOrder, vehicleId, totalDistance, totalDuration, services);
    }

    private TspRequest.ServiceLocation.Address findNearestLocationByDuration(TspRequest.ServiceLocation.Address from, List<TspRequest.ServiceLocation.Address> locations) {
        TspRequest.ServiceLocation.Address nearest = null;
        double minDuration = Double.MAX_VALUE;
        for (TspRequest.ServiceLocation.Address location : locations) {
            double duration = calculateDuration(from, location);
            if (duration < minDuration) {
                minDuration = duration;
                nearest = location;
            }
        }
        return nearest;
    }

    private double calculateDuration(TspRequest.ServiceLocation.Address start, TspRequest.ServiceLocation.Address end) {
        GHRequest request = new GHRequest(start.getLat(), start.getLon(), end.getLat(), end.getLon())
                .setProfile("car")
                .setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating duration: " + response.getErrors());
        }

        return response.getBest().getTime() / 1000.0; // Convert milliseconds to seconds
    }

    private double calculateDistance(TspRequest.ServiceLocation.Address start, TspRequest.ServiceLocation.Address end) {
        GHRequest request = new GHRequest(start.getLat(), start.getLon(), end.getLat(), end.getLon())
                .setProfile("car")
                .setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating distance: " + response.getErrors());
        }

        return response.getBest().getDistance();
    }

    private double calculateTotalDuration(List<TspRequest.ServiceLocation.Address> locations) {
        double totalDuration = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            totalDuration += calculateDuration(locations.get(i - 1), locations.get(i));
        }
        // Include the final leg from the last location back to the start
        totalDuration += calculateDuration(locations.get(locations.size() - 1), locations.get(0)); // Last location to start
        return totalDuration;
    }

    private double calculateTotalDistance(List<TspRequest.ServiceLocation.Address> locations) {
        double totalDistance = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            totalDistance += calculateDistance(locations.get(i - 1), locations.get(i));
        }
        // Include the final leg from the last location back to the start
        totalDistance += calculateDistance(locations.get(locations.size() - 1), locations.get(0)); // Last location to start
        return totalDistance;
    }

    private String formatSolutionResponse(List<TspRequest.ServiceLocation.Address> finalOrder, String vehicleId, double totalDistance, double totalDuration, List<TspRequest.ServiceLocation> services) {
        TSPSolutionResponse response = new TSPSolutionResponse();
        TSPSolutionResponse.Solution solution = new TSPSolutionResponse.Solution(0, totalDistance, (int) totalDuration, 1, new ArrayList<>());
        TSPSolutionResponse.Route route = new TSPSolutionResponse.Route(vehicleId, totalDistance, totalDuration, new ArrayList<>());

        TspRequest.ServiceLocation.Address startLocation = finalOrder.get(0);
        TspRequest.ServiceLocation.Address previousLocation = startLocation;
        double cumulativeDistance = 0.0;
        double cumulativeDuration = 0.0;

        // Add the "start" activity
        TSPSolutionResponse.Activity startActivity = new TSPSolutionResponse.Activity(
                "start",
                "start-location",
                new TSPSolutionResponse.Activity.Address(startLocation.getLocationId(), startLocation.getLocationId(), startLocation.getLat(), startLocation.getLon()),
                0.0,
                0.0
        );
        route.getActivities().add(startActivity);

        // Add "visit" activities for intermediate locations
        for (int i = 1; i < finalOrder.size(); i++) {
            TspRequest.ServiceLocation.Address location = finalOrder.get(i);
            double segmentDistance = calculateDistance(previousLocation, location);
            double segmentDuration = calculateDuration(previousLocation, location);

            cumulativeDistance += segmentDistance;
            cumulativeDuration += segmentDuration;

            // Get the id of the corresponding ServiceLocation from the original request
            String serviceLocationId = services.get(i - 1).getId(); // Adjust index to match services list
            String locationName = services.get(i-1).getName();

            TSPSolutionResponse.Activity visitActivity = new TSPSolutionResponse.Activity(
                    "visit",
                    serviceLocationId,  // Use the correct ServiceLocation id
                    new TSPSolutionResponse.Activity.Address(location.getLocationId(), locationName, location.getLat(), location.getLon()),
                    cumulativeDistance,
                    cumulativeDuration
            );
            route.getActivities().add(visitActivity);

            previousLocation = location;
        }

        // Add the final "end" activity (from last location to start location or other designated end point)
        TspRequest.ServiceLocation.Address endLocation = previousLocation; // Last visited location
        double finalSegmentDistance = calculateDistance(endLocation, startLocation); // Distance back to start location or to final destination
        double finalSegmentDuration = calculateDuration(endLocation, startLocation); // Duration back to start location or to final destination

        cumulativeDistance += finalSegmentDistance;
        cumulativeDuration += finalSegmentDuration;

        // Update totalDistance and totalDuration with the final segment
        totalDistance = cumulativeDistance;
        totalDuration = cumulativeDuration;

        TSPSolutionResponse.Activity endActivity = new TSPSolutionResponse.Activity(
                "end",
                "end-Location",
                new TSPSolutionResponse.Activity.Address("end", "end-location", startLocation.getLat(), startLocation.getLon()),
                cumulativeDistance,
                cumulativeDuration
        );
        route.getActivities().add(endActivity);

        solution.getRoutes().add(route);
        response.setSolution(solution);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT); // Enable pretty-printing
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing TSP solution: " + e.getMessage(), e);
        }
    }
}
