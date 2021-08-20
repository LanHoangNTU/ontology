package vn.lanhoang.ontology;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.lanhoang.ontology.annotation.Name;

public class ModelMapper implements IModelExecutor {
	
	private static final Logger log = LoggerFactory.getLogger(ModelMapper.class);

	private Field[] fields;
	private String[] fieldNameArray;
	private Method[] getSetArray;
	private Method setName;
	private Field nameField;

	public ModelMapper(Class<?> type) {
		ModelManager modelManager = ModelManager.instance();
		if (modelManager.getExecutors().containsKey(type)) {
			return;
		}
		boolean checked = false;
		fields = type.getDeclaredFields();
		Method methods[] = type.getDeclaredMethods();
		List<Method> getterList = new ArrayList<>(fields.length);
		List<Method> setterList = new ArrayList<>(fields.length);

		fieldNameArray = new String[fields.length];
		getSetArray = new Method[fields.length * 2];
		
		validateAnnotations();
		
		for(Method method : methods) {
			if (method.getName().startsWith("get")) {
				getterList.add(method);
			} else if (method.getName().startsWith("set")) {
				setterList.add(method);
			}
		}
		
		int index = 0;
		for (Field field : fields) {
			String fieldName = field.getName();
			
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
			
			if (getter.isEmpty()) {
				throw new IllegalArgumentException("No getter method for field '" + field.getName() + "' found");
			} else if (setter.isEmpty()) {
				throw new IllegalArgumentException("No setter method for field '" + field.getName() + "' found");
			} else {
				fieldNameArray[index / 2] = field.getName();
				getSetArray[index] = getter.get();
				getSetArray[index + 1] = setter.get();
				index += 2;
				
				if (!checked) {
					if (field.isAnnotationPresent(Name.class)) {
						this.setName = setter.get();
						this.nameField = field;
						checked = true;
					}
				}
			}
		}
		
		modelManager.getExecutors().put(type, this);
	}

	@Override
	public Object invokeGetter(String field, Object obj) {
		int index = ArrayUtils.indexOf(fieldNameArray, field);
		if (index >= 0) {
			try {
				return getSetArray[index * 2].invoke(obj);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return null;
		
	}

	@Override
	public void invokeSetter(String field, Object obj, Object val) {
		int index = ArrayUtils.indexOf(fieldNameArray, field);
		int setIndex = index * 2 + 1;
		if (index >= 0) {
			String value = val.toString();
			try {
				if (fields[index].getType() == String.class) {
					if (value.contains("^^")) {
						value = value.substring(0, value.indexOf('^'));
					}
					getSetArray[setIndex].invoke(obj, value);
				} else {
					getSetArray[setIndex].invoke(obj, fields[index].getType().cast(val));
				}
			} catch (ClassCastException e) {
				try {
					if (fields[index].getType().equals(List.class)) {
						getSetArray[setIndex].invoke(obj, fields[index].getType().cast(val));
					} else {
						parse(fields[index].getType().getName(), getSetArray[setIndex], obj, val);
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					log.error("Error while casting to {} from {}", val.getClass().getName(), fields[index].getType().getName());
					e1.printStackTrace();
				}
			} catch (Exception e) {
				log.error("Error while casting to {} from {}", val.getClass().getName(), fields[index].getType().getName());
				e.printStackTrace();
			}
		}
	}

	@Override
	public void invokeSetName(Object obj, Object val) {
		try {
			// TODO Handle different types
			setName.invoke(obj, nameField.getType().cast(val.toString()));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public Object invokeGetName(Object obj) {
		int index = ArrayUtils.indexOf(fieldNameArray, nameField.getName());
		if (index >= 0) {
			try {
				return getSetArray[index * 2].invoke(obj);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	@Override
	public Field[] getAllFields() {
		return fields;
	}
	
	private void validateAnnotations() {
		boolean hasName = false;
		for (Field field : fields) {
			if (field.isAnnotationPresent(Name.class)) {
				if (hasName) { throw new IllegalArgumentException("Ontology object have more than one name"); }
				hasName = true;
			} 
		}

		if (!hasName) { throw new NullPointerException("Ontology object's unique uri not present"); }
	}
	
	public void parse(String className, Method setter, Object obj, Object val) {
		try {
			if (className == Integer.class.getName()) {
				setter.invoke(obj, Integer.parseInt(val.toString()));
			} else if (className == Long.class.getName()) {
				setter.invoke(obj, Long.parseLong(val.toString()));
			} else if (className == Float.class.getName()) {
				setter.invoke(obj, Float.parseFloat(val.toString()));
			} else if (className == Double.class.getName()) {
				setter.invoke(obj, Double.parseDouble(val.toString()));
			} else {
				setter.invoke(obj, val);
			}
		} catch (IllegalArgumentException e) {
			log.error("Error while casting to {} from {}", val.getClass().getName(), className);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
}
