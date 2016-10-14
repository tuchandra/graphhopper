package com.graphhopper.routing.ksp;

/**
 * Created by brandonsmock on 6/1/15.
 */
import java.util.*;

public final class DijkstraKSP {

    private DijkstraKSP() {}

    public static ShortestPathTreeKSP shortestPathTree(GraphKSP graph, String sourceLabel) throws Exception {
        HashMap<String,NodeKSP> nodes = graph.getNodes();
        if (!nodes.containsKey(sourceLabel))
            throw new Exception("Source node not found in graph.");
        ShortestPathTreeKSP predecessorTree = new ShortestPathTreeKSP(sourceLabel);
        Set<DijkstraNodeKSP> visited = new HashSet<DijkstraNodeKSP>();
        PriorityQueue<DijkstraNodeKSP> pq = new PriorityQueue<DijkstraNodeKSP>();
        for (String nodeLabel:nodes.keySet()) {
            DijkstraNodeKSP newNode = new DijkstraNodeKSP(nodeLabel);
            newNode.setDist(Double.MAX_VALUE);
            newNode.setDepth(Integer.MAX_VALUE);
            predecessorTree.add(newNode);
        }
        DijkstraNodeKSP sourceNode = predecessorTree.getNodes().get(predecessorTree.getRoot());
        sourceNode.setDist(0);
        sourceNode.setDepth(0);
        pq.add(sourceNode);

        int count = 0;
        while (!pq.isEmpty()) {
            DijkstraNodeKSP current = pq.poll();
            String currLabel = current.getLabel();
            visited.add(current);
            count++;
            HashMap<String, Double> neighbors = nodes.get(currLabel).getNeighbors();
            for (String currNeighborLabel:neighbors.keySet()) {
                DijkstraNodeKSP neighborNode = predecessorTree.getNodes().get(currNeighborLabel);
                Double currDistance = neighborNode.getDist();
                Double newDistance = current.getDist() + nodes.get(currLabel).getNeighbors().get(currNeighborLabel);
                if (newDistance < currDistance) {
                    DijkstraNodeKSP neighbor = predecessorTree.getNodes().get(currNeighborLabel);

                    pq.remove(neighbor);
                    neighbor.setDist(newDistance);
                    neighbor.setDepth(current.getDepth() + 1);
                    neighbor.setParent(currLabel);
                    pq.add(neighbor);
                }
            }
        }

        return predecessorTree;
    }

    public static PathKSP shortestPath(GraphKSP graph, String sourceLabel, String targetLabel) throws Exception {
        //if (!nodes.containsKey(sourceLabel))
        //    throw new Exception("Source node not found in graph.");
        HashMap<String,NodeKSP> nodes = graph.getNodes();
        ShortestPathTreeKSP predecessorTree = new ShortestPathTreeKSP(sourceLabel);
        PriorityQueue<DijkstraNodeKSP> pq = new PriorityQueue<DijkstraNodeKSP>();
        for (String nodeLabel:nodes.keySet()) {
            DijkstraNodeKSP newNode = new DijkstraNodeKSP(nodeLabel);
            newNode.setDist(Double.MAX_VALUE);
            newNode.setDepth(Integer.MAX_VALUE);
            predecessorTree.add(newNode);
        }
        DijkstraNodeKSP sourceNode = predecessorTree.getNodes().get(predecessorTree.getRoot());
        sourceNode.setDist(0);
        sourceNode.setDepth(0);
        pq.add(sourceNode);

        int count = 0;
        while (!pq.isEmpty()) {
            DijkstraNodeKSP current = pq.poll();
            String currLabel = current.getLabel();
            if (currLabel.equals(targetLabel)) {
                PathKSP shortestPath = new PathKSP();
                String currentN = targetLabel;
                String parentN = predecessorTree.getParentOf(currentN);
                while (parentN != null) {
                    shortestPath.addFirst(new EdgeKSP(parentN,currentN,nodes.get(parentN).getNeighbors().get(currentN)));
                    currentN = parentN;
                    parentN = predecessorTree.getParentOf(currentN);
                }
                return shortestPath;
            }
            count++;
            HashMap<String, Double> neighbors = nodes.get(currLabel).getNeighbors();
            for (String currNeighborLabel:neighbors.keySet()) {
                DijkstraNodeKSP neighborNode = predecessorTree.getNodes().get(currNeighborLabel);
                Double currDistance = neighborNode.getDist();
                Double newDistance = current.getDist() + nodes.get(currLabel).getNeighbors().get(currNeighborLabel);
                if (newDistance < currDistance) {
                    DijkstraNodeKSP neighbor = predecessorTree.getNodes().get(currNeighborLabel);

                    pq.remove(neighbor);
                    neighbor.setDist(newDistance);
                    neighbor.setDepth(current.getDepth() + 1);
                    neighbor.setParent(currLabel);
                    pq.add(neighbor);
                }
            }
        }

        return null;
    }
}
