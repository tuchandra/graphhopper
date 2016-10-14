package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;

import java.util.*;

import java.io.*;


/**
 * Created by isaac on 3/8/16.
 */
public class runKSP {

    private static final TranslationMap trMap = new TranslationMap().doImport();
    private static final Translation usTR = trMap.getWithFallBack(Locale.US);

    public static void process_routes(String city, String route_type) throws Exception {

        // set paths
        String osmFile = "./reader-osm/files/";
        String graphFolder = "./reader-osm/target/tmp/";
        String inputPointsFN = "../data/output/";
        String outputPointsFN = "../data/output/";
        if (city.equals("SF")) {
            osmFile = osmFile + "san-francisco-bay_california.osm.pbf";
            graphFolder = graphFolder + "ghosm_sf";
            inputPointsFN = inputPointsFN + "sf_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "sf_" + route_type + "_graphhopper_routes.csv";
        } else if (city.equals("NYC")) {
            osmFile = osmFile + "new-york_new-york.osm.pbf";
            graphFolder = graphFolder + "ghosm_nyc";
            inputPointsFN = inputPointsFN + "nyc_" + route_type + "_od_pairs.csv";
            outputPointsFN = outputPointsFN + "nyc_" + route_type + "_graphhopper_routes_TEST.csv";
        } else {
            return;
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
        List<Map<String, Object>> iList = null;
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
                routes_skipped++;
                continue;
            }

            // use the best path, see the GHResponse class for more possibilities.
            List<PathWrapper> paths = rsp.getAll();
            for (PathWrapper path : paths) {

                // points, distance in meters and time in seconds (convert from ms) of the full path
                pointList = path.getPoints();
                double distance = Math.round(path.getDistance() * 100) / 100;
                long timeInSec = path.getTime() / 1000;
                InstructionList il = path.getInstructions();
                int numDirections = il.getSize();
                // iterate over every turn instruction
                maneuvers.clear();
                for (Instruction instruction : il) {
                    maneuvers.add(instruction.getSimpleTurnDescription());
                    // System.out.println(instruction.getTurnDescription(usTR) + " for " + instruction.getDistance() + " meters.");
                }
                sc_out.write(od_id + "," + "\"[" + pointList + "]\"," + timeInSec + "," + distance + "," + numDirections +
                        "," + maneuvers.toString() + System.getProperty("line.separator"));
                System.out.println(i + ": Distance: " + distance + "m;\tTime: " + timeInSec + "sec;\t# Directions: " + numDirections);
            }
            break;

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
        process_routes("NYC", "grid");
        // NYC Random
        //process_routes("NYC", "rand");
        // SF Grid
        //process_routes("SF", "grid");
        // SF Random
        //process_routes("SF", "rand");
    }
}
