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

    /**
     * Create edge weighting (speeds) based on how traffic is moving.
     *
     * @param encoder the required vehicle
     * @param trafficFN filename to traffic CSV file
     * @param mapMatching instance of mapMatching to use
     * @throws FileNotFoundException if the traffic file doesn't exist
     */
    public TrafficWeighting(FlagEncoder encoder, String trafficFN, MapMatching mapMatching) throws FileNotFoundException {
        this.mapMatching = mapMatching;
        this.flagEncoder = encoder;

        // Read traffic data from file
        System.out.println("Reading traffic data from file.");
        HashMap<String, ArrayList<ArrayList<GHPoint>>> trafficData;
        trafficData = readTrafficCSV(trafficFN);
        ArrayList<ArrayList<GHPoint>> paths;

        for (String color : colors) {
            // Assume that light traffic is moving at the default speed
            if (color.equals("green")) continue;
            paths = trafficData.get(color);

            // Find the closest edge to each path
            for (ArrayList<GHPoint> path : paths) {
                System.out.println("Processing: " + path.toString());

                // Convert each GHPoint to a GPXEntry (and arbitrarily
                // set the time to 0) for matching
                ArrayList<GPXEntry> segmentGPX = new ArrayList<>();
                segmentGPX.add(new GPXEntry(path.get(0), 0));
                segmentGPX.add(new GPXEntry(path.get(1), 0));

                // Match road segment to graph edge
                MatchResult mr;
                try {
                    mr = mapMatching.doWork(segmentGPX);
                } catch (Exception e) {
                    System.out.println("Couldn't find match for segment: " + segmentGPX.toString() + "; skipping.");
                    continue;
                }

                EdgeMatch match = mr.getEdgeMatches().get(0);
                EdgeIteratorState edge = match.getEdgeState();

                // Set speed of edge
                double oldSpeed = encoder.getSpeed(edge.getFlags());
                double newSpeed = color.equals("yellow") ? yellowSpeed : redSpeed;
                edge.setFlags(encoder.setSpeed(edge.getFlags(), newSpeed));

                System.out.println("Editing weight for edge: " + edge.getName());
                System.out.println("Old speed: " + oldSpeed / 1.5 + " mph. New speed: " + newSpeed / 1.5 + "mph.");

            }
        }
    }


    /**
     * Read traffic data from a CSV
     *
     * Given a CSV of traffic data, read each row as light / moderate /
     * heavy traffic (green / yellow / red). Return a HashMap that has
     * as keys those categories, and as values a list of road segments
     * (which are themselves two GHPoints).
     *
     * @param trafficFN filename to traffic CSV file
     * @return traffic information
     */
    private HashMap<String, ArrayList<ArrayList<GHPoint>>> readTrafficCSV(String trafficFN) throws FileNotFoundException {
        // Open file and get header
        Scanner sc_in = new Scanner(new File(trafficFN));
        String header = sc_in.nextLine();
        System.out.println("Traffic data header: " + header);

        // Set up results. Each value will be a list of pairs, where
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

            // Each segment consists of two GHPoints; create those
            ArrayList<GHPoint> segment = new ArrayList<>();
            segment.add(new GHPoint(originLat, originLon));
            segment.add(new GHPoint(destLat, destLon));

            // Update list of segments
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
