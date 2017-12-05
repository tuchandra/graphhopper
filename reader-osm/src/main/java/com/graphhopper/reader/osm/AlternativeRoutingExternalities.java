package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by isaac on 09/14/16.
 */
public class AlternativeRoutingExternalities {

    private GraphHopper hopper;
    private String city;  // not used
    private String route_type;  // not used
    private String bannedGridCellsFn;
    private HashMap<String, FileWriter> outputFiles;
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private ArrayList<String> optimizations = new ArrayList<>();
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> idToPoints = new ArrayList<>();


    // This is kind of a shitshow, clean up eventually
    private String dataFolder = "C:/Users/Tushar/CS/routing-project/routing/main/data/";
    private String osmFile = dataFolder;
//    private String graphFolder = dataFolder;
    private String inputPointsFN = dataFolder;
    private String outputPointsFN = dataFolder;

//    private String osmFile = "./reader-osm/files/";
    private String graphFolder = "./reader-osm/target/tmp/";
//    private String inputPointsFN = "../data/intermediate/";
//    private String outputPointsFN = "../data/routes/";

    private String gvfnStem = "../data/intermediate/";
    private String gctfnStem = "../data/intermediate/";
    private String outputheader = "ID,name,polyline_points,total_time_in_sec,total_distance_in_meters," +
            "number_of_steps,maneuvers,beauty,simplicity,pctNonHighwayTime,pctNonHighwayDist,pctNeiTime,pctNeiDist" +
            System.getProperty("line.separator");

    public AlternativeRoutingExternalities() {
        this.outputFiles = new HashMap<>();
    }

    /**
     * Set all the data sources (file names, mainly).
     */
    public void setDataSources() {
        osmFile = osmFile + "chicago_illinois.osm.pbf";
        inputPointsFN = dataFolder + "small_chicago_od_pairs.csv";
        outputPointsFN = dataFolder + "chicago_routes_gh.csv";

        // This is where some kind of traffic CSV will go
        // if (IntelliJ.doesNotHateYou() && Java.doesNotSuck()) do.things();
    }


    /**
     * Initialize GraphHopper for this class
     *
     * The type is accepted as a parameter here so that we don't have to
     * constantly recalculate weights when sending requests, and can
     * instead set them as we initialize hopper. This is why we created
     * the class TrafficHopperOSM extending GraphHopperOSM, having the
     * same functionality except for a different set of weights.
     *
     * @param type either "default" or "traffic"
     */
    public void prepareGraphHopper(String type) {
        // Set GraphHopper instance differently based on desired type.
        if (type.equals("traffic")) {
            hopper = new TrafficHopperOSM().forDesktop().setCHEnabled(false);
            optimizations.add("traffic");
        } else {
            hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
            optimizations.add("fastest");
        }

        hopper.setDataReaderFile(osmFile);
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        // This can take minutes (if it imports) or seconds (for loading).
        // Of course, this is dependent on the area you import.
        hopper.importOrLoad();
    }

    /**
     * Read the origin-destination pairs from a file.
     *
     * @throws Exception if bad stuff happens, idk
     */
    public void setODPairs() throws Exception {
        // Create an output file for each optimization
        for (String optimization : optimizations) {
            outputFiles.put(optimization, new FileWriter(outputPointsFN.replaceFirst(".csv", "_" + optimization + ".csv"), true));
        }

        for (FileWriter fw : outputFiles.values()) {
            fw.write(outputheader);
        }

        // Bring in origin-destination pairs for processing
        Scanner sc_in = new Scanner(new File(inputPointsFN));
        String header = sc_in.nextLine();
        System.out.println("Input data points header: " + header);

        String od_id;
        float laF;
        float loF;
        float laT;
        float loT;
        float idx = 0;

        while (sc_in.hasNext()) {
            // Every other line is empty, because Windows
            String line = sc_in.nextLine();
            String[] vals = line.split(",");
            if (vals.length <= 1) continue;

            idx = idx + 1;
            od_id = vals[0];
            loF = Float.valueOf(vals[1]);
            laF = Float.valueOf(vals[2]);
            loT = Float.valueOf(vals[3]);
            laT = Float.valueOf(vals[4]);
            inputPoints.add(new float[]{laF, loF, laT, loT, idx});
            idToPoints.add(od_id);
        }

        int numPairs = inputPoints.size();
        System.out.println(numPairs + " origin-destination pairs.");
    }

    // what the FUCK is this method
    public String writeOutput(int i, String optimized, String name, String od_id, PathWrapper bestPath, float score) {

        // points, distance in meters and time in seconds (convert from ms) of the full path
        PointList pointList = bestPath.getPoints();
        int simplicity = bestPath.getSimplicity();
        double distance = Math.round(bestPath.getDistance() * 100) / 100;
        double nonHighwayDistance = bestPath.getNonHighwayDistance();
        double pctNHD = Math.round(1000.0 * (float) nonHighwayDistance / distance) / 1000.0;
        long timeInSec = bestPath.getTime() / 1000;
        long nonHighwayTimeInSec = bestPath.getNonHighwayTime() / 1000;
        double pctNHT = Math.round(1000.0 * (float) nonHighwayTimeInSec / timeInSec) / 1000.0;
        double smallNeiDistance = bestPath.getNeiHighwayDistance();
        double pctNeiD = Math.round(1000.0 * (float) smallNeiDistance / distance) / 1000.0;
        long neiHighwayTimeInSec = bestPath.getTimeSmallNeigh() / 1000;
        double pctNeiT = Math.round(1000.0 * (float) neiHighwayTimeInSec / timeInSec) / 1000.0;
        InstructionList il = bestPath.getInstructions();
        int numDirections = il.getSize();
        // iterate over every turn instruction
        ArrayList<String> maneuvers = new ArrayList<>();
        for (Instruction instruction : il) {
            maneuvers.add(instruction.getSimpleTurnDescription());
        }

        System.out.println(i + " (" + optimized + "): Distance: " +
                distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections +
                ";\tSimplicity: " + simplicity + ";\tScore: " + score +
                ";\tPctNHT: " + pctNHT + ";\tPctNeiT: " + pctNeiT);

        return od_id + "," + name + "," +
                "\"[" + pointList + "]\"," +
                timeInSec + "," + distance + "," + numDirections +
                ",\"" + maneuvers.toString() + "\"" + "," +
                score + "," + simplicity + "," +
                pctNHT + "," + pctNHD + "," +
                pctNeiT + "," + pctNeiD +
                System.getProperty("line.separator");


    }

    /**
     * Get all of the routes we are interested in.
     *
     * For every optimization and for every OD pair, find the best route
     * and write it to the output file we care about.
     * @throws Exception
     */
    public void processRoutes() throws Exception {
        AtomicInteger numProcessed = new AtomicInteger();
        int numODPairs = idToPoints.size();

        // results is designed to be {optimization : {id : route}}
        HashMap<String, HashMap<String, String>> results = new HashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new HashMap<String, String>());
        }

        for (String odID : idToPoints) {
            System.out.println("Processing: " + odID);
            int route = idToPoints.indexOf(odID);

            // Right now, we only have one optimization at a time. We set
            // up getBestRoute to take the optimization as a parameter in
            // case it's needed in the future.
            for (String optimization : optimizations) {
                String bestRoute = getBestRoute(optimization, route);
                results.get(optimization).put(odID, bestRoute);
            }

            int i = numProcessed.incrementAndGet();
            System.out.println(System.getProperty("line.separator") + i + " of " + numODPairs + " o-d pairs processed." + System.getProperty("line.separator"));
        }

        // Write everything to a file
        for (String optimization : optimizations) {
            for (String result : results.get(optimization).values()) {
                outputFiles.get(optimization).write(result);
            }
            outputFiles.get(optimization).close();
        }
    }

    /**
     * Given an optimization and a route ID, compute the best path
     * @param optimization What the route is based off (not used)
     * @param route route ID
     * @return best path found
     */
    public String getBestRoute(String optimization, int route) {
        float[] points = inputPoints.get(route);
        String od_id = idToPoints.get(route);

        // Default row for the response in case of an error or something
        String defaultRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                + ")]\"," + "-1,-1,-1,[],-1,-1,-1,-1" + System.getProperty("line.separator");

        System.out.println("Looking for best route.");

        // Theoretically, the GH passed should already have the correct weighting
        GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("ksp");
        GHResponse rsp = hopper.route(req);

        // Handle errors
        if (rsp.hasErrors()) {
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            return defaultRow;
        }

        PathWrapper path;
        try {
            path = rsp.getBest();
        } catch (RuntimeException e) {
            System.out.println(route + ": No paths - skipping.");
            return defaultRow;
        }

        System.out.println("Got the route!");
        return writeOutput(route, optimization, optimization, od_id, path, -10000);
    }

    public static void main(String[] args) throws Exception {
        // Get PBFs from: https://mapzen.com/data/metro-extracts/

        // For setting # of cores to use
        // System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "12");

        boolean matchexternal = false;
        boolean getghroutes = true;
        String typeOfRouting = args[0];

        AlternativeRoutingExternalities ksp = new AlternativeRoutingExternalities();
        ksp.setDataSources();

        if (typeOfRouting.equals("traffic")) {
            System.out.println("Starting traffic routing");
            ksp.prepareGraphHopper("traffic");
        } else {
            System.out.println("Starting default fastest routing");
            ksp.prepareGraphHopper("default");
        }

        ksp.setODPairs();
        ksp.processRoutes();

/*
        if (matchexternal) {
            ksp.setDataSources();
            ksp.getGridValues();
            ksp.prepareGraphHopper();
            ksp.prepMapMatcher();
            String inputfolder = "../data/intermediate/";
            String outputfolder = "../data/routes/";
            ArrayList<String> platforms = new ArrayList<>();
            platforms.add("google");
            platforms.add("mapquest");
            ArrayList<String> conditions = new ArrayList<>();
            conditions.add("traffic");
            ArrayList<String> routetypes = new ArrayList<>();
            routetypes.add("main");
            for (String platform : platforms) {
                for (String condition : conditions) {
                    for (String routetype : routetypes) {
                        ksp.PointsToPath(inputfolder + city + "_" + platform + "_" + condition +
                                "_routes_" + routetype + "_gpx.csv", outputfolder + city + "_" + odtype + "_" +
                                platform + "_" + condition + "_routes_" + routetype + "_ghenhanced_sigma100_transitionDefault.csv");
                    }
                }
            }
        }
*/

    }
}
