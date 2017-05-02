package wsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;

import ec.EvolutionState;
import ec.Individual;
import ec.Species;
import ec.util.Parameter;

public class WSCSpecies extends Species {

	private static final long serialVersionUID = 1L;

	@Override
	public Parameter defaultBase() {
		return new Parameter("wscspecies");
	}
	
	@Override
	public Individual newIndividual(EvolutionState state, int thread) {
	    WSCInitializer init = (WSCInitializer) state.initializer;
	    Map<String, Set<String>> predecessorMap = new HashMap<String, Set<String>>();
	    predecessorMap.put("start", new HashSet<String>());

	    finishConstructingTree(init.endServ, init, predecessorMap);

	    WSCIndividual newGraph = (WSCIndividual) super.newIndividual(state, thread);
	    newGraph.setMap(predecessorMap);

	    return newGraph;
	}
	
	/**
	 * Creates a new random sequence of candidates for initialisation.
	 */
	public Service[] createRandomSequence(WSCInitializer init) {
		List<Service> relevantList = init.relevantList;
		Collections.shuffle(relevantList, init.random);

		Service[] genome = new Service[relevantList.size()];
		relevantList.toArray(genome);
		return genome;
	}
	
	public Graph createNewGraph(int numLayers, Service start, Service end, Service[] sequence, WSCInitializer init) {
		Node endNode = new Node(end);
		Node startNode = new Node(start);

        Graph graph = new Graph();
        graph.nodeMap.put(endNode.getName(), endNode);

        // Populate inputs to satisfy with end node's inputs
        List<InputNodeLayerTrio> nextInputsToSatisfy = new ArrayList<InputNodeLayerTrio>();

        for (String input : end.getInputs()){
            nextInputsToSatisfy.add( new InputNodeLayerTrio(input, end.getName(), numLayers) );
        }

        // Fulfil inputs layer by layer
        for (int currLayer = numLayers; currLayer > 0; currLayer--) {

            // Filter out the inputs from this layer that need to fulfilled
            List<InputNodeLayerTrio> inputsToSatisfy = new ArrayList<InputNodeLayerTrio>();
            for (InputNodeLayerTrio p : nextInputsToSatisfy) {
               if (p.layer == currLayer)
                   inputsToSatisfy.add( p );
            }
            nextInputsToSatisfy.removeAll( inputsToSatisfy );

            int index = 0;
            while (!inputsToSatisfy.isEmpty()){

                if (index >= sequence.length) {
                    nextInputsToSatisfy.addAll( inputsToSatisfy );
                    inputsToSatisfy.clear();
                }
                else {
                	Service nextNode = sequence[index++];
                	if (nextNode.layer < currLayer) {
	                    Node n = new Node(nextNode);
	                    //int nLayer = nextNode.layerNum;

	                    List<InputNodeLayerTrio> satisfied = getInputsSatisfiedGraphBuilding(inputsToSatisfy, n, init);

	                    if (!satisfied.isEmpty()) {
	                        if (!graph.nodeMap.containsKey( n.getName() )) {
	                            graph.nodeMap.put(n.getName(), n);
	                        }

	                        // Add edges
	                        createEdges(n, satisfied, graph);
	                        inputsToSatisfy.removeAll(satisfied);


	                        for(String input : n.getInputs()) {
	                            nextInputsToSatisfy.add( new InputNodeLayerTrio(input, n.getName(), n.getLayer()) );
	                        }
	                    }
	                }
                }
            }
        }

        // Connect start node
        graph.nodeMap.put(startNode.getName(), startNode);
        createEdges(startNode, nextInputsToSatisfy, graph);

        return graph;
    }
	
	public void createEdges(Node origin, List<InputNodeLayerTrio> destinations, Graph graph) {
		// Order inputs by destination
		Map<String, Set<String>> intersectMap = new HashMap<String, Set<String>>();
		for(InputNodeLayerTrio t : destinations) {
			addToIntersectMap(t.service, t.input, intersectMap);
		}

		for (Entry<String,Set<String>> entry : intersectMap.entrySet()) {
			Edge e = new Edge(entry.getValue());
			origin.getOutgoingEdgeList().add(e);
			Node destination = graph.nodeMap.get(entry.getKey());
			destination.getIncomingEdgeList().add(e);
			e.setFromNode(origin);
        	e.setToNode(destination);
        	graph.edgeList.add(e);
		}
	}

	private void addToIntersectMap(String destination, String input, Map<String, Set<String>> intersectMap) {
		Set<String> intersect = intersectMap.get(destination);
		if (intersect == null) {
			intersect = new HashSet<String>();
			intersectMap.put(destination, intersect);
		}
		intersect.add(input);
	}
	
	public List<InputNodeLayerTrio> getInputsSatisfiedGraphBuilding(List<InputNodeLayerTrio> inputsToSatisfy, Node n, WSCInitializer init) {
	    List<InputNodeLayerTrio> satisfied = new ArrayList<InputNodeLayerTrio>();
	    for(InputNodeLayerTrio p : inputsToSatisfy) {
            if (init.taxonomyMap.get(p.input).servicesWithOutput.contains( n.getService() ))
                satisfied.add( p );
        }
	    return satisfied;
	}
	
//===========================================================================================================
	
	public Map<String, Set<String>> graphToTreeTransformation(Graph g) {
		// TODO: finish coding this
		Map<String, Set<String>> predecessorMap = new HashMap<String, Set<String>>();
		return null;
	}
	
//===========================================================================================================

	public void finishConstructingTree(Service s, WSCInitializer init, Map<String, Set<String>> predecessorMap) {
	    Queue<Service> queue = new LinkedList<Service>();
	    queue.offer(s);

	    while (!queue.isEmpty()) {
	    	Service current = queue.poll();

	    	if (!predecessorMap.containsKey(current.name)) {
		    	Set<Service> predecessors = findPredecessors(init, current.getInputs(), current.layer);
		    	Set<String> predecessorNames = new HashSet<String>();

		    	for (Service p : predecessors) {
		    		predecessorNames.add(p.name);
	    			queue.offer(p);
		    	}
		    	predecessorMap.put(current.name, predecessorNames);
	    	}
	    }
	}


	public Set<Service> findPredecessors(WSCInitializer init, Set<String> inputs, int layer) {
		Set<Service> predecessors = new HashSet<Service>();

		// Get only inputs that are not subsumed by the given composition inputs

		Set<String> inputsNotSatisfied = init.getInputsNotSubsumed(inputs, init.startServ.outputs);
		Set<String> inputsToSatisfy = new HashSet<String>(inputsNotSatisfied);

		if (inputsToSatisfy.size() < inputs.size())
			predecessors.add(init.startServ);

		// Find services to satisfy all inputs
		for (String i : inputsNotSatisfied) {
			if (inputsToSatisfy.contains(i)) {
				List<Service> candidates = init.taxonomyMap.get(i).servicesWithOutput;
				Collections.shuffle(candidates, init.random);

				Service chosen = null;
				candLoop:
				for(Service cand : candidates) {
					if (init.relevant.contains(cand) && cand.layer < layer) {
						predecessors.add(cand);
						chosen = cand;
						break candLoop;
					}
				}

				inputsToSatisfy.remove(i);

				// Check if other outputs can also be fulfilled by the chosen candidate, and remove them also
				Set<String> subsumed = init.getInputsSubsumed(inputsToSatisfy, chosen.outputs);
				inputsToSatisfy.removeAll(subsumed);
			}
		}
		return predecessors;
	}
}