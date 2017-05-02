package wsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Node implements Cloneable {
	private List<Edge> incomingEdgeList = new ArrayList<Edge>();
	private List<Edge> outgoingEdgeList = new ArrayList<Edge>();
	private Service service;

	public Node(Service service) {
		this.service = service;
	}

	public List<Edge> getIncomingEdgeList() {
		return incomingEdgeList;
	}

	public List<Edge> getOutgoingEdgeList() {
		return outgoingEdgeList;
	}

	@Override
	public String toString(){
		return service.name;
	}

	@Override
	public int hashCode() {
		return service.name.hashCode();
	}

	public String getName() {
		return service.name;
	}

	public int getLayer() {
		return service.layer;
	}

	public Set<String> getInputs() {
		return service.inputs;
	}

	public Set<String> getOutputs() {
		return service.outputs;
	}

	public Service getService() {
		return service;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Node) {
			Node o = (Node) other;
			return service.name.equals(o.service.name);
		}
		else
			return false;
	}
}
