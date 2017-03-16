package wsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;

public class WSCMutationPipeline extends BreedingPipeline {

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

            // Remove the old fragment
            Map<String, Set<String>> predecessorMap = tree.getPredecessorMap();
            predecessorMap.remove(selectedKey);

            // Build the new fragment(s)
            Service s;
            if (selectedKey.equals("end"))
            	s = init.endServ;
            else
            	s = init.serviceMap.get(selectedKey);
            species.finishConstructingTree(s, init, tree.getPredecessorMap());

            tree.evaluated=false;
        }
        return n;
	}

}
