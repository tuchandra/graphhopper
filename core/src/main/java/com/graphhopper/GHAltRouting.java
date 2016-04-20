package com.graphhopper;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ScenicWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.WeightingMap;

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
    public Weighting createWeighting( WeightingMap wMap, FlagEncoder encoder)
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
