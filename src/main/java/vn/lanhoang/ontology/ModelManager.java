package vn.lanhoang.ontology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModelManager {
	
	private static ModelManager INSTANCE;
	private Map<Class<?>, IModelExecutor> executors;
	private Set<Class<?>> supportClasses;
	
	private ModelManager() {
		executors = new HashMap<>();
		supportClasses = new HashSet<>(
		Arrays.asList(
			Integer.class, Short.class, Byte.class,
			Long.class, Float.class, Double.class,
			Boolean.class, String.class
		));
	}

	public static ModelManager instance() {
		if (INSTANCE == null) {
			INSTANCE = new ModelManager();
		}
		
		return INSTANCE;
	}

	public Map<Class<?>, IModelExecutor> getExecutors() {
		return executors;
	}
	
	public IModelExecutor getExecutor(Class<?> type) {
		return executors.get(type);
	}
	
	public boolean isSupported(Class<?> type) {
		return supportClasses.contains(type);
	}
	
	public static <T> List<T> createList(Class<T> type) {
		return new ArrayList<T>();
	}
}
