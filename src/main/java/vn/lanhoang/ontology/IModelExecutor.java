package vn.lanhoang.ontology;

import java.lang.reflect.Field;

public interface IModelExecutor {
	public Object invokeGetter(String field, Object obj);
	public void invokeSetter(String field, Object obj, Object val);
	public void invokeSetName(Object obj, Object val);
	public Object invokeGetName(Object obj);
	public Field[] getAllFields();
}
