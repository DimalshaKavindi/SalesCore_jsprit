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
import com.salescore.vrp_tsp.model.VRPSolutionResponse;
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
            String osmFilePath = getClass().getClassLoader().getResource("osm/cambodia-latest.osm.pbf").getPath();
            graphHopper.setOSMFile(osmFilePath);
            graphHopper.setProfiles(new Profile("car").setWeighting("fastest"));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper: " + e.getMessage(), e);
        }
    }

    public String solveVrp(VrpRequest vrpRequest) {
        final int WEIGHT_INDEX = 0;

        boolean capacityProvided = vrpRequest.getVehicleTypes().stream().anyMatch(type -> type.getCapacity() > 0);
        boolean timeWindowProvided = vrpRequest.getServices().stream().anyMatch(service -> service.getStartTime() != null);

        // Create vehicle types with capacity check
        List<VehicleTypeImpl> vehicleTypes = new ArrayList<>();
        for (VrpRequest.VehicleType type : vrpRequest.getVehicleTypes()) {
            VehicleTypeImpl.Builder typeBuilder = VehicleTypeImpl.Builder.newInstance(type.getTypeId());
            if (capacityProvided) {
                typeBuilder.addCapacityDimension(WEIGHT_INDEX, type.getCapacity());
            }
            vehicleTypes.add(typeBuilder.build());
        }

        // Build vehicles with optional time windows
        List<VehicleImpl> vehicles = new ArrayList<>();
        for (VrpRequest.Vehicle vehicle : vrpRequest.getVehicles()) {
            VehicleTypeImpl type = vehicleTypes.stream()
                    .filter(t -> t.getTypeId().equals(vehicle.getTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid vehicle type: " + vehicle.getTypeId()));

            VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance(vehicle.getVehicleId())
                    .setStartLocation(Location.newInstance(vehicle.getStartAddress().getLon(), vehicle.getStartAddress().getLat()))
                    .setType(type);

            // If vehicle time windows are provided, set them here
            if (vehicle.getStartTime() != null && vehicle.getEndTime() != null) {
                vehicleBuilder.setEarliestStart(vehicle.getStartTime())
                        .setLatestArrival(vehicle.getEndTime());
            }

            vehicles.add(vehicleBuilder.build());
        }

        // Create services with optional capacity and time window settings
        List<Service> services = new ArrayList<>();
        for (VrpRequest.VrpService service : vrpRequest.getServices()) {
            Service.Builder serviceBuilder = Service.Builder.newInstance(service.getId())
                    .setLocation(Location.newInstance(service.getAddress().getLon(), service.getAddress().getLat()));

            if (capacityProvided) {
                serviceBuilder.addSizeDimension(WEIGHT_INDEX, service.getSize());
            }

            if (timeWindowProvided) {
                serviceBuilder.setTimeWindow(
                        new com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow(
                                service.getStartTime(),
                                service.getEndTime()
                        ));
            }
            services.add(serviceBuilder.build());
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

        // Create the VRPSolutionResponse object
        VRPSolutionResponse vrpSolutionResponse = new VRPSolutionResponse();
        VRPSolutionResponse.Solution solution = new VRPSolutionResponse.Solution();
        solution.setCosts(bestSolution.getCost());
        solution.setDistance(0);
        solution.setTime(0);
        solution.setNoVehicles(bestSolution.getRoutes().size()); // count of active routes
        solution.setRoutes(new ArrayList<>());

        double totalDistance = 0;
        long totalTime = 0;

        for (VehicleRoute route : bestSolution.getRoutes()) {
            // Retrieve the vehicle associated with this route
            long accumulatedTime = 0;
            String vehicleId = route.getVehicle().getId();
            VrpRequest.Vehicle vehicle = vrpRequest.getVehicles().stream()
                    .filter(v -> v.getVehicleId().equals(vehicleId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

            VRPSolutionResponse.Solution.Route routeResponse = new VRPSolutionResponse.Solution.Route();
            routeResponse.setVehicleId(vehicleId);
            routeResponse.setActivities(new ArrayList<>());

            int loadBefore = 0;
            Location previousLocation = route.getStart().getLocation();
            double routeDistance = 0;
            long routeTime = 0;

            // Add start location using the vehicle's startAddress locationId
            VRPSolutionResponse.Solution.Route.Activity startActivity = new VRPSolutionResponse.Solution.Route.Activity();
            startActivity.setType("start");
            startActivity.setId("start-location");  // Use the locationId from startAddress
            startActivity.setAddress(new VRPSolutionResponse.Solution.Route.Address(
                    "start",
                    vehicle.getStartAddress().getLocationId(),  // Use locationId from startAddress
                    previousLocation.getCoordinate().getY(),
                    previousLocation.getCoordinate().getX()
            ));
            startActivity.setDistance(0);
            startActivity.setDuration(0);
            startActivity.setLoadBefore(loadBefore);
            startActivity.setLoadAfter(loadBefore);
            routeResponse.getActivities().add(startActivity);


            // Process activities
            for (TourActivity activity : route.getActivities()) {
                double distance = calculateDistance(previousLocation, activity.getLocation());
                long travelTime = calculateTime(previousLocation, activity.getLocation());
                routeDistance += distance;
                routeTime += travelTime;
                totalDistance += distance;
                totalTime += travelTime;

                VrpRequest.VrpService serviceRequest = vrpRequest.getServices().stream()
                        .filter(service -> service.getAddress().getLon() == activity.getLocation().getCoordinate().getX() &&
                                service.getAddress().getLat() == activity.getLocation().getCoordinate().getY())
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Service not found"));

                int serviceSize = serviceRequest.getSize();
                VRPSolutionResponse.Solution.Route.Activity serviceActivity = new VRPSolutionResponse.Solution.Route.Activity();
                serviceActivity.setType("visit");
                serviceActivity.setId(serviceRequest.getId());
                serviceActivity.setAddress(new VRPSolutionResponse.Solution.Route.Address(
                        serviceRequest.getAddress().getLocationId(),
                        serviceRequest.getName(),
                        activity.getLocation().getCoordinate().getY(),
                        activity.getLocation().getCoordinate().getX()));
                serviceActivity.setDistance(distance);
                serviceActivity.setDuration(travelTime / 1000); // Convert milliseconds to seconds
                serviceActivity.setLoadBefore(loadBefore);
                serviceActivity.setLoadAfter(loadBefore + serviceSize);

                // Set arrival and end times for the service activity
                serviceActivity.setArriveTime(accumulatedTime + travelTime / 1000); // Arrival in seconds
                accumulatedTime += travelTime / 1000; // Update accumulated time for travel
                serviceActivity.setEndTime((long) (accumulatedTime + serviceActivity.getDuration())); // End time after service

                loadBefore += serviceSize;
                routeResponse.getActivities().add(serviceActivity);
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

            VRPSolutionResponse.Solution.Route.Activity endActivity = new VRPSolutionResponse.Solution.Route.Activity();
            endActivity.setType("end");
            endActivity.setId("end-location");
            endActivity.setAddress(new VRPSolutionResponse.Solution.Route.Address(
                    "end",
                    vehicle.getStartAddress().getLocationId(),  // Use locationId from startAddress
                    endLocation.getCoordinate().getY(),
                    endLocation.getCoordinate().getX()));
            endActivity.setDistance(endDistance);
            endActivity.setDuration(endTime / 1000); // Convert time from milliseconds to seconds
            endActivity.setLoadBefore(loadBefore);
            endActivity.setLoadAfter(loadBefore);
            routeResponse.getActivities().add(endActivity);

            routeResponse.setDistance(routeDistance);
            routeResponse.setDuration(routeTime / 1000); // Convert time from milliseconds to seconds
            solution.getRoutes().add(routeResponse);
        }

        solution.setDistance(totalDistance);
        solution.setTime((int) totalTime / 1000); // Convert milliseconds to seconds
        vrpSolutionResponse.setSolution(solution);

        // Convert the vrpSolutionResponse to JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vrpSolutionResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON response: " + e.getMessage(), e);
        }
    }

    // Calculate distance using GraphHopper
    private double calculateDistance(Location start, Location end) {
        GHRequest request = new GHRequest(start.getCoordinate().getY(), start.getCoordinate().getX(),
                end.getCoordinate().getY(), end.getCoordinate().getX())
                .setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating distance: " + response.getErrors());
        }

        return response.getBest().getDistance();
    }

    // Calculate travel time using GraphHopper
    private long calculateTime(Location start, Location end) {
        GHRequest request = new GHRequest(start.getCoordinate().getY(), start.getCoordinate().getX(),
                end.getCoordinate().getY(), end.getCoordinate().getX())
                .setProfile("car").setLocale("en");
        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Error calculating time: " + response.getErrors());
        }

        return response.getBest().getTime();
    }
}
