package com.salescore.vrp_tsp.service;

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
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.salescore.vrp_tsp.model.VRPTSPRequest;

import java.util.Collection;


@org.springframework.stereotype.Service
public class VRPService {

    public String solveVrp(VRPTSPRequest vrpRequest) {
        final int WEIGHT_INDEX = 0;

        // Define vehicle types and vehicles
        for (VRPTSPRequest.Vehicle vehicleRequest : vrpRequest.getVehicles()) {
            // Find the vehicle type by ID
            VRPTSPRequest.VehicleType vehicleTypeRequest = vrpRequest.getVehicleTypes()
                    .stream()
                    .filter(vt -> vt.getTypeId().equals(vehicleRequest.getTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Vehicle type not found"));

            VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance(vehicleRequest.getTypeId())
                    .addCapacityDimension(WEIGHT_INDEX, vehicleTypeRequest.getCapacity()) // Using vehicle type capacity
                    .build();

            VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance(vehicleRequest.getVehicleId());
            vehicleBuilder.setStartLocation(Location.newInstance(vehicleRequest.getStartAddress().getLon(), vehicleRequest.getStartAddress().getLat()));
            vehicleBuilder.setType(vehicleType);
            VehicleImpl vehicle = vehicleBuilder.build();

            // Add services
            VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
            vrpBuilder.addVehicle(vehicle);

            for (VRPTSPRequest.VrpService serviceRequest : vrpRequest.getServices()) {
                Service service = Service.Builder.newInstance(serviceRequest.getId())
                        .addSizeDimension(WEIGHT_INDEX, serviceRequest.getSize()) // Example of size
                        .setLocation(Location.newInstance(serviceRequest.getAddress().getLon(), serviceRequest.getAddress().getLat()))
                        .build();
                vrpBuilder.addJob(service);
            }

            // Build the problem instance
            VehicleRoutingProblem problem = vrpBuilder.build();

            // Create and run the algorithm
            VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);

            // Find the best solution
            Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
            VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

            // Prepare solution for output
            StringBuilder solutionOutput = new StringBuilder();
            SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);

            for (VehicleRoute route : bestSolution.getRoutes()) {
                solutionOutput.append("Route for vehicle ").append(route.getVehicle().getId()).append(":");
                solutionOutput.append(" Start at ").append(route.getStart().getLocation().getId());
                for (TourActivity activity : route.getActivities()) {
                    solutionOutput.append(" Visit location ").append(activity.getLocation().getId());
                }
                solutionOutput.append(" End at ").append(route.getEnd().getLocation().getId()).append("\n");
            }

            solutionOutput.append("Total cost of the solution: ").append(bestSolution.getCost()).append("\n");
            return solutionOutput.toString();
        }

        return "No vehicles found.";
    }
}