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
    private MapMatching mapMatching;
    private String city;
    private String route_type;
    private String bannedGridCellsFn;
    private HashMap<String, FileWriter> outputFiles;
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private ArrayList<String> optimizations = new ArrayList<>();
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> id_to_points = new ArrayList<>();

    private String dataFolder = "C:/Users/Tushar/CS/routing-project/route-externalities/main/data/";
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
        optimizations.add("beauty");
        optimizations.add("simple");
        optimizations.add("fast");
        optimizations.add("safety");
        optimizations.add("traffic");
    }

    public void setDataSources() throws Exception {
        osmFile = osmFile + "chicago_illinois.osm.pbf";
        inputPointsFN = dataFolder + "small_chicago_od_pairs.csv";
        outputPointsFN = dataFolder + "chicago_routes_gh.csv";

        // This is where some kind of traffic CSV will go

        // block never hit, just leave it here lol
        // jk it errors
/*        if (city.equals("nyc")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc_noch";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "36005_empath_grid.csv");
            gridValuesFNs.add(gvfnStem + "36047_empath_grid.csv");
            gridValuesFNs.add(gvfnStem + "36061_empath_grid.csv");
            gridValuesFNs.add(gvfnStem + "36081_empath_grid.csv");
            gridValuesFNs.add(gvfnStem + "36085_empath_grid.csv");
            bannedGridCellsFn = gctfnStem + "nyc_banned_grid_cells.csv";
        }
*/
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

    // This one should work too?
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
        // create one GraphHopper instance
        hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
        hopper.setDataReaderFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));
//        hopper.setBannedGridCellsFn(bannedGridCellsFn);

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();
    }

    public void process_routes() throws Exception {

        AtomicInteger num_processed = new AtomicInteger();
        int num_odpairs = id_to_points.size();

        HashMap<String, HashMap<String, String>> results = new HashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new HashMap<String, String>());
        }

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


        for (String od_id : id_to_points) {
            System.out.println("Processing: " + od_id);
            int route = id_to_points.indexOf(od_id);
            HashMap<String, String> routes = process_route(route);
            for (String optimization : optimizations) {
                results.get(optimization).put(od_id, routes.getOrDefault(optimization, "FAILURE"));
            }

            int i = num_processed.incrementAndGet();
            if (i % 50 == 0) {
                System.out.println(System.getProperty("line.separator") + i + " of " + num_odpairs + " o-d pairs processed." + System.getProperty("line.separator"));
            }
        }

        for (String optimization : optimizations) {
            for (String result : results.get(optimization).values()) {
                outputFiles.get(optimization).write(result);
            }
            outputFiles.get(optimization).close();
        }
    }

    public HashMap<String, String> process_route(int route) {
        // Loop through origin-destination pairs, processing each one for beauty, non-beautiful matched, fastest, and simplest
        float[] points;
        String od_id;
        HashMap<String, String> responses = new HashMap<>();

        // Get Routes
        points = inputPoints.get(route);
        od_id = id_to_points.get(route);
        GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("ksp");
        GHResponse rsp = hopper.route(req);

        String defaultRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                + ")]\"," + "-1,-1,-1,[],-1,-1,-1,-1" + System.getProperty("line.separator");

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        // Get All Routes (up to 10K right now)
        List<PathWrapper> paths = rsp.getAll();

        if (paths.size() == 0) {
            System.out.println(route + ": No paths - skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        // Score each route on beauty to determine most beautiful
        int j = 0;
        float bestscore = -1000;
        int routeidx = -1;
        for (PathWrapper path : paths) {
            float score = getBeauty(path);
            if (score > bestscore) {
                bestscore = score;
                routeidx = j;
            }
            j++;
        }
        responses.put("beauty", writeOutput(route, "Beau", "beauty", od_id, paths.get(routeidx), bestscore));

        // Simplest Route
        j = 0;
        bestscore = 10000;
        routeidx = 0;
        for (PathWrapper path : paths) {
            int score = path.getSimplicity();
            if (score < bestscore) {
                bestscore = score;
                routeidx = j;
            }
            j++;
        }
        responses.put("simple", writeOutput(route, "Simp", "simple", od_id, paths.get(routeidx), getBeauty(paths.get(routeidx))));

        // Fastest Route
        PathWrapper bestPath = paths.get(0);
        responses.put("fast", writeOutput(route, "Fast", "fastest", od_id, bestPath, getBeauty(bestPath)));

        // Safety Route
        req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("safest_fastest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("dijkstrabi");
        rsp = hopper.route(req);

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Error - skipping.");
            responses.put("safety", defaultRow);
            return responses;
        }

        // Get paths (should be one)
        paths = rsp.getAll();

        if (paths.size() == 0) {
            System.out.println(route + ": No paths - skipping.");
            responses.put("safety", defaultRow);
            return responses;
        }

        // Fastest Safest Route
        bestPath = paths.get(0);
        responses.put("safety", writeOutput(route, "Safe", "safe-fastest", od_id, bestPath, getBeauty(bestPath)));

        return responses;
    }

    public static void main(String[] args) throws Exception {
        // Get PBFs from: https://mapzen.com/data/metro-extracts/

        // For setting # of cores to use
        //System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "12");

        boolean matchexternal = false;
        boolean getghroutes = true;

        AlternativeRoutingExternalities ksp = new AlternativeRoutingExternalities();

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

        if (getghroutes) {
            ksp.setDataSources();
//            ksp.getGridValues();  // Don't do anything here yet
            ksp.prepareGraphHopper();
            ksp.setODPairs();
            // ksp.process_routes();
        }
    }
}
