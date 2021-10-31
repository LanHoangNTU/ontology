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
		return StringUtils.removeEnd(result, "}").concat("\r\nGROUPBY ?subject");
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

		return "{\r\n SELECT ?subject \r\n"
				+ "WHERE {\r\n".concat( String.join(isAnd ? "\n.\n": "\nUNION\n", conditions).concat("\r\n}") )
				+ "\r\n }";
	}
	
	private String getQueryContains(QueryDTO queryDTO, int no) {
		String pred = prefix.concat(queryDTO.getPredicate());
		String object = "v" + no;
		
		return String.format(
			"{\r\n"
			+ "		?subject %s ?%s \r\n"
			+ "		FILTER (regex(?%s, \"%s\", \"i\"))\r\n"
			+ "}"
		, pred, object, object, queryDTO.getObject());
	}
	
	private String getQueryEqual(QueryDTO queryDTO) {
		String pred = prefix.concat(queryDTO.getPredicate());
		
		return String.format("{ ?subject %s \"%s\" }"
		, pred, queryDTO.getObject());
	}
}
