package com.graphhopper.routing.ksp;

import java.util.HashMap;

/**
 * Created by brandonsmock on 6/8/15.
 */
public class ShortestPathTreeKSP {
    private HashMap<String,DijkstraNodeKSP> nodes;
    private final String root;

    public ShortestPathTreeKSP() {
        this.nodes = new HashMap<String, DijkstraNodeKSP>();
        this.root = "";
    }

    public ShortestPathTreeKSP(String root) {
        this.nodes = new HashMap<String, DijkstraNodeKSP>();
        this.root = root;
    }

    public HashMap<String, DijkstraNodeKSP> getNodes() {
        return nodes;
    }

    public void setNodes(HashMap<String, DijkstraNodeKSP> nodes) {
        this.nodes = nodes;
    }

    public String getRoot() {
        return root;
    }

    public void add(DijkstraNodeKSP newNode) {
        nodes.put(newNode.getLabel(),newNode);
    }

    public void setParentOf(String node, String parent) {
//        if (parent != null && !nodes.containsKey(parent)) {
//            System.out.println("Warning: parent node not present in tree.");
//        }
        if (!nodes.containsKey(node))
            nodes.put(node,new DijkstraNodeKSP(node));

        nodes.get(node).setParent(parent);

    }

    public String getParentOf(String node) {
        if (nodes.containsKey(node))
            return nodes.get(node).getParent();
        else
            return null;
    }
}
