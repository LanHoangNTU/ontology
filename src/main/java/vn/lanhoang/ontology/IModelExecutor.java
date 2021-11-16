package vn.lanhoang.ontology;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public interface IModelExecutor {
	Object invokeGetter(Field field, Object obj) throws IllegalAccessException, InvocationTargetException;
	void invokeSetter(Field field, Object obj, Object val) throws InvocationTargetException, IllegalAccessException;
	void invokeSetName(Object obj, Object val) throws InvocationTargetException, IllegalAccessException;
	Object invokeGetName(Object obj) throws InvocationTargetException, IllegalAccessException;
	Field getNameField();
}
