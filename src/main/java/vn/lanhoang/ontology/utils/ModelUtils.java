package vn.lanhoang.ontology.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceRequiredException;
import org.apache.jena.rdf.model.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;
import vn.lanhoang.ontology.IModelExecutor;
import vn.lanhoang.ontology.ModelManager;
import vn.lanhoang.ontology.annotation.OntologyObject;
import vn.lanhoang.ontology.configuration.OntologyVariables;

public class ModelUtils {
	private static final ModelManager modelManager = ModelManager.instance();

	private static final Logger LOG = LoggerFactory.getLogger(ModelUtils.class);
	
	// 2 Options: Do a recursive or ignore sub models
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <O> O mapToObject(Object obj, Resource res, Set<String> persistentKeys, boolean shouldLoop, 
								   Model model, OntologyVariables ontologyVariables, Class<O> type) throws InvocationTargetException, IllegalAccessException {
		
		if (persistentKeys == null) {
			persistentKeys = new HashSet<>();
		}
		IModelExecutor mapper = modelManager.getExecutor(obj.getClass());
		
		if (persistentKeys.contains(res.toString())) {
			mapper.invokeSetName(obj, res.toString());
			return (O) obj;
		}

		Set<String> finalPersistentKeys = persistentKeys;
		persistentKeys.add(res.toString());
		ReflectionUtils.doWithFields(obj.getClass(), field -> {
			Property p = model.getProperty(ontologyVariables.getBaseUri() + field.getName());

			if (res.hasProperty(p)) {

				Statement stmt = res.getProperty(p);
				Class<?> classField = field.getType();
				try {
					if (modelManager.isSupported(classField)) {
						mapper.invokeSetter(field, obj, stmt.getObject());
					} else if (classField.equals(List.class)) {
						ParameterizedType fieldListType = (ParameterizedType) field.getGenericType();
						Class<?> fieldListClass = (Class<?>) fieldListType.getActualTypeArguments()[0];
						List list = ModelManager.createList(fieldListClass);
						res.listProperties(p).forEach(propStmt -> {
							// ---- Set main key only (uri) ----
							try {
								Constructor<?> ctor = fieldListClass.getConstructor();
								Object object = ctor.newInstance(new Object[] {});

								object = mapToObject(object, propStmt.getResource(), finalPersistentKeys, false, model, ontologyVariables, fieldListClass);

								list.add(object);
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
						mapper.invokeSetter(field, obj, list);
					} else if (classField.isAnnotationPresent(OntologyObject.class)) {
						try {
							Constructor<?> ctor = classField.getConstructor();
							Object object = ctor.newInstance(new Object[] {});

							// ---- Recursive ----
							// object = mapToObject(object, stmt.getResource(), persistentKeys); // Recursive

							// ---- Set main key only (uri) ----
							if (shouldLoop) {
								object = mapToObject(object, stmt.getResource(), finalPersistentKeys, false, model, ontologyVariables, classField);
							} else {
								try {
									// Only set uri of sub model
									modelManager.getExecutor(field.getType()).invokeSetName(object, stmt.getResource());
								} catch(ResourceRequiredException e) {
									modelManager.getExecutor(field.getType()).invokeSetName(object, "");
								}
							}
							// -------------------
							mapper.invokeSetter(field, obj, object);
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						throw new IllegalArgumentException(String.format("Type %s not supported yet", classField.getName()));
					}
				} catch (IllegalAccessException | InvocationTargetException e) {
					LOG.error("Cannot map to desired object with, message: {}", e.getMessage());
				}

			}
		});
		
		mapper.invokeSetName(obj, res.toString());
		return (O) obj;
	}
}
