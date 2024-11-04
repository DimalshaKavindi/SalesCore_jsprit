package com.salescore.vrp_tsp.service;

import com.salescore.vrp_tsp.model.VRPTSPRequest;
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
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;

import java.util.Collection;

@org.springframework.stereotype.Service
public class TSPService {

    public String solveTsp(VRPTSPRequest tspRequest) {
        final int WEIGHT_INDEX = 0;

        // Define a single vehicle type with large capacity to ignore capacity constraints
        VehicleTypeImpl.Builder vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("TSP_Vehicle_Type")
                .addCapacityDimension(WEIGHT_INDEX, Integer.MAX_VALUE);
        VehicleTypeImpl vehicleType = vehicleTypeBuilder.build();

        // Create a single vehicle for the TSP solution
        VRPTSPRequest.Vehicle vehicleRequest = tspRequest.getVehicles().get(0);
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance(vehicleRequest.getVehicleId())
                .setStartLocation(Location.newInstance(vehicleRequest.getStartAddress().getLon(), vehicleRequest.getStartAddress().getLat()))
                .setType(vehicleType);
        VehicleImpl vehicle = vehicleBuilder.build();

        // Define the problem, adding only locations as jobs without capacity constraints
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(vehicle);

        for (VRPTSPRequest.VrpService serviceRequest : tspRequest.getServices()) {
            Service service = Service.Builder.newInstance(serviceRequest.getId())
                    .setLocation(Location.newInstance(serviceRequest.getAddress().getLon(), serviceRequest.getAddress().getLat()))
                    .addSizeDimension(WEIGHT_INDEX, 0) // Capacity not a concern in TSP
                    .build();
            vrpBuilder.addJob(service);
        }

        // Build and solve the TSP problem
        VehicleRoutingProblem problem = vrpBuilder.build();
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // Prepare solution output
        StringBuilder solutionOutput = new StringBuilder();
        SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);

        for (VehicleRoute route : bestSolution.getRoutes()) {
            solutionOutput.append("Route for vehicle ").append(route.getVehicle().getId()).append(":\n");
            solutionOutput.append("Start at ").append(route.getStart().getLocation().getId()).append("\n");
            for (TourActivity activity : route.getActivities()) {
                solutionOutput.append("Visit location ").append(activity.getLocation().getId()).append("\n");
            }
            solutionOutput.append("End at ").append(route.getEnd().getLocation().getId()).append("\n");
        }

        solutionOutput.append("Total cost of the solution: ").append(bestSolution.getCost()).append("\n");
        return solutionOutput.toString();
    }
}
