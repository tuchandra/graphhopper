package com.graphhopper;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ScenicWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.util.HintsMap;

import java.util.HashMap;

/**
 * Created by isaac on 4/13/16.
 */
public class GHAltRouting extends GraphHopper {

    HashMap<Integer, Integer> scenicEdges;
    public void determineScenicEdges() {
        scenicEdges = null;
    }

    @Override
    public Weighting createWeighting( HintsMap wMap, FlagEncoder encoder)
    {
        String weighting = wMap.getWeighting();
        if ("SCENIC".equalsIgnoreCase(weighting)) {
            return new ScenicWeighting(encoder, scenicEdges);
        }
        else {
            return super.createWeighting(wMap, encoder);
        }
    }
}
