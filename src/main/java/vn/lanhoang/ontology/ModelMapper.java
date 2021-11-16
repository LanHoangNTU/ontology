package vn.lanhoang.ontology;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.ReflectionUtils;
import vn.lanhoang.ontology.annotation.Name;

public class ModelMapper implements IModelExecutor {
	
	private static final Logger log = LoggerFactory.getLogger(ModelMapper.class);

	private Map<Field, Method[]> fieldMaps;
	private Method setName;
	private Field nameField;
	private final Class<?> type;

	public ModelMapper(Class<?> type) {
		this.type = type;
		ModelManager modelManager = ModelManager.instance();
		if (modelManager.getExecutors().containsKey(type)) {
			return;
		}
		final AtomicBoolean checked = new AtomicBoolean(false);
		Method[] methods = ReflectionUtils.getAllDeclaredMethods(type);

		List<Method> getterList = new ArrayList<>();
		List<Method> setterList = new ArrayList<>();
		fieldMaps = new Hashtable<>();
		
		validateAnnotations();
		
		for (Method method : methods) {
			if (method.getName().startsWith("get")) {
				getterList.add(method);
			} else if (method.getName().startsWith("set")) {
				setterList.add(method);
			}
		}

		AtomicInteger atomicIndex = new AtomicInteger(0);
		ReflectionUtils.doWithFields(type, field -> {
			String fieldName = field.getName();
			int index = atomicIndex.get();

			Optional<Method> getter = getterList.stream()
					.filter( med ->
							(med.getName().length() == (fieldName.length() + 3))
									&& med.getName().toLowerCase().endsWith(fieldName.toLowerCase()))
					.findFirst();

			Optional<Method> setter = setterList.stream()
					.filter( med ->
							(med.getName().length() == (fieldName.length() + 3))
									&& med.getName().toLowerCase().endsWith(fieldName.toLowerCase()))
					.findFirst();

			if (!getter.isPresent() || !setter.isPresent()) {
				throw new IllegalArgumentException("No getter/setter method for field '" + field.getName() + "' found");
			} else {
				Method[] getSet =  { getter.get(), setter.get() };
				fieldMaps.put(field, getSet);
				atomicIndex.set(index + 2);

				if (!checked.get()) {
					if (field.isAnnotationPresent(Name.class)) {
						this.setName = setter.get();
						this.nameField = field;
						checked.set(true);
					}
				}
			}
		});
		
		modelManager.getExecutors().put(type, this);
	}

	@Override
	public Object invokeGetter(Field field, Object obj) throws IllegalAccessException, InvocationTargetException {
		if (fieldMaps.containsKey(field)) {
			return fieldMaps.get(field)[0].invoke(obj);
		}

		return null;
		
	}

	@Override
	public void invokeSetter(Field field, Object obj, Object val) throws InvocationTargetException, IllegalAccessException {
		if (fieldMaps.containsKey(field)) {
			Method setter = fieldMaps.get(field)[1];
			String value = val.toString();
			try {
				if (field.getType() == String.class) {
					if (value.contains("^^")) {
						value = value.substring(0, value.indexOf('^'));
					}
					setter.invoke(obj, value);
				} else {
					setter.invoke(obj, field.getType().cast(val));
				}
			} catch (ClassCastException e) {
				if (field.getType().equals(List.class)) {
					setter.invoke(obj, field.getType().cast(val));
				} else {
					parse(field.getType().getName(), setter, obj, val);
				}
			}
		}
	}

	@Override
	public void invokeSetName(Object obj, Object val) throws InvocationTargetException, IllegalAccessException {
		setName.invoke(obj, nameField.getType().cast(val.toString()));
	}

	@Override
	public Object invokeGetName(Object obj) throws InvocationTargetException, IllegalAccessException {
		if (fieldMaps.containsKey(nameField)) {
			return fieldMaps.get(nameField)[0].invoke(obj);
		}
		return null;
	}

	@Override
	public Field getNameField() {
		return this.nameField;
	}

	private void validateAnnotations() {
		AtomicBoolean hasName = new AtomicBoolean(false);
		ReflectionUtils.doWithFields(type, field -> {
			if (field.isAnnotationPresent(Name.class)) {
				if (hasName.get()) { throw new IllegalArgumentException("Ontology object have more than one name"); }
				hasName.set(true);
			}
		});

		if (!hasName.get()) { throw new NullPointerException("Ontology object's unique uri not present"); }
	}
	
	public void parse(String className, Method setter, Object obj, Object val) {
		String value = val.toString();
		if (value.contains("^^")) {
			value = value.substring(0, value.indexOf('^'));
		}
		try {
			if (Objects.equals(className, Integer.class.getName())) {
				setter.invoke(obj, Integer.parseInt(value));
			} else if (Objects.equals(className, Long.class.getName())) {
				setter.invoke(obj, Long.parseLong(value));
			} else if (Objects.equals(className, Float.class.getName())) {
				setter.invoke(obj, Float.parseFloat(value));
			} else if (Objects.equals(className, Double.class.getName())) {
				setter.invoke(obj, Double.parseDouble(value));
			} else {
				setter.invoke(obj, val);
			}
		} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
			log.error("Error while casting to {} from {} - Message: {}", val.getClass().getName(), className, e.getMessage());
		}
	}
}
