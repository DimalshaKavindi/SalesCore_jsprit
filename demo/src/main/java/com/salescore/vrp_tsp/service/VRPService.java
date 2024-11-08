package com.salescore.vrp_tsp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.salescore.vrp_tsp.model.SolutionResponse;
import com.salescore.vrp_tsp.model.VrpRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@org.springframework.stereotype.Service
public class VrpService {
    private GraphHopper graphHopper;

    public VrpService() {
        try {
            graphHopper = new GraphHopper();
            graphHopper.setGraphHopperLocation("target/routing-graph-cache");
            graphHopper.setOSMFile("src/main/resources/osm/cambodia-latest.osm.pbf");
            graphHopper.setProfiles(new Profile("car").setWeighting("fastest"));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper: " + e.getMessage(), e);
        }
    }

    public String solveVrp(VrpRequest vrpRequest) {
        final int WEIGHT_INDEX = 0;

        // Create vehicle types
        List<VehicleTypeImpl> vehicleTypes = new ArrayList<>();
        for (VrpRequest.VehicleType type : vrpRequest.getVehicleTypes()) {
            VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance(type.getTypeId())
                    .addCapacityDimension(WEIGHT_INDEX, type.getCapacity())
                    .build();
            vehicleTypes.add(vehicleType);
        }

        // Build vehicles
        List<VehicleImpl> vehicles = new ArrayList<>();
        for (VrpRequest.Vehicle vehicle : vrpRequest.getVehicles()) {
            VehicleTypeImpl type = vehicleTypes.stream()
                    .filter(t -> t.getTypeId().equals(vehicle.getTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid vehicle type: " + vehicle.getTypeId()));

            VehicleImpl vehicleImpl = VehicleImpl.Builder.newInstance(vehicle.getVehicleId())
                    .setStartLocation(Location.newInstance(vehicle.getStartAddress().getLon(), vehicle.getStartAddress().getLat()))
                    .setType(type)
                    .build();
            vehicles.add(vehicleImpl);
        }

        // Create services
        List<Service> services = new ArrayList<>();
        for (VrpRequest.VrpService service : vrpRequest.getServices()) {
            Service serviceImpl = Service.Builder.newInstance(service.getId())
                    .addSizeDimension(WEIGHT_INDEX, service.getSize())
                    .setLocation(Location.newInstance(service.getAddress().getLon(), service.getAddress().getLat()))
                    .build();
            services.add(serviceImpl);
        }

        // Build the VRP problem
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vehicles.forEach(vrpBuilder::addVehicle);
        services.forEach(vrpBuilder::addJob);
        VehicleRoutingProblem problem = vrpBuilder.build();

        // Run the algorithm
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // Create the solution response object
        SolutionResponse solutionResponse = new SolutionResponse();
        solutionResponse.solution = new SolutionResponse.Solution();
        solutionResponse.solution.costs = (int) bestSolution.getCost();
        solutionResponse.solution.distance = 0;
        solutionResponse.solution.time = 0;
        solutionResponse.solution.no_vehicles = bestSolution.getRoutes().size(); // count of active routes
        solutionResponse.solution.routes = new ArrayList<>();

        double totalDistance = 0;
        long totalTime = 0;

        for (VehicleRoute route : bestSolution.getRoutes()) {
            SolutionResponse.Route routeResponse = new SolutionResponse.Route();
            routeResponse.vehicle_id = route.getVehicle().getId();
            routeResponse.activities = new ArrayList<>();

            Location previousLocation = route.getStart().getLocation();
            double routeDistance = 0;
            long routeTime = 0;

            // Add start location
            SolutionResponse.Activity startActivity = new SolutionResponse.Activity();
            startActivity.type = "start";
            startActivity.location_id = "start-location"; // Specify a unique ID for start
            startActivity.lat = route.getStart().getLocation().getCoordinate().getY();
            startActivity.lon = route.getStart().getLocation().getCoordinate().getX();
            startActivity.distance = 0;
            routeResponse.activities.add(startActivity);

            // Process activities
            for (TourActivity activity : route.getActivities()) {
                double distance = calculateDistance(previousLocation, activity.getLocation());
                long time = calculateTime(previousLocation, activity.getLocation());
                routeDistance += distance;
                routeTime += time;
                totalDistance += distance;
                totalTime += time;

                SolutionResponse.Activity serviceActivity = new SolutionResponse.Activity();
                serviceActivity.type = "service";
                serviceActivity.location_id = activity.getName(); // Use original locationId from request
                serviceActivity.lat = activity.getLocation().getCoordinate().getY();
                serviceActivity.lon = activity.getLocation().getCoordinate().getX();
                serviceActivity.distance = distance;
                serviceActivity.duration = time / 1000; // Convert time from milliseconds to seconds
                routeResponse.activities.add(serviceActivity);

                previousLocation = activity.getLocation();
            }

            // Add end location
            Location endLocation = route.getEnd().getLocation();
            double endDistance = calculateDistance(previousLocation, endLocation);
            long endTime = calculateTime(previousLocation, endLocation);
            routeDistance += endDistance;
            routeTime += endTime;
            totalDistance += endDistance;
            totalTime += endTime;

            SolutionResponse.Activity endActivity = new SolutionResponse.Activity();
            endActivity.type = "end";
            endActivity.location_id = "end-location"; // Specify a unique ID for end
            endActivity.lat = endLocation.getCoordinate().getY();
            endActivity.lon = endLocation.getCoordinate().getX();
            endActivity.distance = endDistance;
            endActivity.duration = endTime / 1000; // Convert time from milliseconds to seconds
            routeResponse.activities.add(endActivity);

            routeResponse.distance = routeDistance;
            routeResponse.duration = routeTime / 1000; // Convert time from milliseconds to seconds
            solutionResponse.solution.routes.add(routeResponse);
        }

        solutionResponse.solution.distance = totalDistance;
        solutionResponse.solution.time = (int) totalTime / 1000; // Convert milliseconds to seconds

        // Convert the solutionResponse to JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(solutionResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON response: " + e.getMessage(), e);
        }
    }

    // Calculate distance using GraphHopper
    private double calculateDistance(Location start, Location end) {
        double startLat = start.getCoordinate().getY();
        double startLon = start.getCoordinate().getX();
        double endLat = end.getCoordinate().getY();
        double endLon = end.getCoordinate().getX();

        GHRequest request = new GHRequest(startLat, startLon, endLat, endLon).setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating distance: " + response.getErrors());
        }

        return response.getBest().getDistance();
    }

    // Calculate travel time using GraphHopper
    private long calculateTime(Location start, Location end) {
        double startLat = start.getCoordinate().getY();
        double startLon = start.getCoordinate().getX();
        double endLat = end.getCoordinate().getY();
        double endLon = end.getCoordinate().getX();

        GHRequest request = new GHRequest(startLat, startLon, endLat, endLon).setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating time: " + response.getErrors());
        }

        return response.getBest().getTime();
    }
}