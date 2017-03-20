package wsc;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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