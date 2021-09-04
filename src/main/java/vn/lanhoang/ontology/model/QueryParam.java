package vn.lanhoang.ontology.model;

public class QueryParam {
	private String subject;
	
	private String property;
	
	private String object;

	public QueryParam(String subject, String property, String object) {
		this.subject = subject;
		this.property = property;
		this.object = object;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}

	public String getObject() {
		return object;
	}

	public void setObject(String object) {
		this.object = object;
	}

	@Override
	public String toString() {
		return subject + " " + property + " " + object + " .\r\n";
	}
}
