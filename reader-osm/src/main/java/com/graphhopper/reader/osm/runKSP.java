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

import java.util.*;

import java.io.*;
import java.util.List;


/**
 * Created by isaac on 09/14/16.
 */
public class runKSP {

    String city;
    String route_type;
    ArrayList<FileWriter> outputFiles;
    private String osmFile = "./reader-osm/files/";
    private String graphFolder = "./reader-osm/target/tmp/";
    private String inputPointsFN = "../data/intermediate/";
    private String outputPointsFN = "../data/testing/";
    private String gvfnStem = "../data/intermediate/";
    private String gctfnStem = "../geometries/";
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private ArrayList<String> gridCTsFNs = new ArrayList<>();
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private HashMap<String, Integer> gridCT;
    private GraphHopper hopper;
    private MapMatching mapMatching;
    private String outputheader = "ID,name,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers,beauty,simplicity,numCTs" +
            System.getProperty("line.separator");

    public runKSP(String city, String route_type) {

        this.city = city;
        this.route_type = route_type;
        this.outputFiles = new ArrayList<>(4);
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
        int latIdx = -1;
        int lonIdx = -1;
        int timeIdx = -1;
        for (int i=0; i<pointsHeader.length; i++) {
            if (pointsHeader[i].equalsIgnoreCase("ID")) {
                idIdx = i;
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
        String line;
        String[] vals;
        String routeID = "";
        String prevRouteID = "";
        double lat;
        double lon;
        long time;
        ArrayList<GPXEntry> pointsList = new ArrayList<>();
        PathWrapper path;
        FileWriter sc_out = new FileWriter(fout, true);
        sc_out.write(outputheader);
        int i = 0;
        float score;
        while (sc_in.hasNext()) {
            line = sc_in.nextLine();
            vals = line.split(",");
            routeID = vals[idIdx];
            lat = Double.valueOf(vals[latIdx]);
            lon = Double.valueOf(vals[lonIdx]);
            time = Long.valueOf(vals[timeIdx]);
            GPXEntry pt = new GPXEntry(lat, lon, time);
            if (routeID.equalsIgnoreCase(prevRouteID)) {
                pointsList.add(pt);
            }
            else if (pointsList.size() > 0) {
                path = GPXToPath(pointsList);
                //path = trimPath(path, pointsList);
                if (path.getDistance() > 0) {
                    score = getBeauty(path);
                    writeOutput(sc_out, i, "ExtAPI", prevRouteID, path, score, getNumCTs(path));
                }
                pointsList.clear();
                i++;
                pointsList.add(pt);
                if (i % 10 == 0) {
                    for (FileWriter fw : outputFiles) {
                        fw.flush();
                    }
                }
            }
            prevRouteID = routeID;
        }
        if (pointsList.size() > 0) {
            path = GPXToPath(pointsList);
            if (path.getDistance() > 0) {
                score = getBeauty(path);
                writeOutput(sc_out, i, "ExtAPI", prevRouteID, path, score, getNumCTs(path));
            }
        }
        sc_out.close();
        sc_in.close();
    }

    //TODO: find some way to match path to virtual nodes at start/finish or hope map-matcher updates
    public PathWrapper trimPath(PathWrapper path, ArrayList<GPXEntry> original) {
        return new PathWrapper();
    }


    public void setDataSources() throws Exception {
        if (city.equals("SF")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf_noch";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_ft.csv");
            gridCTsFNs.add(gctfnStem + "06075_ct_grid.csv");
        } else if (city.equals("NYC")) {
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
        } else if (city.equals("BOS")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos_noch";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
            gridCTsFNs.add(gctfnStem + "25025_ct_grid.csv");
        } else {
            throw new Exception("Invalid Parameters: city must be of 'SF','NYC', or 'BOS' and route_type of 'grid' or 'rand'");
        }
    }

    public void getGridValues() throws Exception {
        gvHeaderMap = new HashMap<>();
        gridBeauty = new HashMap<>();

        for (String fn : gridValuesFNs) {
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

        }
    }

    public void getGridCTs() throws Exception {
        gridCT = new HashMap<>();
        for (String fn : gridCTsFNs) {
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


    public void writeOutput(FileWriter fw, int i, String optimized, String od_id, PathWrapper bestPath, float score, int numCTs) throws IOException {

        // points, distance in meters and time in seconds (convert from ms) of the full path
        PointList pointList = bestPath.getPoints();
        int simplicity = bestPath.getSimplicity();
        double distance = Math.round(bestPath.getDistance() * 100) / 100;
        long timeInSec = bestPath.getTime() / 1000;
        InstructionList il = bestPath.getInstructions();
        int numDirections = il.getSize();
        // iterate over every turn instruction
        ArrayList<String> maneuvers = new ArrayList<>();
        for (Instruction instruction : il) {
            maneuvers.add(instruction.getSimpleTurnDescription());
        }
        String routetype = "main";
        if (optimized.equalsIgnoreCase("altn")) {
            routetype = "alternative";
        }

        fw.write(od_id + "," + routetype + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                ",\"" + maneuvers.toString() + "\"" + "," + score + "," + simplicity + "," + numCTs + System.getProperty("line.separator"));
        System.out.println(i + " (" + optimized + "): Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tSimplicity: " + simplicity + ";\tScore: " + score + ";\tNumCts: " + numCTs);

    }

    public int getNumCTs(PathWrapper path) {
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


    public void process_routes() throws Exception {
        ArrayList<float[]> inputPoints = new ArrayList<float[]>();
        ArrayList<String> id_to_points = new ArrayList<String>();

        // Prep Filewriters (Optimized, Worst-but-same-distance, Fastest, Simplest)
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_beauty.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_ugly.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_simple.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_fast.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_shortest.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_alt.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_besi.csv"), true));

        for (FileWriter fw : outputFiles) {
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

        // Loop through origin-destination pairs, processing each one for beauty, non-beautiful matched, fastest, and simplest
        float[] points;
        int routes_skipped = 0;
        for (int i=0; i<numPairs; i++) {

            if (i % 50 == 0) {
                for (FileWriter fw : outputFiles) {
                    fw.flush();
                }
            }

            // Get Routes
            points = inputPoints.get(i);
            od_id = id_to_points.get(i);
            GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                    setWeighting("fastest").
                    setVehicle("car").
                    setLocale(Locale.US).
                    setAlgorithm("ksp");
            GHResponse rsp = hopper.route(req);

            // first check for errors
            if (rsp.hasErrors()) {
                // handle them!
                System.out.println(rsp.getErrors().toString());
                System.out.println(i + ": Skipping.");
                String outputRow = od_id + ",main," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                        + ")]\"," + "-1,-1,-1,[],-1,-1,-1" + System.getProperty("line.separator");
                for (FileWriter fw: outputFiles) {
                    fw.write(outputRow);
                }
                routes_skipped++;
                continue;
            }

            // Get All Routes (up to 10K right now)
            List<PathWrapper> paths = rsp.getAll();
            System.out.println("Num Responses: " + paths.size());

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
            writeOutput(outputFiles.get(0), i, "Best", od_id, paths.get(routeidx), bestscore, getNumCTs(paths.get(routeidx)));
            float maxBeauty = bestscore;

            // Find least-beautiful route within similar distance constraints
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
            writeOutput(outputFiles.get(1), i, "Wrst", od_id, paths.get(routeidx), bestscore, getNumCTs(paths.get(routeidx)));

            // Simplest Route
            j = 0;
            bestscore = 10000;
            routeidx = 0;
            float beauty = -1;
            for (PathWrapper path : paths) {
                int score = path.getSimplicity();
                if (score < bestscore) {
                    bestscore = score;
                    routeidx = j;
                    beauty = getBeauty(path);
                }
                j++;
            }
            writeOutput(outputFiles.get(2), i, "Simp", od_id, paths.get(routeidx), beauty, getNumCTs(paths.get(routeidx)));
            float minSimplicity = bestscore;

            // Fastest Route
            PathWrapper bestPath = paths.get(0);
            beauty = getBeauty(bestPath);
            writeOutput(outputFiles.get(3), i, "Fast", od_id, bestPath, beauty, getNumCTs(bestPath));

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
            writeOutput(outputFiles.get(6), i, "BeSi", od_id, paths.get(routeidx), getBeauty(paths.get(routeidx)), getNumCTs(paths.get(routeidx)));

            // Shortest Route
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
                System.out.println(i + ": Skipping shortest path.");
                continue;
            }

            // Get shortest path
            bestPath = rsp.getBest();
            beauty = getBeauty(bestPath);
            writeOutput(outputFiles.get(4), i, "Shrt", od_id, bestPath, beauty, getNumCTs(bestPath));

            // Alternative Route
            req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                    setWeighting("fastest").
                    setVehicle("car").
                    setLocale(Locale.US).
                    setAlgorithm("alternative_route");
            rsp = hopper.route(req);

            // first check for errors
            if (rsp.hasErrors()) {
                // handle them!
                System.out.println(rsp.getErrors().toString());
                System.out.println(i + ": Skipping alternative path.");
                String outputRow = od_id + ",alternative," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                        + ")]\"," + "-1,-1,-1,[],-1,-1,-1" + System.getProperty("line.separator");
                FileWriter fw = outputFiles.get(5);
                fw.write(outputRow);
                continue;
            }

            // Get Alt Routes (should be 2, of which first is the fastest path)
            paths = rsp.getAll();
            if (paths.size() < 2) {
                System.out.println(i + ": Did not return an alternative path.");
                String outputRow = od_id + ",alternative," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                        + ")]\"," + "-1,-1,-1,[],-1,-1,-1" + System.getProperty("line.separator");
                outputFiles.get(5).write(outputRow);
                continue;
            }
            PathWrapper altpath = paths.get(1);
            beauty = getBeauty(altpath);
            writeOutput(outputFiles.get(5), i, "Altn", od_id, altpath, beauty, getNumCTs(altpath));


        }

        // Finished analysis: close filewriters and indicate how many paths skipped
        System.out.println(routes_skipped + " routes skipped out of " + numPairs);
        for (FileWriter fw : outputFiles) {
            fw.close();
        }
    }

    public static void main(String[] args) throws Exception {

        // PBFs from: https://mapzen.com/data/metro-extracts/

        // SF Grid
        runKSP ksp = new runKSP("SF", "grid");

        // SF Random
        //runKSP ksp = new runKSP("SF", "rand");

        // NYC Grid
        //runKSP ksp = new runKSP("NYC", "grid");

        // NYC Random
        //runKSP ksp = new runKSP("NYC", "rand");

        // BOS Check
        //runKSP ksp = new runKSP("BOS", "check");

        //ksp.setDataSources();
        //ksp.getGridValues();
        //ksp.prepareGraphHopper();
        //ksp.prepMapMatcher();  // score external API routes
        //ksp.PointsToPath("../data/output/sf_grid_mapquest_gpx.csv", "../data/output/sf_grid_mapquest_ghenhanced_sigma100_transitionDefault.csv");
        //ksp.PointsToPath("../data/output/sf_grid_google_gpx.csv", "../data/output/sf_grid_google_ghenhanced_sigma100_transitionDefault.csv");
        //ksp.PointsToPath("../data/output/nyc_grid_mapquest_gpx.csv", "../data/output/nyc_grid_mapquest_ghenhanced_sigma100_transitionDefault.csv");
        //ksp.PointsToPath("../data/output/nyc_grid_google_gpx.csv", "../data/output/nyc_grid_google_ghenhanced_sigma100_transitionDefault.csv");

        ksp.setDataSources();
        ksp.getGridValues();
        ksp.prepareGraphHopper();
        ksp.getGridCTs();
        ksp.process_routes();  // get Graphhopper routes
    }
}
