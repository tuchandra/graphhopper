package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.TrafficWeighting;
import com.graphhopper.util.*;

import javax.print.DocFlavor;
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
    private MapMatching mapMatching;
    private String city;  // not used
    private String route_type;  // not used
    private String bannedGridCellsFn;
    private HashMap<String, FileWriter> outputFiles;
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private ArrayList<String> optimizations = new ArrayList<>();
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> id_to_points = new ArrayList<>();


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
        optimizations.add("fastest");
        optimizations.add("traffic");
    }

    public void setDataSources() throws Exception {
        osmFile = osmFile + "chicago_illinois.osm.pbf";
        inputPointsFN = dataFolder + "small_chicago_od_pairs.csv";
        outputPointsFN = dataFolder + "chicago_routes_gh.csv";

        // This is where some kind of traffic CSV will go
        // if (IntelliJ.doesNotHateYou() && Java.doesNotSuck()) do.things();
    }

    // This function shouldn't ever be called yet / will break if you try
    public void getGridValues() throws Exception {
        gvHeaderMap = new HashMap<>();
        gridBeauty = new HashMap<>();

        for (String fn : gridValuesFNs) {
            try {
                Scanner sc_in = new Scanner(new File(fn));
                String[] gvHeader = sc_in.nextLine().split(",");
                int i = 0;
                for (String col : gvHeader) {
                    gvHeaderMap.put(col, i);
                    i++;
                }
                String line;
                String[] vals;
                String rc;
                float beauty;
                while (sc_in.hasNext()) {
                    line = sc_in.nextLine();
                    vals = line.split(",");
                    try {
                        rc = vals[gvHeaderMap.get("rid")] + "," + vals[gvHeaderMap.get("cid")];
                        beauty = Float.valueOf(vals[gvHeaderMap.get("beauty")]);
                        gridBeauty.put(rc, beauty);
                    } catch (NullPointerException ex) {
                        System.out.println(ex.getMessage());
                        System.out.println(line);
                        continue;
                    }
                }
            } catch (IOException io) {
                System.out.println(io + ": " + fn + " does not exist.");
            }
        }
    }

    // This one should work
    public void setODPairs() throws Exception {
        for (String optimization : optimizations) {
            outputFiles.put(optimization, new FileWriter(outputPointsFN.replaceFirst(".csv", "_" + optimization + ".csv"), true));
        }

        for (FileWriter fw : outputFiles.values()) {
            fw.write(outputheader);
        }

        // Bring in origin-destination pairs for processing
        Scanner sc_in = new Scanner(new File(inputPointsFN));
        String header = sc_in.nextLine();
        String od_id;
        float laF;
        float loF;
        float laT;
        float loT;
        float idx = 0;
        System.out.println("Input data points header: " + header);
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
            id_to_points.add(od_id);
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

    public float getBeauty(PathWrapper path) {
        // it's easier to stub this than replace it.
        if (1 == 1) return 11111;

        HashSet<String> roundedPoints = path.roundPoints();
        float score = 0;
        for (String pt : roundedPoints) {
            if (gridBeauty.containsKey(pt)) {
                score = score + gridBeauty.get(pt);
            }
        }
        score = score / roundedPoints.size();
        return score;
    }

    public void prepMapMatcher() {

        // create MapMatching object, can and should be shared accross threads
        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA).
                traversalMode(hopper.getTraversalMode()).
                hints(new HintsMap().put("weighting", "fastest").put("vehicle", "car")).
                build();
        mapMatching = new MapMatching(hopper, algoOpts);
        mapMatching.setTransitionProbabilityBeta(0.00959442);
        mapMatching.setMeasurementErrorSigma(100);
    }

    // This is used in getPaths
    public PathWrapper GPXToPath(ArrayList<GPXEntry> gpxEntries) {
        PathWrapper matchGHRsp = new PathWrapper();
        try {
            MatchResult mr = mapMatching.doWork(gpxEntries);
            Path path = mapMatching.calcPath(mr);
            new PathMerger().doWork(matchGHRsp, Collections.singletonList(path), new TranslationMap().doImport().getWithFallBack(Locale.US));
        }
        catch (RuntimeException e) {
            System.out.println("Broken GPX trace.");
            System.out.println(e.getMessage());
        }
        return matchGHRsp;
    }

    // This is used in the route-matching section
    public void PointsToPath(String fin, String fout) throws IOException {
        Scanner sc_in = new Scanner(new File(fin));
        String[] pointsHeader = sc_in.nextLine().split(",");
        int idIdx = -1;
        int nameIdx = -1;
        int latIdx = -1;
        int lonIdx = -1;
        int timeIdx = -1;
        for (int i=0; i<pointsHeader.length; i++) {
            if (pointsHeader[i].equalsIgnoreCase("ID")) {
                idIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("name")) {
                nameIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("lat")) {
                latIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("lon")) {
                lonIdx = i;
            }
            else if (pointsHeader[i].equalsIgnoreCase("millis")) {
                timeIdx = i;
            }
            else {
                System.out.println("Unexpected header value: " + pointsHeader[i]);
            }
        }
        String optimized = "";
        if (fin.indexOf("google") > -1) {
            optimized = optimized + "Goog";
        } else if (fin.indexOf("mapquest") > -1) {
            optimized = optimized + "MapQ";
        } else {
            System.out.println("Don't recognize platform: " + fin);
        }
        if (fin.indexOf("alt") > -1) {
            optimized = optimized + " altn";
        } else if (fin.indexOf("main") > -1) {
            optimized = optimized + " main";
        } else {
            System.out.println("Don't recognize route type: " + fin);
        }
        String line;
        String[] vals;
        String routeID = "";
        String prevRouteID = "";
        String name = "";
        String prevName = "";
        String label = "";
        String prevLabel = "";
        double lat;
        double lon;
        long time;
        HashMap<String, ArrayList<GPXEntry>> pointsLists = new HashMap<>();
        HashMap<String, String> routeNames = new HashMap<>();
        ArrayList<GPXEntry> pointsList = new ArrayList<>();
        while (sc_in.hasNext()) {
            line = sc_in.nextLine();
            vals = line.split(",");
            routeID = vals[idIdx];
            name = vals[nameIdx];
            if (name.equalsIgnoreCase("alternative 2") || name.equalsIgnoreCase("alternative 3")) {
                continue;
            }
            lat = Double.valueOf(vals[latIdx]);
            lon = Double.valueOf(vals[lonIdx]);
            time = Long.valueOf(vals[timeIdx]);
            label = routeID + "|" + name;
            GPXEntry pt = new GPXEntry(lat, lon, time);
            if (label.equalsIgnoreCase(prevLabel)) {
                pointsList.add(pt);
            }
            else if (pointsList.size() > 0) {
                pointsLists.put(prevRouteID, pointsList);
                routeNames.put(prevRouteID, prevName);
                pointsList = new ArrayList<>();
                pointsList.add(pt);
            } else {
                System.out.println("First point.");
                pointsList.add(pt);
            }
            prevRouteID = routeID;
            prevName = name;
            prevLabel = label;
        }
        if (pointsList.size() > 0) {
            pointsLists.put(prevRouteID, pointsList);
            routeNames.put(prevRouteID, prevName);
        }
        sc_in.close();

        HashMap<String, String> results = getPaths(pointsLists, routeNames, optimized);
        FileWriter sc_out = new FileWriter(fout, true);
        sc_out.write(outputheader);
        for (String result : results.values()) {
            sc_out.write(result);
        }
        sc_out.close();
    }

    // This is used in PointsToPath
    public HashMap<String, String> getPaths(HashMap<String, ArrayList<GPXEntry>> pointLists,
                                            HashMap<String, String> routeNames, String optimized) {

        AtomicInteger num_processed = new AtomicInteger();
        int num_routes = pointLists.size();
        Set<String> routeIDs = pointLists.keySet();

        HashMap<String, String> results = new HashMap<>();
        for (String routeID : routeIDs) {
            System.out.println("Processing: " + routeID);
            int i = num_processed.incrementAndGet();
            PathWrapper path = GPXToPath(pointLists.get(routeID));
            if (path.getDistance() > 0) {
                float score = getBeauty(path);
                results.put(routeID, writeOutput(i, optimized, routeNames.get(routeID), routeID, path, score));
            }
            if (i % 50 == 0) {
                System.out.println("\t\t" + i + " of " + num_routes + " routes matched.");
            }

        }

        return results;
    }

    public void prepareGraphHopper() {
        hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
        hopper.setDataReaderFile(osmFile);
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();
    }

    public void process_routes() throws Exception {

        AtomicInteger num_processed = new AtomicInteger();
        int num_odpairs = id_to_points.size();

        // results is designed to be {optimization : {id : route}}
        HashMap<String, HashMap<String, String>> results = new HashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new HashMap<String, String>());
        }

/*        // this is never hit, just leave it here
        if (optimizations.contains("safety")) {
            // initialize banned edges
            GHRequest req = new GHRequest(inputPoints.get(0)[0], inputPoints.get(0)[1],
                    inputPoints.get(0)[2], inputPoints.get(0)[3]).  // latFrom, lonFrom, latTo, lonTo
                    setWeighting("safest_fastest").
                    setVehicle("car").
                    setLocale(Locale.US).
                    setAlgorithm("dijkstrabi");
            GHResponse rsp = hopper.route(req);
        }
*/

        for (String od_id : id_to_points) {
            System.out.println("Processing: " + od_id);
            int route = id_to_points.indexOf(od_id);
            HashMap<String, String> routes = process_route(route);
            for (String optimization : optimizations) {
                results.get(optimization).put(od_id, routes.getOrDefault(optimization, "FAILURE"));
            }

            int i = num_processed.incrementAndGet();
            System.out.println(System.getProperty("line.separator") + i + " of " + num_odpairs + " o-d pairs processed." + System.getProperty("line.separator"));
        }

        for (String optimization : optimizations) {
            for (String result : results.get(optimization).values()) {
                outputFiles.get(optimization).write(result);
            }
            outputFiles.get(optimization).close();
        }
    }



/*
    public String getBestRoute(GraphHopper hopper, String optimized, int route) {
        float[] points = inputPoints.get(route);
        String od_id = id_to_points.get(route);

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
        return writeOutput(route, optimized, optimized, od_id, path, -10000);
    }

*/


    public HashMap<String, String> process_route(int route) {
        // Loop through origin-destination pairs, processing each one for
        // ordinary fastest and traffic-optimized routes
        float[] points = inputPoints.get(route);
        String od_id = id_to_points.get(route);

        HashMap<String, String> responses = new HashMap<>();

        // Default row for the CSVs, in case of an error or something
        String defaultRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                + ")]\"," + "-1,-1,-1,[],-1,-1,-1,-1" + System.getProperty("line.separator");

        // Get fastest routes
        System.out.println("Looking for fastest routes.");
        GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("ksp");
        GHResponse rsp = hopper.route(req);

        // TODO FACTOR THIS CODE BETTER
        // Check for errors, and handle them if so
        if (rsp.hasErrors()) {
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            responses.put("fastest", defaultRow);
        }

        List<PathWrapper> paths = rsp.getAll();
        System.out.println("Got all the fastest routes!");

        if (paths.size() == 0) {
            System.out.println(route + ": No paths - skipping.");
            responses.put("fastest", defaultRow);
        }

        PathWrapper bestPath = paths.get(0);
        responses.put("fastest", writeOutput(route, "Fast", "fastest", od_id, bestPath, getBeauty(bestPath)));

        // Get traffic routes
        System.out.println("Looking for routes under traffic.");
        req = new GHRequest(points[0], points[1], points[2], points[3]).
                setWeighting("traffic").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("ksp");
        rsp = hopper.route(req);

        // Check for errors, and handle them if so
        if (rsp.hasErrors()) {
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            responses.put("traffic", defaultRow);
            return responses;
        }

        paths = rsp.getAll();
        System.out.println("Got all the traffic-optimized routes!");

        if (paths.size() == 0) {
            System.out.println(route + ": No paths - skipping.");
            responses.put("traffic", defaultRow);
            return responses;
        }

        bestPath = paths.get(0);
        responses.put("traffic", writeOutput(route, "Traffic", "traffic", od_id, bestPath, getBeauty(bestPath)));

        return responses;
    }

    public static void main(String[] args) throws Exception {
        // Get PBFs from: https://mapzen.com/data/metro-extracts/

        // For setting # of cores to use
        //System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "12");

        boolean matchexternal = false;
        boolean getghroutes = true;
//        boolean useTraffic = false;  // args[0]

        AlternativeRoutingExternalities ksp = new AlternativeRoutingExternalities();
//        ksp.setDataSources();


//        ksp.prepareGraphHopper();

        if (getghroutes) {
            ksp.setDataSources();
            ksp.prepareGraphHopper();
            ksp.setODPairs();
            ksp.process_routes();
        }

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
