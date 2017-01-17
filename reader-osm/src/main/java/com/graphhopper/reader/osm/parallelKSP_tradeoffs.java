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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by isaac on 09/14/16.
 */
public class parallelKSP_tradeoffs {

    String city;
    String route_type;
    HashMap<String, FileWriter> outputFiles;
    private String osmFile = "./reader-osm/files/";
    private String graphFolder = "./reader-osm/target/tmp/";
    private String inputPointsFN = "../data/intermediate/";
    private String outputPointsFN = "../data/final/tradeoffs/";
    private String gvfnStem = "../data/intermediate/";
    private String gctfnStem = "../geometries/";
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<String> gridCTsFNs = new ArrayList<>();
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private HashMap<String, Integer> gridCT;
    private GraphHopper hopper;
    private MapMatching mapMatching;
    private String outputheader = "ID";
    private ArrayList<float[]> inputPoints = new ArrayList<>();
    private ArrayList<String> id_to_points = new ArrayList<>();
    private ArrayList<String> optimizations = new ArrayList<>();
    private int stepsize = 10;
    private int maxstep = 100;

    public parallelKSP_tradeoffs(String city, String route_type) {

        this.city = city;
        this.route_type = route_type;
        this.outputFiles = new HashMap<>();
        optimizations.add("beauty_time");
        optimizations.add("simple_time");
        optimizations.add("beauty_beauty");
        optimizations.add("simple_simple");
        for (int i = 0; i <= maxstep; i += stepsize) {
            outputheader = outputheader + "," + i + "pct";
        }
        outputheader = outputheader + System.getProperty("line.separator");
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setRouteType(String route_type) {
        this.route_type = route_type;
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

        ConcurrentHashMap<String, String> results = getPaths(pointsLists, routeNames, optimized);
        FileWriter sc_out = new FileWriter(fout, true);
        sc_out.write(outputheader);
        for (String result : results.values()) {
            sc_out.write(result);
        }
        sc_out.close();
    }

    public ConcurrentHashMap<String, String> getPaths(HashMap<String, ArrayList<GPXEntry>> pointLists, HashMap<String, String> routeNames, String optimized) {

        AtomicInteger num_processed = new AtomicInteger();
        int num_routes = pointLists.size();
        Set<String> routeIDs = pointLists.keySet();

        ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
        routeIDs.parallelStream().forEach(routeID -> {
            System.out.println("Processing: " + routeID);
            int i = num_processed.incrementAndGet();
            PathWrapper path = GPXToPath(pointLists.get(routeID));
            if (path.getDistance() > 0) {
                float score = getBeauty(path);
                results.put(routeID, writeOutput(routeID, new float[1], new int[1]));
            }
            if (i % 50 == 0) {
                System.out.println(i + " of " + num_routes + " routes matched.");
            }
        }
        );

        return results;
    }

    //TODO: find some way to match path to virtual nodes at start/finish or hope map-matcher updates
    public PathWrapper trimPath(PathWrapper path, ArrayList<GPXEntry> original) {
        return new PathWrapper();
    }


    public void setDataSources() throws Exception {
        if (city.equals("sf")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf_noch";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "06075_ct_grid.csv");
        } else if (city.equals("nyc")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc_noch";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "36005_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36047_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36061_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36081_logfractionempath_ft.csv");
            gridValuesFNs.add(gvfnStem + "36085_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "nyc_ct_grid.csv");
        } else if (city.equals("bos")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos_noch";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
            gridCTsFNs.add(gctfnStem + "25025_ct_grid.csv");
        } else if (city.equals("chi")) {
            osmFile = osmFile + "chicago_illinois.osm.pbf";
            graphFolder = graphFolder + "ghosm_chi_noch";
            inputPointsFN = inputPointsFN + "chi_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "chi_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "17031_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "17031_ct_grid.csv");
        } else if (city.equals("sin")) {
            osmFile = osmFile + "singapore.osm.pbf";
            graphFolder = graphFolder + "ghosm_sin_noch";
            inputPointsFN = inputPointsFN + "sin_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sin_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "SINGAPORE_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
        } else if (city.equals("lon")) {
            osmFile = osmFile + "london_england.osm.pbf";
            graphFolder = graphFolder + "ghosm_lon_noch";
            inputPointsFN = inputPointsFN + "lon_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "lon_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "LONDON_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
        } else if (city.equals("man")) {
            osmFile = osmFile + "manila_philippines.osm.pbf";
            graphFolder = graphFolder + "ghosm_man_noch";
            inputPointsFN = inputPointsFN + "man_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "man_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "MANILA_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "");
        } else {
            throw new Exception("Invalid Parameters: city must be of 'SF','NYC', or 'BOS' and route_type of 'grid' or 'rand'");
        }
    }

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

    public void getGridCTs() throws Exception {
        gridCT = new HashMap<>();
        for (String fn : gridCTsFNs) {
            try {
                Scanner sc_in = new Scanner(new File(fn));
                sc_in.nextLine();
                String line;
                String[] vals;
                String rc;
                int ct;
                while (sc_in.hasNext()) {
                    line = sc_in.nextLine();
                    vals = line.split(",");
                    try {
                        rc = vals[1] + "," + vals[0];
                        ct = Integer.valueOf(vals[2]);
                        gridCT.put(rc, ct);
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

    public void prepareGraphHopper() {
        // create one GraphHopper instance
        hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
        hopper.setDataReaderFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();
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
//        mapMatching.setTransitionProbabilityBeta(0.000959442);
        mapMatching.setMeasurementErrorSigma(100);
    }


    public String writeOutput(String od_id, float[] scores, int[] bestindices) {

        // preps output for CSV
        String result = od_id;
        int num_bins = bestindices.length;
        for (int i = 0; i < num_bins; i++) {
            try {
                result = result + "," + scores[bestindices[i]];
            } catch (ArrayIndexOutOfBoundsException ai) {
                result = result + ",";
                System.out.println(System.getProperty("line.separator") + System.getProperty("line.separator") + "ArrayIndexException: beauty" + System.getProperty("line.separator") + System.getProperty("line.separator"));
            }
        }
        System.out.println(result);
        return result + System.getProperty("line.separator");


    }

    public String writeOutput(String od_id, long[] scores, int[] bestindices) {

        // preps output for CSV
        String result = od_id;
        int num_bins = bestindices.length;
        for (int i = 0; i < num_bins; i++) {
            try {
                result = result + "," + scores[bestindices[i]];
            } catch (ArrayIndexOutOfBoundsException ai) {
                result = result + ",";
                System.out.println(System.getProperty("line.separator") + System.getProperty("line.separator") + "ArrayIndexException: time" + System.getProperty("line.separator") + System.getProperty("line.separator"));
            }
        }
        System.out.println(result);
        return result + System.getProperty("line.separator");


    }

    public String writeOutput(String od_id, int[] scores, int[] bestindices) {

        // preps output for CSV
        String result = od_id;
        int num_bins = bestindices.length;
        for (int i = 0; i < num_bins; i++) {
            try {
                result = result + "," + scores[bestindices[i]];
            } catch (ArrayIndexOutOfBoundsException ai) {
                result = result + ",";
                System.out.println(System.getProperty("line.separator") + System.getProperty("line.separator") + "ArrayIndexException: simplicity" + System.getProperty("line.separator") + System.getProperty("line.separator"));
            }
        }
        System.out.println(result);
        return result + System.getProperty("line.separator");


    }

    public int getNumCTs(PathWrapper path) {
        if (gridCT.size() == 0) {
            return -1;
        }
        HashSet<String> roundedPoints = path.roundPoints();
        HashSet<Integer> cts = new HashSet<>();
        for (String pt : roundedPoints) {
            if (gridCT.containsKey(pt)) {
                cts.add(gridCT.get(pt));
            }
        }
        return cts.size();
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

    public void setODPairs() throws Exception {
        // Prep Filewriters (Optimized, Worst-but-same-distance, Fastest, Simplest)
        for (String optimization : optimizations) {
            outputFiles.put(optimization, new FileWriter(outputPointsFN.replaceFirst(".csv", "_optimize" + optimization + "tradeoff.csv"), true));
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
            idx = idx + 1;
            String line = sc_in.nextLine();
            String[] vals = line.split(",");
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

    public void process_routes() throws Exception {

        AtomicInteger num_processed = new AtomicInteger();
        int num_odpairs = id_to_points.size();

        ConcurrentHashMap<String, ConcurrentHashMap<String, String>> results = new ConcurrentHashMap<>();
        for (String optimization : optimizations) {
            results.put(optimization, new ConcurrentHashMap<>());
        }
        id_to_points.parallelStream().forEach(od_id -> {
            System.out.println("Processing: " + od_id);
            int route = id_to_points.indexOf(od_id);
            HashMap<String, String> routes = process_route(route);
            for (String optimization : optimizations) {
                results.get(optimization).put(od_id, routes.getOrDefault(optimization, "FAILURE"));
            }
            int i = num_processed.incrementAndGet();
            if (i % 50 == 0) {
                System.out.println(i + " of " + num_odpairs + " o-d pairs processed.");
            }
        }
        );

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

        String defaultRow = od_id;
        for (int i = 0; i <= maxstep; i = i + stepsize) {
            defaultRow = defaultRow + ",";
        }
        defaultRow = defaultRow + System.getProperty("line.separator");

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
        int numpaths = paths.size();

        if (numpaths == 0) {
            System.out.println(route + ": No paths - skipping.");
            for (String optimization : optimizations) {
                responses.put(optimization, defaultRow);
            }
            return responses;
        }

        /**
         *   Algorithm Order:
         *   1. Most Beautiful
         *   2. Fastest
         *   3. Vary Beauty/Fastest by 10%
         *   4. Simplest
         *   5. Vary Simplicity/Fastest by 10%
         *
         *   Metrics:
         *   1. Time
         *   2. What's being optimized - beauty or simplicity
         *
         */
        float[] beautyscores = new float[numpaths];
        long[] timescores = new long[numpaths];
        int numbins = Math.floorDiv(maxstep, stepsize) + 1;
        int[] bestindices = new int[numbins];

        // Store beauty values for all routes and min/max
        int j = 0;
        int bestidx = 0;
        int worstidx = 0;
        for (PathWrapper path : paths) {
            beautyscores[j] = getBeauty(path);
            timescores[j] = path.getTime();
            if (beautyscores[j] > beautyscores[bestidx]) {
                bestidx = j;
            } else if (beautyscores[j] < beautyscores[worstidx]) {
                worstidx = j;
            }
            j++;
        }
        // 100% beauty = most beautiful route idx; 0% beauty is by default already 0, the fastest path
        bestindices[numbins - 1] = bestidx;
        // fastest path = 1st, slowest path = last
        long fastest = paths.get(0).getTime();
        long slowest = paths.get(numpaths - 1).getTime();

        /*
        long baselinetime = paths.get(0).getTime();
        float baselinebeauty = getBeauty(paths.get(0));
        int baselinesimplicity = paths.get(0).getSimplicity();

        if (baselinebeauty == 0) {
            int i = 1;
            while (baselinebeauty == 0) {
                baselinebeauty = getBeauty(paths.get(i));
                i++;
            }
            System.out.println("\tUsed beauty for " + (i-1) + "th path for " + od_id);
        }
        */

        // Loop through 10% to 90% weightings, scoring each route on beauty to determine most beautiful
        for (int i = 1; i < numbins - 1; i = i + 1) {
            float bestscore = -1;
            float combinedscore;
            float beautyscore;
            float timescore;
            int bestcomboidx = -1;
            for (j=0; j<numpaths; j++) {
                beautyscore = (beautyscores[j] - beautyscores[worstidx]) / (beautyscores[bestidx] - beautyscores[worstidx]);
                timescore = 1 - (((float) timescores[j] - fastest) / (slowest - fastest));
                combinedscore = (i * beautyscore) + ((stepsize - i) * timescore);
                if (combinedscore > bestscore) {
                    bestscore = combinedscore;
                    bestcomboidx = j;
                }
            }
            bestindices[i] = bestcomboidx;
        }
        responses.put("beauty_beauty", writeOutput(od_id, beautyscores, bestindices));
        responses.put("beauty_time", writeOutput(od_id, timescores, bestindices));


        /* Find least-beautiful route within similar distance constraints
        double beautyDistance = paths.get(routeidx).getDistance();
        j = 0;
        bestscore = 1000;
        routeidx = -1;
        double uglydistance;
        for (PathWrapper path : paths) {
            uglydistance = path.getDistance();
            if (uglydistance / beautyDistance < 1.05 && uglydistance / beautyDistance > 0.95) {
                float score = getBeauty(path);
                if (score < bestscore) {
                    bestscore = score;
                    routeidx = j;
                }
            }
            j++;
        }
        responses.put("ugly", writeOutput(route, "Wrst", "ugly", od_id, paths.get(routeidx), bestscore, getNumCTs(paths.get(routeidx))));
        */

        // Simplest Route
        int[] simplescores = new int[numpaths];
        bestindices = new int[numbins];

        // Store beauty values for all routes and min/max
        j = 0;
        bestidx = 0;
        worstidx = 0;
        for (PathWrapper path : paths) {
            simplescores[j] = path.getSimplicity();
            if (simplescores[j] < simplescores[bestidx]) {
                bestidx = j;
            } else if (simplescores[j] > simplescores[worstidx]) {
                worstidx = j;
            }
            j++;
        }
        // 100% simple = simplest route idx; 0% simplicity is by default already 0, the fastest path
        bestindices[numbins - 1] = bestidx;

        // Loop through 10% to 90% weightings, scoring each route on beauty to determine most beautiful
        for (int i = 1; i < numbins - 1; i = i + 1) {
            float bestscore = -1;
            float combinedscore;
            float simplescore;
            float timescore;
            int bestcomboidx = -1;
            for (j=0; j<numpaths; j++) {
                simplescore = 1 - (((float) simplescores[j] - simplescores[bestidx]) / (simplescores[worstidx] - simplescores[bestidx]));
                timescore = 1 - (((float) timescores[j] - fastest) / (slowest - fastest));
                combinedscore = (i * simplescore) + ((stepsize - i) * timescore);
                if (combinedscore > bestscore) {
                    bestscore = combinedscore;
                    bestcomboidx = j;
                }
            }
            bestindices[i] = bestcomboidx;
        }
        responses.put("simple_simple", writeOutput(od_id, simplescores, bestindices));
        responses.put("simple_time", writeOutput(od_id, timescores, bestindices));

        /*
        //System.out.println("Simplest route: " + routeidx);
        responses.put("simple", writeOutput(route, "Simp", "simple", od_id, paths.get(routeidx), beauty, getNumCTs(paths.get(routeidx))));
        float minSimplicity = bestscore;

        // Fastest Route
        PathWrapper bestPath = paths.get(0);
        beauty = getBeauty(bestPath);
        responses.put("fast", writeOutput(route, "Fast", "Fastest", od_id, bestPath, beauty, getNumCTs(bestPath)));

        // Beautifully simple route
        j = 0;
        bestscore = 0;
        routeidx = 0;
        float combined;
        for (PathWrapper path : paths) {
            combined = (minSimplicity / path.getSimplicity()) + (getBeauty(path) / maxBeauty);
            if (combined > bestscore) {
                bestscore = combined;
                routeidx = j;
            }
            j++;
        }
        //System.out.println("Most beautiful-simple route: " + routeidx);
        responses.put("besi", writeOutput(route, "BeSi", "beauty-simple", od_id, paths.get(routeidx), getBeauty(paths.get(routeidx)), getNumCTs(paths.get(routeidx))));
        */

        /* Fewest # directions
        j = 0;
        bestscore = 10000;
        routeidx = 0;
        InstructionList il;
        int numDirections;
        for (PathWrapper path : paths) {
            il = path.getInstructions();
            numDirections = il.getSize();
            if (numDirections < bestscore) {
                bestscore = numDirections;
                routeidx = j;
            }
            j++;
        }
        responses.put("mindirections", writeOutput(route, "MnDi", "mindirections", od_id, paths.get(routeidx), getBeauty(paths.get(routeidx)), getNumCTs(paths.get(routeidx))));
        */

        /* Shortest Route
        req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                setWeighting("shortest").
                setVehicle("car").
                setLocale(Locale.US).
                setAlgorithm("dijkstrabi");
        rsp = hopper.route(req);

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            System.out.println(rsp.getErrors().toString());
            System.out.println(route + ": Skipping shortest path.");
            responses.put("short", defaultRow);
        } else {
            // Get shortest path
            bestPath = rsp.getBest();
            beauty = getBeauty(bestPath);
            responses.put("short", writeOutput(route, "Shrt", "shortest", od_id, bestPath, beauty, getNumCTs(bestPath)));
        }
         */

        return responses;
    }

    public static void main(String[] args) throws Exception {

        // PBFs from: https://mapzen.com/data/metro-extracts/

        //String city = args[0];
        //String odtype = args[1];
        String city = "lon";  // sf, nyc, chi, lon, man, sin
        String odtype = "grid";  // grid, rand
        parallelKSP_tradeoffs ksp = new parallelKSP_tradeoffs(city, odtype);
        boolean getghroutes = true;

        if (getghroutes) {
            ksp.setDataSources();
            ksp.getGridValues();
            ksp.prepareGraphHopper();
            //ksp.getGridCTs();
            ksp.setODPairs();
            ksp.process_routes();  // get Graphhopper routes
        }
    }
}
