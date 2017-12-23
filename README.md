# GraphHopper Routing Engine

GraphHopper is a fast and memory efficient Java road routing engine released under Apache License 2.0. Per default it uses OpenStreetMap data but can import other data sources.

This is a fork created by Tushar Chandra in December 2017. See my own [routing repository](https://github.com/tuchandra/routing) for an explanation of this research project.

## Differences

This fork includes the following classes:

`com.graphhopper.routing.weighting.TrafficWeighting.java` - implements the custom edge weighting based on traffic data.

`com.graphhopper.reader.osm.AlternativeRoutingExternalities.java` - depending on arguments provided, either starts map matching from Google Maps paths (which are sequences of arbitrary lat/lon points) to GraphHopper nodes, or starts traffic based routing, or starts default fastest path routing.

## Running AlternativeRoutingExternalities.java

I had luck running this file using the following:

```
java -Xmx1500 -Xms1500m -d64 -cp "reader-osm/target/graphhopper-reader-osm-0.8-SNAPSHOT-jar-with-dependencies.jar;map-matching/hmm-lib/target/hmm-lib-1.0.0-jar-with-dependencies.jar" com.graphhopper.reader.osm.AlternativeRoutingExternalities traffic
```

The last word can be either `matching`, `traffic`, or `default`.

To debug on Windows (because this was difficult for me to figure out), run the following in two different command windows:

```
java -Xmx1500m -Xms1500m -d64 -cp "reader-osm/target/graphhopper-reader-osm-0.8-SNAPSHOT-jar-with-dependencies.jar;map-matching/hmm-lib/target/hmm-lib-1.0.0-jar-with-dependencies.jar" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=6000 com.graphhopper.reader.osm.AlternativeRoutingExternalities traffic

jdb -connect com.sun.jdi.SocketAttach:port=6000
```

and then follow usual JDB procedures.
