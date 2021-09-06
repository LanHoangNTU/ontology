package vn.lanhoang.ontology.configuration;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;

public class OntologyVariables {
	
	private String baseUri;
	private String path;
	private Integer maxNestedCount;
	private String preffix;
	private String preffixes;
	
	public OntologyVariables() {
	}
	
	public String getBaseUri() {
		return this.baseUri;
	}
	
	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
	}
	
	public Model getModel() {
		return FileManager.getInternal().loadModelInternal(path);
	}

	public Integer getMaxNestedCount() {
		return maxNestedCount;
	}

	public void setMaxNestedCount(Integer maxNestedCount) {
		this.maxNestedCount = maxNestedCount;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPreffix() {
		return preffix;
	}

	public void setPreffix(String preffix) {
		this.preffix = preffix;
	}

	public String getPreffixes() {
		return preffixes;
	}

	public void setPreffixes(String preffixes) {
		this.preffixes = preffixes;
	}
	
}
