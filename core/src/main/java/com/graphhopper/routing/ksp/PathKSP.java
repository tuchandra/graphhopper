package com.graphhopper.routing.ksp;

import java.util.*;

/**
 * The Path class implements a path in a weighted, directed graph as a sequence of Edges.
 *
 * Created by Brandon Smock on 6/18/15.
 */
public class PathKSP implements Cloneable, Comparable<PathKSP> {
    private LinkedList<EdgeKSP> edges;
    private double totalCost;

    public PathKSP() {
        edges = new LinkedList<EdgeKSP>();
        totalCost = 0;
    }

    public PathKSP(double totalCost) {
        edges = new LinkedList<EdgeKSP>();
        this.totalCost = totalCost;
    }

    public PathKSP(LinkedList<EdgeKSP> edges) {
        this.edges = edges;
        totalCost = 0;
        for (EdgeKSP edge : edges) {
            totalCost += edge.getWeight();
        }
    }

    public PathKSP(LinkedList<EdgeKSP> edges, double totalCost) {
        this.edges = edges;
        this.totalCost = totalCost;
    }

    public LinkedList<EdgeKSP> getEdges() {
        return edges;
    }

    public void setEdges(LinkedList<EdgeKSP> edges) {
        this.edges = edges;
    }

    public List<String> getNodes() {
        LinkedList<String> nodes = new LinkedList<String>();

        for (EdgeKSP edge : edges) {
            nodes.add(edge.getFromNode());
        }

        EdgeKSP lastEdge = edges.getLast();
        if (lastEdge != null) {
            nodes.add(lastEdge.getToNode());
        }

        return nodes;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public void addFirstNode(String nodeLabel) {
        String firstNode = edges.getFirst().getFromNode();
        edges.addFirst(new EdgeKSP(nodeLabel, firstNode,0));
    }

    public void addFirst(EdgeKSP edge) {
        edges.addFirst(edge);
        totalCost += edge.getWeight();
    }

    public void add(EdgeKSP edge) {
        edges.add(edge);
        totalCost += edge.getWeight();
    }

    public void addLastNode(String nodeLabel) {
        String lastNode = edges.getLast().getToNode();
        edges.addLast(new EdgeKSP(lastNode, nodeLabel,0));
    }

    public int size() {
        return edges.size();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        int numEdges = edges.size();
        sb.append(totalCost);
        sb.append(": [");
        if (numEdges > 0) {
            for (int i = 0; i < edges.size(); i++) {
                sb.append(edges.get(i).getFromNode().toString());
                sb.append("-");
            }

            sb.append(edges.getLast().getToNode().toString());
        }
        sb.append("]");
        return sb.toString();
    }

/*    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Path path = (Path) o;

        if (Double.compare(path.totalCost, totalCost) != 0) return false;
        if (!edges.equals(path.edges)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = edges.hashCode();
        temp = Double.doubleToLongBits(totalCost);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }*/

    public boolean equals(PathKSP path2) {
        if (path2 == null)
            return false;

        LinkedList<EdgeKSP> edges2 = path2.getEdges();

        int numEdges1 = edges.size();
        int numEdges2 = edges2.size();

        if (numEdges1 != numEdges2) {
            return false;
        }

        for (int i = 0; i < numEdges1; i++) {
            EdgeKSP edge1 = edges.get(i);
            EdgeKSP edge2 = edges2.get(i);
            if (!edge1.getFromNode().equals(edge2.getFromNode()))
                return false;
            if (!edge1.getToNode().equals(edge2.getToNode()))
                return false;
        }

        return true;
    }

    public int compareTo(PathKSP path2) {
        double path2Cost = path2.getTotalCost();
        if (totalCost == path2Cost)
            return 0;
        if (totalCost > path2Cost)
            return 1;
        return -1;
    }

    public PathKSP clone() {
        LinkedList<EdgeKSP> edges = new LinkedList<EdgeKSP>();

        for (EdgeKSP edge : this.edges) {
            edges.add(edge.clone());
        }

        return new PathKSP(edges);
    }

    public PathKSP shallowClone() {
        LinkedList<EdgeKSP> edges = new LinkedList<EdgeKSP>();

        for (EdgeKSP edge : this.edges) {
            edges.add(edge);
        }

        return new PathKSP(edges,this.totalCost);
    }

    public PathKSP cloneTo(int i) {
        LinkedList<EdgeKSP> edges = new LinkedList<EdgeKSP>();
        int l = this.edges.size();
        if (i > l)
            i = l;

        //for (Edge edge : this.edges.subList(0,i)) {
        for (int j = 0; j < i; j++) {
            edges.add(this.edges.get(j).clone());
        }

        return new PathKSP(edges);
    }

    public PathKSP cloneFrom(int i) {
        LinkedList<EdgeKSP> edges = new LinkedList<EdgeKSP>();

        for (EdgeKSP edge : this.edges.subList(i,this.edges.size())) {
            edges.add(edge.clone());
        }

        return new PathKSP(edges);
    }

    public void addPath(PathKSP p2) {
        // ADD CHECK TO SEE THAT PATH P2'S FIRST NODE IS SAME AS THIS PATH'S LAST NODE

        this.edges.addAll(p2.getEdges());
        this.totalCost += p2.getTotalCost();
    }
}
