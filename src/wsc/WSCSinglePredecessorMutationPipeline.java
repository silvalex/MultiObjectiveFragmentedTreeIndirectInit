package wsc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;

public class WSCSinglePredecessorMutationPipeline extends BreedingPipeline {

	private static final long serialVersionUID = 1L;

	@Override
	public Parameter defaultBase() {
		return new Parameter("wscmutationpipeline");
	}

	@Override
	public int numSources() {
		return 1;
	}

	@Override
	public int produce(int min, int max, int start, int subpopulation,
			Individual[] inds, EvolutionState state, int thread) {
		WSCInitializer init = (WSCInitializer) state.initializer;

		int n = sources[0].produce(min, max, start, subpopulation, inds, state, thread);

        if (!(sources[0] instanceof BreedingPipeline)) {
            for(int q=start;q<n+start;q++)
                inds[q] = (Individual)(inds[q].clone());
        }

        if (!(inds[start] instanceof WSCIndividual))
            // uh oh, wrong kind of individual
            state.output.fatal("WSCMutationPipeline didn't get a WSCIndividual. The offending individual is in subpopulation "
            + subpopulation + " and it's:" + inds[start]);

        // Perform mutation
        for(int q=start;q<n+start;q++) {
            WSCIndividual tree = (WSCIndividual)inds[q];
            WSCSpecies species = (WSCSpecies) tree.species;

            // Randomly select a node in the tree to be mutated
            List<String> keyList = new ArrayList<String>(tree.getPredecessorMap().keySet());
            String selectedKey = "start";

            while(selectedKey.equals("start")) {
            	selectedKey = keyList.get(init.random.nextInt(keyList.size()));
            }
            
            // Randomly select a predecessor in that fragment and remove it
            Map<String, Set<String>> predecessorMap = tree.getPredecessorMap();
            Set<String> predecessorSet = predecessorMap.get(selectedKey);
            List<String> predecessorList = new ArrayList<String>(predecessorSet);
            String chosenPredecessor = predecessorList.get(init.random.nextInt(predecessorList.size()));
            predecessorSet.remove(chosenPredecessor);

            // Identify inputs that have not been subsumed
            Set<String> availableOutputs = new HashSet<String>();
            for (String predecessor : predecessorSet)
            	availableOutputs.addAll(init.serviceMap.get(predecessor).outputs);
            
            Service rootService;
            if(selectedKey.equals("end"))
            	rootService = init.endServ;
            else
            	rootService = init.serviceMap.get(selectedKey);
            
            Set<String> unfulfilledInputs = init.getInputsNotSubsumed(rootService.getInputs(), availableOutputs);
            
            // Find new predecessors to fulfil those inputs
            Set<Service> additionalPredecessors = species.findPredecessors(init, unfulfilledInputs, rootService.layer);
            
            // Add predecessors to fragment, and build any additional fragments as needed
            for (Service pred : additionalPredecessors) {
            	predecessorSet.add(pred.name);
            	species.finishConstructingTree(pred, init, predecessorMap);
            }
            
            tree.evaluated=false;
        }
        return n;
	}

}
