package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;

import java.awt.*;
import java.util.*;

import java.io.*;
import java.util.List;


/**
 * Created by isaac on 09/14/16.
 */
public class runKSP {

    private static final TranslationMap trMap = new TranslationMap().doImport();
    private static final Translation usTR = trMap.getWithFallBack(Locale.US);
    private static final boolean outputAlternative = true;

    public static void process_routes(String city, String route_type, boolean useCH) throws Exception {

        // set paths
        String osmFile = "./reader-osm/files/";
        String graphFolder = "./reader-osm/target/tmp/";
        String inputPointsFN = "../data/intermediate/";
        String outputPointsFN = "../data/output/";
        ArrayList<String> gridValuesFNs = new ArrayList<>();
        String gvfnStem = "../data/intermediate/";
        if (city.equals("SF")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_graphhopper_routes_ksp.csv";
            gridValuesFNs.add(gvfnStem + "06075_logfractionempath_flickr.csv");
        } else if (city.equals("NYC")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "_graphhopper_routes_ksp.csv";
            gridValuesFNs.add(gvfnStem + "36005_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36047_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36061_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36081_beauty_flickr.csv");
            gridValuesFNs.add(gvfnStem + "36085_beauty_flickr.csv");
        } else if (city.equals("BOS")) {
            osmFile = osmFile + "boston_massachusetts.osm.pbf";
            graphFolder = graphFolder + "ghosm_bos";
            inputPointsFN = inputPointsFN + "bos_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "bos_" + route_type + "_graphhopper_routes_ksp.csv";
            gridValuesFNs.add(gvfnStem + "25025_beauty_twitter.csv");
        } else {
            return;
        }
        if (!useCH) {
            graphFolder = graphFolder + "_noch";
        }

        HashMap<String, Integer> gvHeaderMap = new HashMap<>();
        HashMap<String, Float> gridBeauty = new HashMap<>();
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
        // create one GraphHopper instance
        GraphHopper hopper = new GraphHopperOSM().forDesktop().setCHEnabled(false);
        hopper.setDataReaderFile(osmFile);
        // where to store graphhopper files?
        hopper.setGraphHopperLocation(graphFolder);
        hopper.setEncodingManager(new EncodingManager("car"));

        ArrayList<float[]> inputPoints = new ArrayList<float[]>();
        ArrayList<String> id_to_points = new ArrayList<String>();
        ArrayList<String> maneuvers = new ArrayList<>();
        Scanner sc_in = new Scanner(new File(inputPointsFN));
        FileWriter sc_out = new FileWriter(outputPointsFN, true);
        sc_out.write("ID,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers" +
                System.getProperty("line.separator"));
        FileWriter sc_out_alt;
        if (outputAlternative) {
            sc_out_alt = new FileWriter(outputPointsFN.replaceFirst(".csv","_alt.csv"), true);
            sc_out_alt.write("ID,polyline_points,total_time_in_sec,total_distance_in_meters,number_of_steps,maneuvers" +
                    System.getProperty("line.separator"));
        }
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

        // now this can take minutes if it imports or a few seconds for loading
        // of course this is dependent on the area you import
        hopper.importOrLoad();

        // simple configuration of the request object, see the GraphHopperServlet classs for more possibilities.
        float[] points;
        PointList pointList = null;
        //List<Map<String, Object>> iList = null;
        int routes_skipped = 0;
        for (int i=0; i<numPairs; i++) {
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
                sc_out.write(od_id + "," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                        + ")]\"," + "-1,-1,-1,[]" + System.getProperty("line.separator"));
                if (outputAlternative) {
                    sc_out_alt.write(od_id + "," + "\"[(" + points[0] + "," + points[1] + "),(" + points[2] + "," + points[3]
                            + ")]\"," + "-1,-1,-1,[]" + System.getProperty("line.separator"));
                }
                routes_skipped++;
                continue;
            }

            // use the best path, see the GHResponse class for more possibilities.
            List<PathWrapper> paths = rsp.getAll();
            HashSet<String> roundedPoints;
            int j = 0;
            float maxscore = -1000;
            int maxidx = 0;
            for (PathWrapper path: paths) {
                roundedPoints = path.roundPoints();
                float score = 0;
                for (String pt : roundedPoints) {
                    if (gridBeauty.containsKey(pt)) {
                        score = score + gridBeauty.get(pt);
                    }
                }
                score = score / roundedPoints.size();
                if (score > maxscore) {
                    maxscore = score;
                    maxidx = j;
                }
                j++;
            }

            PathWrapper bestPath = paths.get(maxidx);
            // points, distance in meters and time in seconds (convert from ms) of the full path
            pointList = bestPath.getPoints();
            double distance = Math.round(bestPath.getDistance() * 100) / 100;
            long timeInSec = bestPath.getTime() / 1000;
            InstructionList il = bestPath.getInstructions();
            int numDirections = il.getSize();
            // iterate over every turn instruction
            maneuvers.clear();
            for (Instruction instruction : il) {
                maneuvers.add(instruction.getSimpleTurnDescription());
                // System.out.println(instruction.getTurnDescription(usTR) + " for " + instruction.getDistance() + " meters.");
            }

            sc_out.write(od_id + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                    ",\"" + maneuvers.toString() + "\"" + System.getProperty("line.separator"));
            System.out.println(i + ": Distance: " + Math.round(paths.get(0).getDistance() * 100) / 100 + "m;\tTime: " + paths.get(0).getTime() / 1000 + "sec;\t# Directions: " + paths.get(0).getInstructions().getSize());
            System.out.println(i + ": Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tScore: " + maxscore);

            int k = 0;
            int minidx = 0;
            float minscore = 1000;
            if (outputAlternative) {
                double altdistance;
                for (PathWrapper path: paths) {
                    altdistance = path.getDistance();
                    if (altdistance / distance > 1.05 || altdistance / distance < 0.95) {
                        continue;
                    }
                    roundedPoints = path.roundPoints();
                    float score = 0;
                    for (String pt : roundedPoints) {
                        if (gridBeauty.containsKey(pt)) {
                            score = score + gridBeauty.get(pt);
                        }
                    }
                    score = score / roundedPoints.size();
                    if (score < minscore) {
                        minscore = score;
                        minidx = k;
                    }
                    k++;
                }
                bestPath = paths.get(minidx);
                // points, distance in meters and time in seconds (convert from ms) of the full path
                pointList = bestPath.getPoints();
                distance = Math.round(bestPath.getDistance() * 100) / 100;
                timeInSec = bestPath.getTime() / 1000;
                il = bestPath.getInstructions();
                numDirections = il.getSize();
                // iterate over every turn instruction
                maneuvers.clear();
                for (Instruction instruction : il) {
                    maneuvers.add(instruction.getSimpleTurnDescription());
                    // System.out.println(instruction.getTurnDescription(usTR) + " for " + instruction.getDistance() + " meters.");
                }
                sc_out_alt.write(od_id + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                        ",\"" + maneuvers.toString() + "\"" + System.getProperty("line.separator"));
                System.out.println(i + ": Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections + ";\tScore: " + minscore);

            }
            paths.clear();

            // or get the json
            //iList = il.createJson();
            //System.out.println("JSON: " + iList);

            // or get the result as gpx entries:
            //List<GPXEntry> list = il.createGPXList();
            //System.out.println("GPX: " + list);
        }
        // example JSON
        //System.out.println("Example JSON: " + iList);
        System.out.println(routes_skipped + " routes skipped out of " + numPairs);
        sc_out.close();
    }

    public static void main(String[] args) throws Exception {

        // PBF from: https://mapzen.com/data/metro-extracts/
        // NYC Grid
        //process_routes("NYC", "grid", true);
        // NYC Random
        //process_routes("NYC", "rand", true);
        // SF Grid
        process_routes("SF", "grid", false);
        // SF Random
        //process_routes("SF", "rand", true);
        //process_routes("BOS", "check", true);
    }
}
