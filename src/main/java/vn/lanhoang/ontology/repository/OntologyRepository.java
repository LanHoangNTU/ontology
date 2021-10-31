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
import vn.lanhoang.ontology.utils.ModelUtils;

public abstract class OntologyRepository<R> {
	
	private static final Logger log = LoggerFactory.getLogger(OntologyRepository.class);
	private Class<R> type;
	private String classUri;
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
		Model model = ontologyVariables.getModel();
		this.classUri = ontologyVariables.getBaseUri() + classUri;
		this.klass = model.getProperty(classUri);
	}
	
	private R mapToObject(R obj, Resource res, 
							   Set<String> persistentKeys, 
							   boolean shouldLoop, Model model) {
		return ModelUtils.mapToObject(obj, res, persistentKeys, true, model, ontologyVariables, type);
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
		Model model = ontologyVariables.getModel();
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
				if (list == null) continue;
				Property prop = model.getProperty(baseUri + field.getName());
				List<Resource> subReses = new ArrayList<>();
				for (Object object : list) {
					String subUri = subMapper.invokeGetName(object).toString();
					if (StringUtils.isNotBlank(subUri)) {
						Resource subRes = model.getResource(subUri);
						subReses.add(subRes);
					}
				}
				
				properties.put(prop, subReses);
				
			} else if (field.getType().isAnnotationPresent(OntologyObject.class)) {
				Object object = mapper.invokeGetter(field.getName(), obj);
				if (object != null) {
					IModelExecutor subMapper = modelManager.getExecutor(field.getType());
					Property prop = model.getProperty(propUri);
					String subUri = subMapper.invokeGetName(object).toString();
					if (StringUtils.isNotBlank(subUri)) {
						Resource subRes = model.getResource(subUri);
						properties.put(prop, subRes);
					} else {
						properties.put(prop, null);
					}
				}
			} else {
				properties.put(model.getProperty(propUri), mapper.invokeGetter(field.getName(), obj));
			}
		}
		
		for (Map.Entry<Property, Object> entry : properties.entrySet()) {
			Object value = entry.getValue();
			Property key = entry.getKey();
			//TODO Make better update statements
			root.removeAll(key);
			
			if (value != null) {
				if (value instanceof Resource) {
					model.add(root, entry.getKey(), (Resource) entry.getValue());
				} else if (value instanceof List) {
					for (Resource subRes : (List<Resource>) value) {
						model.add(root, entry.getKey(), subRes);
					}
				} else if (entry.getValue() != null) {
					model.add(root, entry.getKey(), entry.getValue().toString());
				}
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
		Model model = ontologyVariables.getModel();
		ResIterator iterator = model.listResourcesWithProperty(RDF.type, klass);
		List<R> results = new ArrayList<>();
		try {
			while (iterator.hasNext()) {
				R obj;
				obj = type.getDeclaredConstructor().newInstance();
				mapToObject(obj, iterator.next(), null, true, model);
				results.add(obj);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		model.close();
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
		Model model = ontologyVariables.getModel();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    if (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("subject");
		    	obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true, model);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		model.close();
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
		Model model = ontologyVariables.getModel();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    if (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("subject");
		    	obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true, model);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		model.close();
		return Optional.ofNullable(obj);
	}
	
	public Optional<R> findOne(QueryParam ...params) {
		String paramStr = "";
		for (QueryParam param: params) {
			paramStr += param.toString();
		}
		
		String queryStr = "SELECT ?subject\nWHERE {\n" + paramStr + "\n}";
		queryStr = ontologyVariables.getPreffixes() + queryStr;
		
		Query query = QueryFactory.create(queryStr);
		Model model = ontologyVariables.getModel();
		R obj = null;
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    if (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("?subject");
		    	obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true, model);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		model.close();
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
		Model model = ontologyVariables.getModel();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    while (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource("?subject");
		    	R obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true, model);
		    	list.add(obj);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		model.close();
		return list;
	}
	
	/**
	 * Execute a SPARQL query
	 * 
	 * 
	 * @param queryStr Query string
	 * @return Corresponding List of specified type
	 */
	public List<R> query(String queryStr) {
		List<R> list = new ArrayList<>(); 
		String words[] = queryStr.split("\\s+");
		String subject = "?subject";
		for (String word: words) {
			if (word.startsWith("?")) {
				subject = word;
				break;
			}
		}
		queryStr = ontologyVariables.getPreffixes() + queryStr;
		
		Query query = QueryFactory.create(queryStr);
		Model model = ontologyVariables.getModel();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    while (results.hasNext()) {
		    	QuerySolution soln = results.next();
		    	Resource res = soln.getResource(subject);
		    	R obj = type.getDeclaredConstructor().newInstance();
		    	mapToObject(obj, res, null, true, model);
		    	list.add(obj);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		model.close();
		return list;
	}
	
	/**
	 * 
	 * Remove an Entity
	 * 
	 * @param obj
	 */
	public void remove(R obj) {
		Model model = ontologyVariables.getModel();
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
		model.close();
	}
	
	/**
	 * Remove an entity base on its unique URI
	 * 
	 * @param uri
	 */
	public void remove(String uri) {
		Model model = ontologyVariables.getModel();
		String baseUri = ontologyVariables.getBaseUri();
		String uniqueUri;
		if (uri.startsWith(baseUri)) {
			uniqueUri = uri;
		} else {
			uniqueUri = baseUri + uri;
		}
		
		StmtIterator iter = model.getResource(uniqueUri).listProperties();
		if (iter != null) {
			model = model.remove(iter);
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
		
		model.close();
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
		Model model = ontologyVariables.getModel();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qexec.execSelect();
		    
		    return results.hasNext();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			model.close();
		}

		model.close();
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
		Model model = ontologyVariables.getModel();
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
		} finally {
			model.close();
		}

		model.close();
		return false;
	}
	
	public Map<String, List<R>> groupBy(String property) {
		Map<String, List<R>> group = new HashMap<>();
		try {
			List<R> list = this.find();
			IModelExecutor mapper = modelManager.getExecutor(type);
			Field field = type.getDeclaredField(property);
			if (field.getType() == List.class) {
				throw new IllegalArgumentException();
			}
			
			for (R obj: list) {
				String fieldValue;
				try {
					if (field.getType().isAnnotationPresent(OntologyObject.class)) {
						IModelExecutor subMapper = modelManager.getExecutor(field.getType());
						fieldValue = String.valueOf(subMapper.invokeGetName(mapper.invokeGetter(field.getName(), obj)));
					} else {
						fieldValue = String.valueOf(mapper.invokeGetter(field.getName(), obj));
					}
				} catch (Exception e) {
					fieldValue = "no_category";
				}
				
				if (!group.containsKey(fieldValue)) {
					group.put(fieldValue, new ArrayList<>());
				}
				group.get(fieldValue).add(obj);
			}
			
			return group;
		} catch (NoSuchFieldException e) {
			log.info("Field {} not found for grouping", property);
			
			return new HashMap<>();
		} catch (IllegalArgumentException e) {
			log.info("Cannot group field type of List");
			
			return new HashMap<>();
		}
	}
}
