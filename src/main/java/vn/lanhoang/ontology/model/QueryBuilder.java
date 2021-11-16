package vn.lanhoang.ontology.model;

import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {
	private List<QueryParam> queries;
	
	public QueryBuilder() {
		queries = new ArrayList<>();
	}
	
	public QueryBuilder add(QueryParam query) {
		if (query != null) {
			queries.add(query);
		}
		
		return this;
	}
	
	public QueryParam[] toArray() {
		return queries.toArray(new QueryParam[0]);
	}
}
