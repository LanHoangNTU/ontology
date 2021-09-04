package vn.lanhoang.ontology.repository;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceRequiredException;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;

import vn.lanhoang.ontology.IModelExecutor;
import vn.lanhoang.ontology.ModelManager;
import vn.lanhoang.ontology.ModelMapper;
import vn.lanhoang.ontology.annotation.Name;
import vn.lanhoang.ontology.annotation.OntologyObject;
import vn.lanhoang.ontology.configuration.OntologyVariables;
import vn.lanhoang.ontology.model.QueryParam;

public abstract class OntologyRepository<R> {
	
	private static final Logger log = LoggerFactory.getLogger(OntologyRepository.class);
	private Class<R> type;
	private String classUri;
	private Model model;
	private Property klass;
	private OntologyVariables ontologyVariables;
	private ModelManager modelManager = ModelManager.instance();
	private IModelExecutor executor;

	@Autowired
	public final void setOntologyVariables(OntologyVariables ontologyVariables) {
		this.ontologyVariables = ontologyVariables;
	}

	@SuppressWarnings("unchecked")
	protected OntologyRepository() {
		type = (Class<R>) GenericTypeResolver.resolveTypeArgument(getClass(), OntologyRepository.class);
		
		if (type.getClass().getDeclaredFields().length <= 0) {
			throw new IllegalArgumentException("No fields in class " + type.getPackageName() + "." + type.getName());
		} else {
			// Register to ModelManager
			executor = new ModelMapper(type);
		}
		
		for (Field field: type.getDeclaredFields()) {
			if (field.getType().isAnnotationPresent(OntologyObject.class)) {
				// Register field to ModelManager
				new ModelMapper(field.getType());
			} else if (field.getType().equals(List.class)) {
				ParameterizedType fieldListType = (ParameterizedType) field.getGenericType();
				Class<?> fieldListClass = (Class<?>) fieldListType.getActualTypeArguments()[0];
				
				new ModelMapper(fieldListClass);
			}
		}
		
		if (type.isAnnotationPresent(OntologyObject.class)) {
			OntologyObject annonObj = type.getAnnotation(OntologyObject.class);
			if (StringUtils.isBlank(annonObj.uri())) {
				throw new NullPointerException("Ontology object's uri is null or empty");
			}
			
			classUri = annonObj.uri();
		} else {
			throw new NullPointerException("Ontology object's uri is null or empty");
		}
	}
	
	@PostConstruct
	protected void init() {
		this.model = ontologyVariables.getModel();
		this.classUri = ontologyVariables.getBaseUri() + classUri;
		this.klass = model.getProperty(classUri);
	}
	
	// 2 Options: Do a recursive or ignore sub models
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object mapToObject(Object obj, Resource res, Set<String> persistentKeys, boolean shouldLoop) {
		if (persistentKeys == null) {
			persistentKeys = new HashSet<>();
		}
		IModelExecutor mapper = modelManager.getExecutor(obj.getClass());
		Field[] fields = mapper.getAllFields();
		
		if (persistentKeys.contains(res.toString())) {
			mapper.invokeSetName(obj, res.toString());
			return obj;
		}
		
		persistentKeys.add(res.toString());
		for (Field field : fields) {
			Property p = model.getProperty(ontologyVariables.getBaseUri() + field.getName());
			if (res.hasProperty(p)) {
				Statement stmt = res.getProperty(p);
				Class<?> classField = field.getType();
				if (modelManager.isSupported(classField)) {
					mapper.invokeSetter(field.getName(), obj, stmt.getObject());
				} else if (classField.equals(List.class)) {
					ParameterizedType fieldListType = (ParameterizedType) field.getGenericType();
					Class<?> fieldListClass = (Class<?>) fieldListType.getActualTypeArguments()[0];
					List list = ModelManager.createList(fieldListClass);
					res.listProperties(p).forEach(propStmt -> {
						// ---- Set main key only (uri) ----
						try {
							Constructor<?> ctor = fieldListClass.getConstructor();
							Object object = ctor.newInstance(new Object[] {});
							modelManager.getExecutor(fieldListClass).invokeSetName(object, propStmt.getResource()); // Only set uri of sub model
							list.add(object);
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					mapper.invokeSetter(field.getName(), obj, list);
				} else if (classField.isAnnotationPresent(OntologyObject.class)) {
					try {
						Constructor<?> ctor = classField.getConstructor();
						Object object = ctor.newInstance(new Object[] {});
						
						// ---- Recursive ----
						// object = mapToObject(object, stmt.getResource(), persistentKeys); // Recursive
						
						// ---- Set main key only (uri) ----
						if (shouldLoop) {
							object = mapToObject(object, stmt.getResource(), persistentKeys, false);
						} else {
							try {
								// Only set uri of sub model
								modelManager.getExecutor(field.getType()).invokeSetName(object, stmt.getResource());
							} catch(ResourceRequiredException e) {
								modelManager.getExecutor(field.getType()).invokeSetName(object, ""); 
							} 
						}
						// -------------------
						mapper.invokeSetter(field.getName(), obj, object);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					throw new IllegalArgumentException(String.format("Type %s not supported yet", classField.getName()));
				}
			}
		}	
		
		mapper.invokeSetName(obj, res.toString());
		return obj;
	}
	
	private boolean validateUri(String uri) {
		return (uri.startsWith(ontologyVariables.getBaseUri())
				&& uri.indexOf('#') == uri.lastIndexOf('#'));
	}
	
	/**
	 * Save an entity to the model
	 * 
	 * @param obj
	 * @return R
	 */
	public R save(R obj) {
		IModelExecutor mapper = modelManager.getExecutor(type);
		String baseUri = ontologyVariables.getBaseUri();
		Resource root = null;
		Map<Property, Object> properties = new HashMap<>();
		
		Field fields[] = obj.getClass().getDeclaredFields();
		
		for (Field field: fields) {
			String propUri = baseUri + field.getName();
			if (field.isAnnotationPresent(Name.class)) {
				String uniqueUri = mapper.invokeGetter(field.getName(), obj).toString();
				if (!uniqueUri.startsWith(baseUri)) {
					uniqueUri = baseUri + uniqueUri;
				}
				if (!validateUri(uniqueUri)) {
					return null;
				}
				root = model.createResource(uniqueUri);
			} else if (field.getType().equals(List.class)) {
				ParameterizedType fieldListType = (ParameterizedType) field.getGenericType();
				Class<?> fieldListClass = (Class<?>) fieldListType.getActualTypeArguments()[0];
				IModelExecutor subMapper = modelManager.getExecutor(fieldListClass);
				
				List<?> list = (List<?>) mapper.invokeGetter(field.getName(), obj);
				Property prop = model.getProperty(baseUri + field.getName());
				for (Object object : list) {
					String subUri = subMapper.invokeGetName(object).toString();
					if (StringUtils.isNotBlank(subUri)) {
						Resource subRes = model.getResource(subUri);
						properties.put(prop, subRes);
					}
				}
				
			} else if (field.getType().isAnnotationPresent(OntologyObject.class)) {
				Object object = mapper.invokeGetter(field.getName(), obj);
				IModelExecutor subMapper = modelManager.getExecutor(field.getType());
				Property prop = model.getProperty(propUri);
				String subUri = subMapper.invokeGetName(object).toString();
				if (StringUtils.isNotBlank(subUri)) {
					Resource subRes = model.getResource(subUri);
					properties.put(prop, subRes);
				}
			} else {
				properties.put(model.getProperty(propUri), mapper.invokeGetter(field.getName(), obj));
			}
		}
		
		for (Map.Entry<Property, Object> entry : properties.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Resource) {
				model.add(root, entry.getKey(), (Resource) entry.getValue());
			} else if (entry.getValue() != null) {
				model.add(root, entry.getKey(), entry.getValue().toString());
			}
		}
		model.add(root, RDF.type, model.getResource(classUri));
		
		OutputStream out = null;
		try {
			out = new FileOutputStream(ontologyVariables.getPath());
			RDFDataMgr.write(out, model, Lang.RDFXML);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return obj;
	}
	
	/**
	 * Find all entity base on class
	 * 
	 * @return List<R>
	 */
	public List<R> find() {
		ResIterator iterator = model.listResourcesWithProperty(RDF.type, klass);
		List<R> results = new ArrayList<>();
		try {
			while (iterator.hasNext()) {
				R obj;
				obj = type.getDeclaredConstructor().newInstance();
				mapToObject(obj, iterator.next(), null, true);
				results.add(obj);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return results;
	}
	
	/**
	 * 
	 * Find an entity base on its unique URI
	 * 
	 * @param uriTag The entity's unique URI
	 * @return Optional<R>
	 */
	public Optional<R> findByUriTag(String uriTag) {
		String baseUri = ontologyVariables.getBaseUri();
		String uniqueUri;
		if (uriTag.startsWith(baseUri)) {
			uniqueUri = uriTag;
		} else {
			uniqueUri = baseUri + uriTag;
		}
		String queryStr = ontologyVariables.getPreffixes()
				+ " SELECT ?subject \r\n"
				+ " WHERE {\r\n"
				+ " ?subject rdf:type <" + classUri + ">.\r\n"
				+ " FILTER (?subject  = <" + uniqueUri + ">) \r\n"
				+ " }";
		R obj = null;
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    if (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("subject");
		    	obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return Optional.ofNullable(obj);
	}
	
	/**
	 * 
	 * Find an entity base on its property and value
	 * 
	 * @param property Entity's property
	 * @param value property's values
	 * @return Optional<R>
	 */
	public Optional<R> findByPropertyValue(String property, String value) {
		R obj = null;
		final String preffix = ontologyVariables.getPreffix();
		String queryStr = String.format(
			"SELECT ?subject WHERE { "
			+ "?subject %s \"%s\" .\n"
			+ "?subject rdf:type <%s> ."
			+ " }", 
			preffix + property,
			value,
			classUri
		);
		
		queryStr = ontologyVariables.getPreffixes() + queryStr;
		
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    if (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("subject");
		    	obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return Optional.ofNullable(obj);
	}
	
	public List<R> query(QueryParam ...params) {
		String paramStr = "";
		for (QueryParam param: params) {
			paramStr += param.toString();
		}
		
		String queryStr = "SELECT ?subject\nWHERE {\n" + paramStr + "\n}";
		List<R> list = new ArrayList<>(); 
		queryStr = ontologyVariables.getPreffixes() + queryStr;
		
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    while (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("?subject");
		    	R obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true);
		    	list.add(obj);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list;
	}
	
	public List<R> query(String subjectparam, String queryStr) {
		List<R> list = new ArrayList<>(); 
		queryStr = ontologyVariables.getPreffixes() + queryStr;
		
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    while (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource(subjectparam);
		    	R obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true);
		    	list.add(obj);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list;
	}
	
	/**
	 * 
	 * Remove an Entity
	 * 
	 * @param obj
	 */
	public void remove(R obj) {
		Resource res = model.getResource(executor.invokeGetName(obj).toString());
		if (res != null) {
			res.removeProperties();
			OutputStream out = null;
			try {
				out = new FileOutputStream(ontologyVariables.getPath());
				RDFDataMgr.write(out, model, Lang.RDFXML);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Remove an entity base on its unique URI
	 * 
	 * @param uri
	 */
	public void remove(String uri) {
		StmtIterator iter = model.getResource(uri).listProperties();
		if (iter != null) {
			this.model = model.remove(iter);
			OutputStream out = null;
			try {
				out = new FileOutputStream(ontologyVariables.getPath());
				RDFDataMgr.write(out, model, Lang.RDFXML);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			log.warn("Cannot find entity");
		}
	}
	
	/**
	 * <p>Check if a requested entity exists in database</p>
	 * 
	 * @param uriTag Entity's uri
	 * @return	 <b>true</b> if exists, <b>false</b> if not exists
	 */
	public boolean exists(String uriTag) {
		String baseUri = ontologyVariables.getBaseUri();
		String uniqueUri;
		if (uriTag.startsWith(baseUri)) {
			uniqueUri = uriTag;
		} else {
			uniqueUri = baseUri + uriTag;
		}
		
		String queryStr = ontologyVariables.getPreffixes()
				+ " SELECT ?subject \r\n"
				+ " WHERE {\r\n"
				+ " ?subject rdf:type <" + classUri + ">.\r\n"
				+ " FILTER (?subject  = <" + uniqueUri + ">) \r\n"
				+ " }";
		
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    return results.hasNext();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	/**
	 * <p>Check if a requested entity exists in database</p>
	 * 
	 * @param params Query parameters
	 * @return	 <b>true</b> if exists, <b>false</b> if not exists
	 */
	public boolean exists(QueryParam ...params) {
		String queryparams = " ";
		for (QueryParam param : params) {
			queryparams += param.toString();
		}
		
		String queryStr = ontologyVariables.getPreffixes()
				+ " SELECT ?subject \r\n"
				+ " WHERE {\r\n"
				+ " ?subject rdf:type <" + classUri + ">.\r\n"
				+ queryparams
				+ " }";
		
		Query query = QueryFactory.create(queryStr);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    return results.hasNext();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
}
