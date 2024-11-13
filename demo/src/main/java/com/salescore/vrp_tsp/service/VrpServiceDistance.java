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
public class VrpServiceDistance {

    private GraphHopper graphHopper;

    public VrpServiceDistance() {
        try {
            graphHopper = new GraphHopper();
            graphHopper.setGraphHopperLocation("target/routing-graph-cache-dis");
            String osmFilePath = getClass().getClassLoader().getResource("osm/laos-latest.osm.pbf").getPath();
            graphHopper.setOSMFile(osmFilePath);
            graphHopper.setProfiles(new Profile("car").setWeighting("fastest"));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
            graphHopper.importOrLoad();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing GraphHopper: " + e.getMessage(), e);
        }
    }

    public String solveVrpByDistance(VrpRequest vrpRequest) {
        final int WEIGHT_INDEX = 0;

        boolean capacityProvided = vrpRequest.getVehicleTypes().stream().anyMatch(type -> type.getCapacity() > 0);

        // Create vehicle types with capacity check
        List<VehicleTypeImpl> vehicleTypes = new ArrayList<>();
        for (VrpRequest.VehicleType type : vrpRequest.getVehicleTypes()) {
            VehicleTypeImpl.Builder typeBuilder = VehicleTypeImpl.Builder.newInstance(type.getTypeId());
            if (capacityProvided) {
                typeBuilder.addCapacityDimension(WEIGHT_INDEX, type.getCapacity());
            }
            vehicleTypes.add(typeBuilder.build());
        }

        // Build vehicles
        List<VehicleImpl> vehicles = new ArrayList<>();
        for (VrpRequest.Vehicle vehicle : vrpRequest.getVehicles()) {
            VehicleTypeImpl type = vehicleTypes.stream()
                    .filter(t -> t.getTypeId().equals(vehicle.getTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid vehicle type: " + vehicle.getTypeId()));

            VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance(vehicle.getVehicleId())
                    .setStartLocation(Location.newInstance(vehicle.getStartAddress().getLon(), vehicle.getStartAddress().getLat()))
                    .setType(type);

            vehicles.add(vehicleBuilder.build());
        }

        // Create services with optional capacity settings
        List<Service> services = new ArrayList<>();
        for (VrpRequest.VrpService service : vrpRequest.getServices()) {
            Service.Builder serviceBuilder = Service.Builder.newInstance(service.getId())
                    .setLocation(Location.newInstance(service.getAddress().getLon(), service.getAddress().getLat()));

            if (capacityProvided) {
                serviceBuilder.addSizeDimension(WEIGHT_INDEX, service.getSize());
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
        solution.setNoVehicles(bestSolution.getRoutes().size());
        solution.setRoutes(new ArrayList<>());

        double totalDistance = 0;

        for (VehicleRoute route : bestSolution.getRoutes()) {
            String vehicleId = route.getVehicle().getId();
            VrpRequest.Vehicle vehicle = vrpRequest.getVehicles().stream()
                    .filter(v -> v.getVehicleId().equals(vehicleId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

            VRPSolutionResponse.Solution.Route routeResponse = new VRPSolutionResponse.Solution.Route();
            routeResponse.setVehicleId(vehicleId);
            routeResponse.setActivities(new ArrayList<>());

            Location previousLocation = route.getStart().getLocation();
            double routeDistance = 0;

            for (TourActivity activity : route.getActivities()) {
                double distance = calculateDistance(previousLocation, activity.getLocation());
                routeDistance += distance;
                totalDistance += distance;

                VrpRequest.VrpService serviceRequest = vrpRequest.getServices().stream()
                        .filter(service -> service.getAddress().getLon() == activity.getLocation().getCoordinate().getX() &&
                                service.getAddress().getLat() == activity.getLocation().getCoordinate().getY())
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Service not found"));

                VRPSolutionResponse.Solution.Route.Activity serviceActivity = new VRPSolutionResponse.Solution.Route.Activity();
                serviceActivity.setType("visit");
                serviceActivity.setId(serviceRequest.getId());
                serviceActivity.setDistance(distance);
                routeResponse.getActivities().add(serviceActivity);

                previousLocation = activity.getLocation();
            }

            routeResponse.setDistance(routeDistance);
            solution.getRoutes().add(routeResponse);
        }

        solution.setDistance(totalDistance);
        vrpSolutionResponse.setSolution(solution);

        // Convert the vrpSolutionResponse to JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vrpSolutionResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON response: " + e.getMessage(), e);
        }
    }

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
}
