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
public class TrafficWeighting extends AbstractWeighting {
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

    private static final double KM_PER_MILE = 1.609;

    // This is the conversion factor for taking
    // edge.distance() [in m] / encoder.speed() [in km/hr]
    // and converting it to seconds. Multiply by 3600 s / 1 hr
    // and by 1 km / 1000 m.
    private static final double TIME_CONV = 3.6;

    private static final String[] colors = new String[]{"green", "yellow", "red"};
    private static final double yellowSpeed = 15 * KM_PER_MILE;
    private static final double redSpeed = 5 * KM_PER_MILE;

    /**
     * Create edge weighting (speeds) based on how traffic is moving.
     *
     * @param encoder the required vehicle
     * @param trafficFN filename to traffic CSV file
     * @param mapMatching instance of mapMatching to use
     * @throws FileNotFoundException if the traffic file doesn't exist
     */
    public TrafficWeighting(FlagEncoder encoder, String trafficFN, MapMatching mapMatching) throws FileNotFoundException {
        super(encoder);
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
     * Return the minimum possible weight, used for heuristic calculation.
     *
     * @param distance
     * @return weight (time in seconds)
     */
    @Override
    public double getMinWeight(double distance) {
        return distance / redSpeed * TIME_CONV;
    }

    /**
     * Calculate the weighting that a given edge should have. Note that high
     * weights correspond to unfavorable edges; the natural mapping from the
     * speeds calculated earlier is to weight by time, which sends high
     * speeds to more favorable, low times.
     *
     * @param edgeState        the edge for which the weight should be calculated
     * @param reverse          if the specified edge is specified in reverse direction e.g. from the reverse
     *                         case of a bidirectional search.
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     * @return
     */
    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double speed = reverse ? flagEncoder.getReverseSpeed(edgeState.getFlags()) : flagEncoder.getSpeed(edgeState.getFlags());
        double time = edgeState.getDistance() / speed * TIME_CONV;
        return time;
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
        return super.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

/*
        // TODO move this to AbstractWeighting?
        long time = 0;
        boolean unfavoredEdge = edgeState.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenaltyMillis;

        return time + super.calcMillis(edgeState, reverse, prevOrNextEdgeId);
*/

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
