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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.shapes.BBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
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
    protected final FlagEncoder flagEncoder;

    private static final String[] colors = new String[]{"green", "yellow", "red"};

    /**
     * Initialize traffic-based weighting with data from a CSV.
     *
     * @param encoder Not sure what this is
     * @param trafficFN Filename for CSV of traffic data
     */
    public TrafficWeighting(FlagEncoder encoder, String trafficFN) throws FileNotFoundException {
        this.flagEncoder = encoder;

        // Get traffic data from file
        HashMap<String, ArrayList<float[]>> trafficData;
        trafficData = readTrafficCSV(trafficFN);

        // For moderate and heavy traffic, find the nearest edge to each road segment
        ArrayList<float[]> roadSegments;
        for (String color : colors) {
            if (color.equals("green")) continue;

            roadSegments = trafficData.get(color);
            for (float[] segment : roadSegments) {
                System.out.println(Arrays.toString(segment));

                // does nothing right now
            }

        }

    }

    /**
     * Read traffic data from a CSV.
     *
     * @param trafficFN file name for CSV of traffic data
     * @return information about where traffic is light/moderate/heavy
     */
    private HashMap<String, ArrayList<float[]>> readTrafficCSV(String trafficFN) throws FileNotFoundException {
        // Open file and get header
        Scanner sc_in = new Scanner(new File(trafficFN));
        String header = sc_in.nextLine();
        System.out.println("Traffic data header: " + header);

        // Set up results -- green is light traffic, yellow is medium traffic,
        // red is heavy traffic.
        HashMap<String, ArrayList<float[]>> roads = new HashMap<>();
        for (String color : colors) {
            roads.put(color, new ArrayList<float[]>());
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

            roads.get(color).add(new float[]{originLon, originLat, destLon, destLat});
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
