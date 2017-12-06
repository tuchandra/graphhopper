/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting;

import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import gnu.trove.map.hash.TByteIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Calculates the fastest route with the specified vehicle (VehicleEncoder). Calculates the weight
 * in seconds.
 * <p>
 *
 * @author Tushar Chandra
 */
public class TrafficWeighting implements Weighting {
    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
//    protected final static double SPEED_CONV = 3.6;
//    private final double headingPenalty;
//    private final long headingPenaltyMillis;
//    private final double maxSpeed;
//    private HashSet<Integer> bannedEdges;
    protected Graph graphStorage;
    protected LocationIndex locationIndex;
    protected MapMatching mapMatching;
    protected FlagEncoder flagEncoder;

    private static final String[] colors = new String[]{"green", "yellow", "red"};
    private static final double yellowSpeed = 15 * 1.609;  // 15 mph, but in km/h
    private static final double redSpeed = 5 * 1.609; // 5 mph, but in km/h


    public TrafficWeighting(FlagEncoder encoder, String trafficFN, MapMatching mapMatching) throws FileNotFoundException {
        this.mapMatching = mapMatching;
        this.flagEncoder = encoder;

        // Get traffic data from file
        HashMap<String, ArrayList<ArrayList<GHPoint>>> trafficData;
        trafficData = readTrafficPaths(trafficFN);
        System.out.println("Reading traffic data from file.");

        // For moderate and heavy traffic, find nearest edge to each road
        ArrayList<ArrayList<GHPoint>> paths;
        TIntHashSet visitedEdgeIDs = new TIntHashSet();

        for (String color : colors) {
            // Don't do anything with light traffic
            if (color.equals("green")) continue;
            paths = trafficData.get(color);

            // Convert each path to a GPXEntry list, then match it
            // and find the closest edge
            for (ArrayList<GHPoint> path : paths) {
                System.out.println("Processing: " + path.toString());
                ArrayList<GPXEntry> pathGPX = new ArrayList<>();
                for (GHPoint p : path) {
                    // arbitrarily set time to 0
                    pathGPX.add(new GPXEntry(p, 0));
                }

                MatchResult mr = mapMatching.doWork(pathGPX);
                EdgeMatch match = mr.getEdgeMatches().get(0);
                EdgeIteratorState edge = match.getEdgeState();

                double oldSpeed = encoder.getSpeed(edge.getFlags());
                double newSpeed = (color == "yellow") ? yellowSpeed : redSpeed;
                if (newSpeed != oldSpeed) {
                    System.out.println("Editing weight for edge: " + edge.getName());
                    System.out.println("Old speed: " + oldSpeed / 1.5 + " mph. New speed: " + newSpeed / 1.5 + "mph.");
                    edge.setFlags(encoder.setSpeed(edge.getFlags(), newSpeed));
                }

            }
        }
    }



    /**
     * Initialize traffic-based weighting with data from a CSV.
     *
     * @param encoder Not sure what this is
     * @param trafficFN Filename for CSV of traffic data
     * @param locationIndex Location index
     */
    public TrafficWeighting(FlagEncoder encoder, String trafficFN, LocationIndex locationIndex, Graph storage) throws FileNotFoundException {
        this.graphStorage = storage;
        this.locationIndex = locationIndex;

        // Get traffic data from file
        HashMap<String, ArrayList<ArrayList<GHPoint>>> trafficData;
        trafficData = readTrafficCSV(trafficFN);
        System.out.println("Reading traffic data from file.");

        // For moderate and heavy traffic, find nearest edge to each road
        ArrayList<ArrayList<GHPoint>> segments;
        TIntHashSet visitedEdgeIDs = new TIntHashSet();

        for (String color : colors) {
            // Don't do anything with light traffic
            if (color.equals("green")) continue;
            segments = trafficData.get(color);

            // For each segment, look up the midpoint and find the edge
            // closest to it. This can be improved by using the map
            // matching component.
            for (ArrayList<GHPoint> segment : segments) {
                double midLat = (segment.get(0).lat + segment.get(1).lat) / 2.0;
                double midLon = (segment.get(0).lon + segment.get(1).lon) / 2.0;

                // Find closest edge
                QueryResult qr = locationIndex.findClosest(midLat, midLon, EdgeFilter.ALL_EDGES);
                if (!qr.isValid()) {
                    System.out.println("No matching road found for entry " + segment.toString());
                    continue;
                }

                // Check if we already visited this edge (wouldn't happen with
                // map matcher)
                int edgeID = qr.getClosestEdge().getEdge();
                if (visitedEdgeIDs.contains(edgeID)) {
                    System.out.println("Attempting to update weight for edge already hit " + edgeID);
                    continue;
                }

                visitedEdgeIDs.add(edgeID);

                // Update edge speed
                EdgeIteratorState edge = storage.getEdgeIteratorState(edgeID, Integer.MIN_VALUE);
                double oldSpeed = encoder.getSpeed(edge.getFlags());
                double newSpeed = (color == "yellow") ? yellowSpeed : redSpeed;
                if (newSpeed != oldSpeed) {
                    System.out.println("Editing weight for edge: " + edgeID);
                    encoder.setSpeed(edge.getFlags(), newSpeed);
                }
            }

        }

    }


    /**
     * Read traffic data from a CSV; return all segments
     *
     * @param trafficFN file name for CSV of traffic data
     * @return information about where traffic is light/moderate/heavy
     */
    private HashMap<String, ArrayList<ArrayList<GHPoint>>> readTrafficCSV(String trafficFN) throws FileNotFoundException {
        // Open file and get header
        Scanner sc_in = new Scanner(new File(trafficFN));
        String header = sc_in.nextLine();
        System.out.println("Traffic data header: " + header);

        // Set up results -- green is light traffic, yellow is medium traffic,
        // red is heavy traffic. Each value will be a list of pairs, where
        // each pair has an origin and destination of a road segment.
        HashMap<String, ArrayList<ArrayList<GHPoint>>> segments = new HashMap<>();
        for (String color : colors) {
            segments.put(color, new ArrayList<ArrayList<GHPoint>>());
        }

        String color;
        float originLon;
        float originLat;
        float destLon;
        float destLat;

        while (sc_in.hasNext()) {
            // Every other line is empty, because Windows
            String line = sc_in.nextLine();
            String[] vals = line.split(",");
            if (vals.length <= 1) continue;

            // Read the actual line information
            color = vals[1];
            originLon = Float.valueOf(vals[2]);
            originLat = Float.valueOf(vals[3]);
            destLon = Float.valueOf(vals[4]);
            destLat = Float.valueOf(vals[5]);

            // segment = (origin, dest) pair
            ArrayList<GHPoint> segment = new ArrayList<>();
            segment.add(new GHPoint(originLat, originLon));
            segment.add(new GHPoint(destLat, destLon));

            // store to the right color
            segments.get(color).add(segment);
        }

        return segments;
    }


    /**
     * Read traffic data from a CSV; return all paths
     *
     * @param trafficFN file name for CSV of traffic data
     * @return information about where traffic is light/moderate/heavy
     */
    private HashMap<String, ArrayList<ArrayList<GHPoint>>> readTrafficPaths(String trafficFN) throws FileNotFoundException {
        // Open file and get header
        Scanner sc_in = new Scanner(new File(trafficFN));
        String header = sc_in.nextLine();
        System.out.println("Traffic data header: " + header);

        // Set up results -- green is light traffic, yellow is medium traffic,
        // red is heavy traffic. Each value will be a list of paths, which
        // we itself is a List<GHPoint>.
        HashMap<String, ArrayList<ArrayList<GHPoint>>> roads = new HashMap<>();
        for (String color : colors) {
            roads.put(color, new ArrayList<ArrayList<GHPoint>>());
        }

        String color;
        float segmentID;
        float originLon;
        float originLat;
        float destLon;
        float destLat;

        float currentSegmentID = -1;
        ArrayList<GHPoint> currentPath = null;

        while (sc_in.hasNext()) {
            // Every other line is empty, because Windows
            String line = sc_in.nextLine();
            String[] vals = line.split(",");
            if (vals.length <= 1) continue;

            // Read the actual line information
            segmentID = Float.valueOf(vals[0]);
            color = vals[1];
            originLon = Float.valueOf(vals[2]);
            originLat = Float.valueOf(vals[3]);
            destLon = Float.valueOf(vals[4]);
            destLat = Float.valueOf(vals[5]);

            // If we're starting a new segment, flush the current path and save it to the
            // list. Then restart the path with the origin and destination. Update
            // currentSegmentID with the ID of the new segment.
            if (segmentID != currentSegmentID) {
                if (currentPath != null) {  // it's null at the start, so catch that
                    roads.get(color).add(currentPath);
                }

                currentPath = new ArrayList<>();
                currentPath.add(new GHPoint(originLat, originLon));
                currentPath.add(new GHPoint(destLat, destLon));

                currentSegmentID = segmentID;
            }

            // Otherwise, continue the previous segment with the destination (since the origin
            // is the destination of the previous line)
            else {
                currentPath.add(new GHPoint(destLat, destLon));
            }
        }

        return roads;
    }



/*
    public AvoidanceWeighting(FlagEncoder encoder, PMap pMap, HashSet<Integer> bannedEdges) {
        super(encoder);
        headingPenalty = pMap.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);
        maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;
        this.bannedEdges = bannedEdges;
    }
*/

    @Override
    public double getMinWeight(double distance) {
        return 12;
    }




/*
    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }
*/

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return 12;
    }

/*
    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double speed = reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        if (speed < 72) {  // 45 mph
            if (bannedEdges.contains(edge.getEdge())) {
                return Double.POSITIVE_INFINITY;
            }
        }

        double time = edge.getDistance() / speed * SPEED_CONV;

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenalty;

        return time;
    }
*/


    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return 11222;

/*
        // TODO move this to AbstractWeighting?
        long time = 0;
        boolean unfavoredEdge = edgeState.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenaltyMillis;

        return time + super.calcMillis(edgeState, reverse, prevOrNextEdgeId);
*/
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return this.flagEncoder;
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting())
                && flagEncoder.toString().equals(reqMap.getVehicle());
    }

    @Override
    public String getName() {
        return "traffic";
    }
}
