package wsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;

public class WSCMergeCrossoverPipeline extends BreedingPipeline {

	private static final long serialVersionUID = 1L;
	static int itsEquivalent;
	static int nonEquivalent;

	@Override
	public Parameter defaultBase() {
		return new Parameter("wsccrossoverpipeline");
	}

	@Override
	public int numSources() {
		return 2;
	}

	@Override
	public int produce(int min, int max, int start, int subpopulation,
			Individual[] inds, EvolutionState state, int thread) {

		WSCInitializer init = (WSCInitializer) state.initializer;

		Individual[] inds1 = new Individual[inds.length];
		Individual[] inds2 = new Individual[inds.length];

		int n1 = sources[0].produce(min, max, 0, subpopulation, inds1, state, thread);
		int n2 = sources[1].produce(min, max, 0, subpopulation, inds2, state, thread);

        if (!(sources[0] instanceof BreedingPipeline)) {
            for(int q=0;q<n1;q++)
                inds1[q] = (Individual)(inds1[q].clone());
        }

        if (!(sources[1] instanceof BreedingPipeline)) {
            for(int q=0;q<n2;q++)
                inds2[q] = (Individual)(inds2[q].clone());
        }

        if (!(inds1[0] instanceof WSCIndividual))
            // uh oh, wrong kind of individual
            state.output.fatal("WSCCrossoverPipeline didn't get a WSCIndividual. The offending individual is in subpopulation "
            + subpopulation + " and it's:" + inds1[0]);

        if (!(inds2[0] instanceof WSCIndividual))
            // uh oh, wrong kind of individual
            state.output.fatal("WSCCrossoverPipeline didn't get a WSCIndividual. The offending individual is in subpopulation "
            + subpopulation + " and it's:" + inds2[0]);

        int nMin = Math.min(n1, n2);

        // Perform crossover
        for(int q=start,x=0; q < nMin + start; q++,x++) {
    		WSCIndividual t1 = ((WSCIndividual)inds1[x]);
    		WSCIndividual t2 = ((WSCIndividual)inds2[x]);

    		// Get all fragment roots from both candidates
    		List<String> allT1Keys = new ArrayList<String>(t1.getPredecessorMap().keySet());
            List<String> allT2Keys = new ArrayList<String>(t2.getPredecessorMap().keySet());

            // Shuffle them so that the crossover is done randomly
            Collections.shuffle( allT1Keys, init.random );
            Collections.shuffle( allT2Keys, init.random );

            // Select the fragment root for crossover
            String selected = null;

            for (String s1 : allT1Keys) {
                if (!s1.equals("start")) {
                    for (String s2 : allT2Keys) {
                        if (s1.equals( s2 )) {
                            selected = s1;
                            break;
                        }
                    }
                }
            }

            // Merge fragments
            Map<String, Set<String>> mergedFragments = new HashMap<String, Set<String>>();
            for (Entry<String, Set<String>> e : t1.getPredecessorMap().entrySet()) {
            	addToMergedMap(e.getKey(), e.getValue(), mergedFragments);
            }

            // Generate new candidate from the set of merged fragments
            Map<String, Set<String>> candidate1frags = produceFromMerged(mergedFragments, init);
            Map<String, Set<String>> candidate2frags = produceFromMerged(mergedFragments, init);
            WSCIndividual newInd1 = new WSCIndividual();
            newInd1.setMap(candidate1frags);
            WSCIndividual newInd2 = new WSCIndividual();
            newInd2.setMap(candidate2frags);

	        inds[q] = newInd1;
	        inds[q].evaluated=false;

	        if (q+1 < inds.length) {
	        	inds[q+1] = newInd2;
	        	inds[q+1].evaluated=false;
	        }
        }
        return n1;
	}

	private void addToMergedMap(String key, Set<String> values, Map<String, Set<String>> mergedFragments) {
		if (mergedFragments.containsKey(key)) {
			Set<String> mergedValues = mergedFragments.get(key);
			mergedValues.addAll(values);
		}
		else {
			Set<String> mergedValues = new HashSet<String>();
			mergedValues.addAll(values);
			mergedFragments.put(key, mergedValues);
		}
	}

	private Map<String, Set<String>> produceFromMerged(Map<String, Set<String>> mergedFragments, WSCInitializer init) {

		Map<String, Set<String>> predecessorMap = new HashMap<String, Set<String>>();
	    predecessorMap.put("start", new HashSet<String>());

	    finishConstructingTree(init.endServ, init, predecessorMap, mergedFragments);

		return predecessorMap;
	}

	public void finishConstructingTree(Service s, WSCInitializer init, Map<String, Set<String>> predecessorMap, Map<String, Set<String>> mergedFragments) { // XXX
	    Queue<Service> queue = new LinkedList<Service>();
	    queue.offer(s);

	    while (!queue.isEmpty()) {
	    	Service current = queue.poll();

	    	if (!predecessorMap.containsKey(current.name)) {
		    	Set<Service> predecessors = findPredecessors(current, predecessorMap, mergedFragments, init);
		    	Set<String> predecessorNames = new HashSet<String>();

		    	for (Service p : predecessors) {
		    		predecessorNames.add(p.name);
	    			queue.offer(p);
		    	}
		    	predecessorMap.put(current.name, predecessorNames);
	    	}
	    }
	}


	public Set<Service> findPredecessors(Service current, Map<String, Set<String>> predecessorMap, Map<String, Set<String>> mergedFragments, WSCInitializer init) { // XXX
		Set<Service> predecessors = new HashSet<Service>();

		// Get only inputs that are not subsumed by the given composition inputs
		Set<String> inputsNotSatisfied = init.getInputsNotSubsumed(current.inputs, init.startServ.outputs);
		Set<String> inputsToSatisfy = new HashSet<String>(inputsNotSatisfied);

		if (inputsToSatisfy.size() < current.inputs.size())
			predecessors.add(init.startServ);

		// Find services to satisfy all inputs from the merged map
		List<String> candidatePredecessors = new ArrayList<String>(mergedFragments.get(current.name));
		Collections.shuffle(candidatePredecessors, init.random);

		int idx = 0;
		// Find services to satisfy all inputs
		while (!inputsToSatisfy.isEmpty()) {
			// Get the next candidate
			String candName = candidatePredecessors.get(idx++);
			Service cand = init.serviceMap.get(candName);

			// Check if the candidate fulfils any pending inputs

		}

		for (String cand : candidatePredecessors) {

		}

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
