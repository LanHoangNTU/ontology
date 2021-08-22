# Ontology Model Mapper
Java library to standardized Semantic Data from Apache Jena Library

# Installing

Maven Install:

       <repositories>
       		<repository>
       		    <id>jitpack.io</id>
       		    <url>https://jitpack.io</url>
       		</repository>
       </repositories>
       <dependencies>
	       ...
		   <dependency>
	    	    <groupId>com.github.LanHoangNTU</groupId>
	    	    <artifactId>ontology</artifactId>
	    	    <version>Tag</version>
		   </dependency>
    	   ...
       </dependencies>

# Instructions

 **1. Settings**
Must specifies these settings in order to use the library correctly.
![Ontology IRI](https://ontology101tutorial.readthedocs.io/en/latest/_images/Figure2.png)

**application.yml**

    spring:
	    onto:
	        path: # Your .OWL file path here (required). Ex: src/main/resources/Datasource.owl
	        base-uri: # Your base Ontology URI (required). Ex: http://www.myontology.domain
	        max-nested-count: 5 # (required)
	        prefix: tckt #User defined prefix (required)

Create a configuration class and you're all set:

    @Configuration
    @Component
    public class OntologyVariablesConfiguration {
    	
    	@Value("${spring.onto.base-uri}")
    	private String baseUri;
    	@Value("${spring.onto.path}")
    	private String path;
    	@Value("${spring.onto.max-nested-count:5}")
    	private Integer maxNestedCount;
    	@Value("${spring.onto.prefix}")
    	private String preffix;
    	
    	@Bean
    	public OntologyVariables ontologyVariables() {
    		// Important
    		OntologyVariables ontologyVariables = new OntologyVariables();
    		// Base Ontology IRI
    		ontologyVariables.setBaseUri(baseUri.endsWith("#") ? baseUri : baseUri + "#");
    		// File .owl path
    		ontologyVariables.setPath(path);
    		// Nesting document (not support as of now)
    		ontologyVariables.setMaxNestedCount(maxNestedCount);
    		// Load Model from path
    		ontologyVariables.setModel(FileManager.getInternal().loadModelInternal(path));
    		// Ontology Prefix
    		ontologyVariables.setPreffix(preffix + ":");
    		
    		// Set Ontology base Prefix
    		ontologyVariables.getModel().setNsPrefix(preffix, ontologyVariables.getBaseUri());
    		// For Querying purposes (Optional but recommended)
    		ontologyVariables.setPreffixes(
    			"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
    			+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
    			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
    			+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
    			+ "PREFIX " + preffix + ": <" + ontologyVariables.getBaseUri() + ">\r\n"
    		);
    		
    		return ontologyVariables;
    	}
    }

 **2. Model classes**
 
**Important notes:**
 - Every variables in the class must be identical to your .owl file. Ex: class Subject has Data/Object property "**hasName**" then Model should have variable "**hasName**", not "**Name**" or "**NAME**", etc...
 - **OntologyObject** must have **uri** value of **that class** (class uri in your .owl file)
- Annotation **@Name** and field **id** is mandatory
- Can also reference other **class** with annotation **OntologyObject**
- Getters and Setters is required for each variables

```
@vn.lanhoang.ontology.OntologyObject(uri = "Subject")
public class Subject {
    @vn.lanhoang.ontology.Name
    private String id;
    private String name;
    private Program hasProgram;
	// Getters and Setters
	...
}

@vn.lanhoang.ontology.OntologyObject(uri = "Program")
public class Program {
    @vn.lanhoang.ontology.Name
    private String id;
    private String name;
    private Program hasProgram;
	// Getters and Setters
	...
}
```
 **3. Repositories**
 The library offer basic CRUD operations on Models:
 Example:
 ```
 @Repository
 public class SubjectRepository extends OntologyRepository<Subject> {
 }
```

 **4. CRUD Operations**
Available **OntologyRepository** functions are:
```
OntologyRepository.save(T object): T;
OntologyRepository.find(): List<T>;
OntologyRepository.findByUriTag(String uriTag): Optional<T>;
OntologyRepository.findByPropertyValue(String property, String value): Optional<T>;
OntologyRepository.query(String subjectparam, QueryParam ...params): List<T>;
OntologyRepository.remove(T obj): void;
OntologyRepository.remove(String uri): void;
```
