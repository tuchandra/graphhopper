package com.graphhopper.routing.util;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

import java.util.HashMap;

/**
 * Created by isaac on 4/13/16.
 */

public class ScenicWeighting implements Weighting {

    private final FlagEncoder encoder;
    private HashMap<Integer, Integer> scenicEdges;

    public ScenicWeighting( FlagEncoder encoder, HashMap<Integer, Integer> scenicEdges) {
        this.encoder = encoder;
        this.scenicEdges = scenicEdges;
    }

    @Override
    public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId ) {
        int edge = edgeState.getEdge();
        if (scenicEdges.containsKey(edge)) {
            return scenicEdges.get(edge);
        } else {
            return edgeState.getDistance() / encoder.getSpeed(edgeState.getFlags());
        }
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return -1l;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return encoder;
    }

    @Override
    public double getMinWeight( double distance ) {
        return -1;
    }

    @Override
    public String getName() {
        return "SCENIC";
    }

    @Override
    public boolean matches(HintsMap map) {
        return true;
    }

}
