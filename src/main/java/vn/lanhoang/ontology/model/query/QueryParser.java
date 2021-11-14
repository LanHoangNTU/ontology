package vn.lanhoang.ontology.model.query;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import vn.lanhoang.ontology.configuration.OntologyVariables;

public class QueryParser {
	private String uri;
	
	private String prefix;

	public QueryParser(String uri, String prefix) {
		this.uri = uri;
		this.prefix = prefix;
	}
	
	public QueryParser(OntologyVariables v) {
		this(v.getBaseUri(), v.getPreffix());
	}
	
	public String parse(QueryContainer queryContainer) {
		Set<String> subjects = new HashSet<>();
		String result = parse(queryContainer, subjects, new AtomicInteger(0));
		result = RegExUtils.removeFirst(result, "\\{");
		return StringUtils.removeEnd(result, "}").concat("\n\tGROUPBY ?subject");
	}
	
	private String parse(QueryContainer queryContainer, Set<String> subjects, AtomicInteger aNo) {
		Set<String> conditions = new HashSet<>();
		boolean isAnd = Operator.AND.equals(queryContainer.getOperator());
		
		for (QueryDTO queryDTO: queryContainer.getConditions()) {
			if (queryDTO.getCondition().equalsIgnoreCase("CONTAINS")) {
				conditions.add(getQueryContains(queryDTO, aNo.incrementAndGet()));
			} else {
				conditions.add(getQueryEqual(queryDTO));
			}
		}
		
		for (QueryContainer container: queryContainer.getQueries()) {
			conditions.add(parse(container, subjects, aNo));
		}
		
		StringBuilder sb = new StringBuilder();
		String wheres = String.join(isAnd ? "\n\t\t.\n\t\t": "\n\t\tUNION\n\t\t", conditions);
		sb.append("{\n\t");
		sb.append("SELECT ?subject \n\t");
		sb.append("WHERE {\n\t\t");
		sb.append(wheres.replace("\n", "\n\t"));
		sb.append("\n\t\t}\n");
		sb.append("\t}");

		return sb.toString().replace("\n", "\n\t");
	}
	
	private String getQueryContains(QueryDTO queryDTO, int no) {
		String pred = prefix.concat(queryDTO.getPredicate());
		String object = "v" + no;
		
		StringBuilder sb = new StringBuilder();
		sb.append("\t{ \t");
		sb.append(String.format("?subject %s ?%s .\t", pred, object));
		sb.append(String.format("FILTER (regex(?%s, \"%s\", \"i\")) \t", object, queryDTO.getObject()));
		sb.append(" }");
		
		return sb.toString();
	}
	
	private String getQueryEqual(QueryDTO queryDTO) {
		String pred = prefix.concat(queryDTO.getPredicate());
		
		return String.format("{ ?subject %s \"%s\" }"
		, pred, queryDTO.getObject());
	}
}
