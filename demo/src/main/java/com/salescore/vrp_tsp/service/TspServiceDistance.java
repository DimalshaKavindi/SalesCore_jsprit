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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TspServiceDistance {
    private final GraphHopper graphHopper;

    public TspServiceDistance() {
        try {
            graphHopper = new GraphHopper();
            graphHopper.setGraphHopperLocation("target/routing-graph-cache-distance");
            String osmFilePath = "/app/osm/laos-latest.osm.pbf";
            graphHopper.setOSMFile(osmFilePath);
            graphHopper.setProfiles(new Profile("car").setWeighting("shortest"));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper: " + e.getMessage(), e);
        }
    }

    public String solveTsp(TspRequest tspRequest) {
        TspRequest.Vehicle vehicle = tspRequest.getVehicle();
        List<TspRequest.ServiceLocation> services = tspRequest.getServices();

        if (vehicle == null || vehicle.getStartAddress() == null) {
            throw new IllegalArgumentException("TSP requires a vehicle with a defined start location.");
        }

        // Create a list for ordered locations, starting with the vehicle's start address
        List<TspRequest.ServiceLocation.Address> orderedLocations = new ArrayList<>();
        TspRequest.ServiceLocation.Address vehicleStart = new TspRequest.ServiceLocation.Address(
                vehicle.getStartAddress().getLocationId(),
                vehicle.getStartAddress().getLocationName(),
                vehicle.getStartAddress().getLon(),
                vehicle.getStartAddress().getLat()
        );
        orderedLocations.add(vehicleStart);

        Map<TspRequest.ServiceLocation.Address, String> addressToIdMap = new HashMap<>();
        for (TspRequest.ServiceLocation service : services) {
            orderedLocations.add(service.getAddress());
            addressToIdMap.put(service.getAddress(), service.getId());
        }

        return solveTspWithNearestNeighbor(orderedLocations, vehicle.getVehicleId(),addressToIdMap);
    }

    private String solveTspWithNearestNeighbor(List<TspRequest.ServiceLocation.Address> orderedLocations, String vehicleId, Map<TspRequest.ServiceLocation.Address, String> addressToIdMap) {
        List<TspRequest.ServiceLocation.Address> remainingLocations = new ArrayList<>(orderedLocations);
        List<TspRequest.ServiceLocation.Address> finalOrder = new ArrayList<>();

        TspRequest.ServiceLocation.Address vehicleStart = remainingLocations.get(0);  // First element (vehicle start)
        finalOrder.add(vehicleStart);
        remainingLocations.remove(0);

        TspRequest.ServiceLocation.Address currentLocation = vehicleStart;
        while (!remainingLocations.isEmpty()) {
            TspRequest.ServiceLocation.Address nearest = findNearestLocation(currentLocation, remainingLocations);
            finalOrder.add(nearest);
            remainingLocations.remove(nearest);
            currentLocation = nearest;
        }

        // Add the final segment back to the start location (or designated endpoint)
        finalOrder.add(vehicleStart);

        double totalDistance = calculateTotalDistance(finalOrder);
        double totalDuration = calculateTotalDuration(finalOrder);

        return formatSolutionResponse(finalOrder, vehicleId, totalDistance, totalDuration, addressToIdMap);
    }

    private TspRequest.ServiceLocation.Address findNearestLocation(TspRequest.ServiceLocation.Address from, List<TspRequest.ServiceLocation.Address> locations) {
        TspRequest.ServiceLocation.Address nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (TspRequest.ServiceLocation.Address location : locations) {
            double distance = calculateDistance(from, location);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = location;
            }
        }
        return nearest;
    }

    private double calculateDistance(TspRequest.ServiceLocation.Address start, TspRequest.ServiceLocation.Address end) {
        GHRequest request = new GHRequest(start.getLat(), start.getLon(), end.getLat(), end.getLon()).setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating distance: " + response.getErrors());
        }

        return response.getBest().getDistance();
    }

    private double calculateTotalDistance(List<TspRequest.ServiceLocation.Address> locations) {
        double totalDistance = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            totalDistance += calculateDistance(locations.get(i - 1), locations.get(i));
        }
        return totalDistance;
    }

    private double calculateTotalDuration(List<TspRequest.ServiceLocation.Address> locations) {
        double totalDuration = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            totalDuration += calculateDuration(locations.get(i - 1), locations.get(i));
        }
        return totalDuration;
    }

    private double calculateDuration(TspRequest.ServiceLocation.Address start, TspRequest.ServiceLocation.Address end) {
        GHRequest request = new GHRequest(start.getLat(), start.getLon(), end.getLat(), end.getLon()).setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating duration: " + response.getErrors());
        }

        return response.getBest().getTime() / 1000.0; // Convert milliseconds to seconds
    }

    private String formatSolutionResponse(List<TspRequest.ServiceLocation.Address> finalOrder, String vehicleId, double totalDistance, double totalDuration, Map<TspRequest.ServiceLocation.Address, String> addressToIdMap) {
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
                new TSPSolutionResponse.Activity.Address(
                        startLocation.getLocationId(),
                        startLocation.getLocationId(),
                        startLocation.getLat(),
                        startLocation.getLon()),
                0.0,
                0.0
        );
        route.getActivities().add(startActivity);

        // Add "visit" activities for intermediate locations
        for (int i = 1; i < finalOrder.size() - 1; i++) { // Use finalOrder.size() - 1 to avoid the extra end point
            TspRequest.ServiceLocation.Address location = finalOrder.get(i);
            double segmentDistance = calculateDistance(previousLocation, location);
            double segmentDuration = calculateDuration(previousLocation, location);

            cumulativeDistance += segmentDistance;
            cumulativeDuration += segmentDuration;

            // Fetch correct id from the map
            String serviceLocationId = addressToIdMap.getOrDefault(location, "unknown");

            TSPSolutionResponse.Activity visitActivity = new TSPSolutionResponse.Activity(
                    "visit",
                    serviceLocationId,
                    new TSPSolutionResponse.Activity.Address(
                            location.getLocationId(),
                            location.getName(),
                            location.getLat(),
                            location.getLon()),
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

        TSPSolutionResponse.Activity endActivity = new TSPSolutionResponse.Activity(
                "end",
                "end-location",
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
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON response: " + e.getMessage(), e);
        }
    }

}
