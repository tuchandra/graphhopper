package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.EncodingManager;
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
    private String outputPointsFN = "../data/output/";
    private String gvfnStem = "../data/intermediate/";
    private ArrayList<String> gridValuesFNs = new ArrayList<>();
    private HashMap<String, Integer> gvHeaderMap;
    private HashMap<String, Float> gridBeauty;
    private GraphHopper hopper;

    public runKSP(String city, String route_type) {

        this.city = city;
        this.route_type = route_type;
        this.outputFiles = new ArrayList<>(4);
    }

    public void setCiy(String city) {
        this.city = city;
    }

    public void setRouteType(String route_type) {
        this.route_type = route_type;
    }

    public void setDataSources() throws Exception {
        if (city.equals("SF")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf_noch";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_flickr.csv");
        } else if (city.equals("NYC")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc_noch";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "36005_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36047_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36061_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36081_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36085_beauty_flickr.csv");
        } else if (city.equals("BOS")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos_noch";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "gh_routes.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
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
                rc = vals[gvHeaderMap.get("rid")] + "," + vals[gvHeaderMap.get("cid")];
                beauty = Float.valueOf(vals[gvHeaderMap.get("beauty")]);
                gridBeauty.put(rc, beauty);
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

    public void writeOutput(FileWriter fw, int i, String optimized, String od_id, PathWrapper bestPath, float score) throws IOException {

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

        fw.write(od_id + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                ",\"" + maneuvers.toString() + "\"" + "," + score + "," + simplicity + System.getProperty("line.separator"));
        System.out.println(i + " (" + optimized + "): Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tSimplicity: " + simplicity + ";\tScore: " + score);

    }

    public void process_routes() throws Exception {
        ArrayList<float[]> inputPoints = new ArrayList<float[]>();
        ArrayList<String> id_to_points = new ArrayList<String>();

        // Prep Filewriters (Optimized, Worst-but-same-distance, Fastest, Simplest)
        String outputheader = "ID,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers,beauty,simplicity" +
                System.getProperty("line.separator");
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_beauty.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_ugly.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_fast.csv"), true));
        outputFiles.add(new FileWriter(outputPointsFN.replaceFirst(".csv","_simple.csv"), true));

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

            // Get Routes
            points = inputPoints.get(i);
            od_id = id_to_points.get(i);
            GHRequest req = new GHRequest(points[0], points[1], points[2], points[3]).  // latFrom, lonFrom, latTo, lonTo
                    setWeighting("fastest").
                    setVehicle("car").
                    setLocale(Locale.US).
                    setAlgorithm("ksp");
            GHResponse rsp = hopper.route(req);
            System.out.println("Num Responses: " + rsp.getAll().size());

            // first check for errors
            if (rsp.hasErrors()) {
                // handle them!
                System.out.println(rsp.getErrors().toString());
                System.out.println(i + ": Skipping.");
                String outputRow = od_id + "," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                        + ")]\"," + "-1,-1,-1,[]" + System.getProperty("line.separator");
                for (FileWriter fw: outputFiles) {
                    fw.write(outputRow);
                }
                routes_skipped++;
                continue;
            }

            // Get All Routes (up to 10K right now)
            List<PathWrapper> paths = rsp.getAll();

            // Score each route on beauty to determine most beautiful
            HashSet<String> roundedPoints;
            int j = 0;
            float bestscore = -1000;
            int routeidx = -1;
            for (PathWrapper path : paths) {
                roundedPoints = path.roundPoints();
                float score = 0;
                for (String pt : roundedPoints) {
                    if (gridBeauty.containsKey(pt)) {
                        score = score + gridBeauty.get(pt);
                    }
                }
                score = score / roundedPoints.size();
                if (score > bestscore) {
                    bestscore = score;
                    routeidx = j;
                }
                j++;
            }
            writeOutput(outputFiles.get(0), i, "Best", od_id, paths.get(routeidx), bestscore);

            // Find least-beautiful route within similar distance constraints
            double beautyDistance = paths.get(routeidx).getDistance();
            j = 0;
            bestscore = 1000;
            routeidx = -1;
            double uglydistance;
            for (PathWrapper path : paths) {
                uglydistance = path.getDistance();
                if (uglydistance / beautyDistance < 1.05 && uglydistance / beautyDistance > 0.95) {
                    roundedPoints = path.roundPoints();
                    float score = 0;
                    for (String pt : roundedPoints) {
                        if (gridBeauty.containsKey(pt)) {
                            score = score + gridBeauty.get(pt);
                        }
                    }
                    score = score / roundedPoints.size();
                    if (score < bestscore) {
                        bestscore = score;
                        routeidx = j;
                    }
                }
                j++;
            }
            writeOutput(outputFiles.get(1), i, "Wrst", od_id, paths.get(routeidx), bestscore);

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
                    roundedPoints = path.roundPoints();
                    beauty = 0;
                    for (String pt : roundedPoints) {
                        if (gridBeauty.containsKey(pt)) {
                            beauty = beauty + gridBeauty.get(pt);
                        }
                    }
                    beauty = beauty / roundedPoints.size();
                }
                j++;
            }
            writeOutput(outputFiles.get(2), i, "Simp", od_id, paths.get(routeidx), beauty);

            // Fastest Route
            PathWrapper bestPath = paths.get(0);
            roundedPoints = bestPath.roundPoints();
            beauty = 0;
            for (String pt : roundedPoints) {
                if (gridBeauty.containsKey(pt)) {
                    beauty = beauty + gridBeauty.get(pt);
                }
            }
            beauty = beauty / roundedPoints.size();
            writeOutput(outputFiles.get(3), i, "Fast", od_id, bestPath, beauty);
        }

        // Finished analysis: close filewriters and indicate how many paths skipped
        System.out.println(routes_skipped + " routes skipped out of " + numPairs);
        for (FileWriter fw : outputFiles) {
            fw.close();
        }
    }

    public static void main(String[] args) throws Exception {

        // PBF from: https://mapzen.com/data/metro-extracts/
        // NYC Grid
        //process_routes("NYC", "grid", true);
        // NYC Random
        //process_routes("NYC", "rand", true);
        // SF Grid
        runKSP SFGrid = new runKSP("SF", "grid");
        SFGrid.setDataSources();
        SFGrid.getGridValues();
        SFGrid.prepareGraphHopper();
        SFGrid.process_routes();
        // SF Random
        //process_routes("SF", "rand", true);
        //process_routes("BOS", "check", true);
    }
}
