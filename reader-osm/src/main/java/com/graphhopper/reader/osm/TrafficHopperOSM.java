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
package com.graphhopper.reader.osm;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Parameters;
import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;

import java.io.FileNotFoundException;

/**
 * Simplified entry to OpenStreetMap data, but using traffic weighting.
 * @author Tushar Chandra
 */
public class TrafficHopperOSM extends GraphHopperOSM {
    public Weighting trafficWeighting;
    public MapMatching mapMatching;
    private String trafficFN = "../routing/main/data/traffic.csv";



    /**
     * Prepare map matching object; initialize with current Hopper and
     * trafffic weighting. This needs to be run before using mapMatching.
     */
    private void prepareMapMatching() {
        AlgorithmOptions algoOptions = AlgorithmOptions.start()
                .algorithm(Parameters.Algorithms.DIJKSTRA)
                .traversalMode(this.getTraversalMode())
                .hints(new HintsMap().put("weighting", "traffic")
                        .put("vehicle", "car"))
                .build();

        this.mapMatching = new MapMatching(this, algoOptions);
    }

    /**
     * Create weighting based on traffic data.
     *
     * Attempt to access a copy saved to the class if available -- we
     * neither want, nor need, to create a new one every time.
     *
     * @param hintsMap all parameters influencing the weighting. E.g. parameters coming via
     *                 GHRequest.getHints or directly via "&amp;api.xy=" from the URL of the web UI
     * @param encoder  the required vehicle
     * @return Weighting based on traffic data
     */
    @Override
    public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder) {
        // If we don't have a weighting yet, create a new one; otherwise
        // just return the one we have.
        if (this.trafficWeighting == null) {
            try {
                prepareMapMatching();
//                LocationIndex locationIndex = this.getLocationIndex();
//                Graph graphStorage = this.getGraphHopperStorage();
//                this.trafficWeighting = new TrafficWeighting(encoder, trafficFN, locationIndex, graphStorage);

                this.trafficWeighting = new TrafficWeighting(encoder, trafficFN, mapMatching);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return this.trafficWeighting;
    }

}
