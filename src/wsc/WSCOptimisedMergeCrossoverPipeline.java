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

public class WSCOptimisedMergeCrossoverPipeline extends BreedingPipeline {

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

//            // Merge fragments
//            Map<String, Set<String>> mergedFragments = new HashMap<String, Set<String>>();
//            for (Entry<String, Set<String>> e : t1.getPredecessorMap().entrySet()) {
//            	addToMergedMap(e.getKey(), e.getValue(), mergedFragments);
//            }
//            for (Entry<String, Set<String>> e : t2.getPredecessorMap().entrySet()) {
//            	addToMergedMap(e.getKey(), e.getValue(), mergedFragments);
//            }

            // Generate new candidate from the set of merged fragments
            Map<String, Set<String>> candidate1frags = produceFromMerged(t1.getPredecessorMap(), t2.getPredecessorMap(), init);
            Map<String, Set<String>> candidate2frags = produceFromMerged(t1.getPredecessorMap(), t2.getPredecessorMap(), init);
            t1.setMap(candidate1frags);
            t2.setMap(candidate2frags);

	        inds[q] = t1;
	        inds[q].evaluated=false;

	        if (q+1 < inds.length) {
	        	inds[q+1] = t2;
	        	inds[q+1].evaluated=false;
	        }
        }
        return n1;
	}

	private Map<String, Set<String>> produceFromMerged(Map<String, Set<String>> frags1, Map<String, Set<String>> frags2, WSCInitializer init) {

		Map<String, Set<String>> predecessorMap = new HashMap<String, Set<String>>();
	    predecessorMap.put("start", new HashSet<String>());

	    finishConstructingTree(init.endServ, init, predecessorMap, frags1, frags2);

		return predecessorMap;
	}

	public void finishConstructingTree(Service s, WSCInitializer init, Map<String, Set<String>> predecessorMap, Map<String, Set<String>> frags1, Map<String, Set<String>> frags2) {
	    Queue<Service> queue = new LinkedList<Service>();
	    queue.offer(s);
		Set<Service> chosenPredecessors = new HashSet<Service>();

	    while (!queue.isEmpty()) {
	    	Service current = queue.poll();

	    	if (!predecessorMap.containsKey(current.name)) {
		    	Set<Service> predecessors = findPredecessors(chosenPredecessors, current, predecessorMap, frags1, frags2, init);
		    	Set<String> predecessorNames = new HashSet<String>();

		    	for (Service p : predecessors) {
		    		predecessorNames.add(p.name);
	    			queue.offer(p);
		    	}
		    	predecessorMap.put(current.name, predecessorNames);
	    	}
	    }
	}

	private String _getNextCandidate(String[] preds1, String[] preds2, WSCInitializer init) {
		int listRand = init.random.nextInt(2);
		String listElem;
		if (listRand == 0) {
			listElem = preds1 != null ? preds1[init.random.nextInt( preds1.length )] : preds2[init.random.nextInt( preds2.length)];
		}
		else {
			listElem = preds2 != null ? preds2[init.random.nextInt( preds2.length )] : preds1[init.random.nextInt( preds1.length)];
		}
		return listElem;
	}

	public Set<Service> findPredecessors(Set<Service> chosenPredecessors, Service current, Map<String, Set<String>> predecessorMap, Map<String, Set<String>> frags1, Map<String, Set<String>> frags2, WSCInitializer init) {
		chosenPredecessors.clear();

		// Get only inputs that are not subsumed by the given composition inputs
		Set<String> inputsToSatisfy = new HashSet<String>(init.getInputsNotSubsumed(current.inputs, init.startServ.outputs));

		if (inputsToSatisfy.size() < current.inputs.size())
			chosenPredecessors.add(init.startServ);

		// Find services to satisfy all inputs from the merged map
		String[] a = new String[0];
		Set<String> predSet1 = frags1.get(current.name);
		Set<String> predSet2 = frags2.get(current.name);
		String[] preds1 = predSet1 != null ? predSet1.toArray(a) : null;
		String[] preds2 = predSet2 != null ? predSet2.toArray(a) : null;

		// Find services to satisfy all inputs
		while (!inputsToSatisfy.isEmpty()) {
			// Get the next candidate
			String candName = _getNextCandidate( preds1, preds2, init );
			Service cand = init.serviceMap.get(candName);

			if (!chosenPredecessors.contains(cand)) {
				// Check if the candidate fulfils any pending inputs
				Set<String> satisfied = init.getInputsSubsumed(inputsToSatisfy, cand.outputs);
				// If so, select it as a predecessor and remove the satisfied inputs from set to satisfy
				if (!satisfied.isEmpty()) {
					chosenPredecessors.add(cand);
					inputsToSatisfy.removeAll(satisfied);
				}
			}
		}
		return chosenPredecessors;
	}
}
