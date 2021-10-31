package vn.lanhoang.ontology.model.query;

import java.util.List;

public class QueryContainer {
	private Operator operator;
	
	private List<QueryDTO> conditions;
	
	private List<QueryContainer> queries;

	public Operator getOperator() {
		return operator;
	}

	public void setOperator(Operator operator) {
		this.operator = operator;
	}

	public List<QueryDTO> getConditions() {
		return conditions;
	}

	public void setConditions(List<QueryDTO> conditions) {
		this.conditions = conditions;
	}

	public List<QueryContainer> getQueries() {
		return queries;
	}

	public void setQueries(List<QueryContainer> queries) {
		this.queries = queries;
	}
	
}
