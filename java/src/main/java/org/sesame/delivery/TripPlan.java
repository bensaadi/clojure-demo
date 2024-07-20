package org.sesame.delivery;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.IntVar;
import com.google.ortools.constraintsolver.RoutingDimension;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import java.util.logging.Logger;

/* TODO:
  Use the SWEEP algorithm for multiple depots
*/

public class TripPlan {
  /*
   * returns array of itineraries 
   * each itinerary is an array of [lockerIndex, start time, end time]
   * time in number of minutes
   */
  public static int makeTest(long[][] numbers) {
    int sum = 0;
    for(int i = 0; i < numbers.length; ++i) {
    for(int j = 0; j < numbers[i].length; ++j) {
      sum += numbers[i][j];
    }}
    return sum;
  }

  public static long[][][] makePlan(
    long[][] timeMatrix,
    long[][] timeWindows,
    int depot,
    int vehicleNumber
  ) {
    long[][][] itineraries = new long[vehicleNumber][timeMatrix.length][3];

    for(int i = 0; i < vehicleNumber; ++i) {
      for(int j = 0; j < timeMatrix.length; ++j)
        for(int k = 0; k < 3; ++k) {
          itineraries[i][j][k] = -1;
        }
    }

    Loader.loadNativeLibraries();
    final DataModel data = new DataModel();
    RoutingIndexManager manager =
        new RoutingIndexManager(timeMatrix.length, vehicleNumber, depot);
    RoutingModel routing = new RoutingModel(manager);

    final int transitCallbackIndex =
        routing.registerTransitCallback((long fromIndex, long toIndex) -> {
          int fromNode = manager.indexToNode(fromIndex);
          int toNode = manager.indexToNode(toIndex);
          return timeMatrix[fromNode][toNode];
        });

    routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

    routing.addDimension(transitCallbackIndex,
        30, // allow waiting time
        600, // vehicle maximum capacities
        false, // start cumul to zero
        "Time");
    RoutingDimension timeDimension = routing.getMutableDimension("Time");

    // Add time window constraints for each location except depot.
    for (int i = 1; i < timeWindows.length; ++i) {
      long index = manager.nodeToIndex(i);
      timeDimension.cumulVar(index).setRange(timeWindows[i][0], timeWindows[i][1]);
    }
    // Add time window constraints for each vehicle start node.
    for (int i = 0; i < vehicleNumber; ++i) {
      long index = routing.start(i);
      timeDimension.cumulVar(index).setRange(timeWindows[0][0], timeWindows[0][1]);
    }

    // Instantiate route start and end times to produce feasible times.
    for (int i = 0; i < vehicleNumber; ++i) {
      routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.start(i)));
      routing.addVariableMinimizedByFinalizer(timeDimension.cumulVar(routing.end(i)));
    }

    // Setting first solution heuristic.
    RoutingSearchParameters searchParameters =
        main.defaultRoutingSearchParameters()
            .toBuilder()
            .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
            .build();

    // Solve the problem.
    Assignment solution = routing.solveWithParameters(searchParameters);

    if(solution == null || solution.empty()) {
      logger.info("Could not solve. Status : " + routing.status());
      return itineraries;
    }

    /* return value */

    long totalTime = 0;
    for (int i = 0; i < data.vehicleNumber; ++i) {
      long index = routing.start(i);

      int j = 0;
      while (!routing.isEnd(index)) {
        IntVar timeVar = timeDimension.cumulVar(index);
        itineraries[i][j][0] = manager.indexToNode(index);
        itineraries[i][j][1] = solution.min(timeVar);
        itineraries[i][j][2] = solution.max(timeVar);
        index = solution.value(routing.nextVar(index));
        ++j;
      }

      IntVar timeVar = timeDimension.cumulVar(index);
      itineraries[i][j][0] = manager.indexToNode(index);
      itineraries[i][j][1] = solution.min(timeVar);
      itineraries[i][j][2] = solution.max(timeVar);
    }

    return itineraries;
  }

  public static void main(String[] args) throws Exception {

  }
}