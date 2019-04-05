import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BeanUtils {
	private static Lookup lookup = MethodHandles.lookup();
	private static HashMap<Class<?>, HashMap<String, Function<?, ?>>> getterMap = new HashMap<>();
	private static HashMap<Class<?>, HashMap<String, BiConsumer<?, ?>>> setterMap = new HashMap<>();
	private static HashMap<Class<?>, Field[]> fieldsMap = new HashMap<>(256);
	private static HashMap<Class<?>, HashMap<String, Field>> fieldMap = new HashMap<>(256);

	public static <O, T> void copyProperties(O originBean, T targetBean) {
		Class<?> beanclass = originBean.getClass();
		Field[] beanfields = getFields(beanclass);

		for (Field beanField : beanfields) {
			String fieldName = beanField.getName();
			copyProperty(originBean, targetBean, fieldName);
		}
	}

	@SuppressWarnings("unchecked")
	public static <O, T> void copyProperties(O originBean, T targetBean,
			EnumMap<? extends BasicBeanEnum, ? extends BasicBeanEnum> fieldsMap) {
		fieldsMap.forEach((beanField, targetField) -> {
			copyProperty(originBean, targetBean, (Function<O, Object>) beanField.getter(),
					(BiConsumer<T, Object>) targetField.setter());
		});
	}

	public static <O, T> void copyProperties(O originBean, T targetBean, BasicBeanEnum originBeanEnum,
			BasicBeanEnum targetBeanEnum) {
		BasicBeanEnum[] values = originBeanEnum.getValues();
		for (BasicBeanEnum value : values) {
			String fieldName = value.getName();
			copyProperty(originBean, targetBean, value, targetBeanEnum.getValue(fieldName));
		}
	}

	public static <O, T> void copyPropertiesPartialNameDiffer(O originBean, T targetBean,
			Map<String, String> partialDifferNamesMap) {
		Class<?> originBeanClass = originBean.getClass();
		Field[] originBeanFields = getFields(originBeanClass);
		for (Field field : originBeanFields) {
			String fieldName = field.getName();
			if (partialDifferNamesMap.containsKey(fieldName)) {
				String targetBeanFieldName = partialDifferNamesMap.get(fieldName);
				copyProperty(originBean, targetBean, fieldName, targetBeanFieldName);
			} else {
				copyProperty(originBean, targetBean, fieldName);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static <O, T> void copyPropertiesPartialNameDiffer(O originBean, T targetBean,
			EnumMap<? extends BasicBeanEnum, ? extends BasicBeanEnum> partialDifferNamesMap) {
		Class<?> originBeanClass = originBean.getClass();
		Field[] originBeanFields = getFields(originBeanClass);
		HashSet<String> ignoreFieldsSet = new HashSet<>();

		partialDifferNamesMap.forEach((originBeanFieldEnum, targetBeanFieldEnum) -> {
			ignoreFieldsSet.add(originBeanFieldEnum.name());
			copyProperty(originBean, targetBean, (Function<O, Object>) originBeanFieldEnum.getter(),
					(BiConsumer<T, Object>) targetBeanFieldEnum.setter());
		});
		for (Field originBeanField : originBeanFields) {
			String originBeanFilesName = originBeanField.getName();
			if (!ignoreFieldsSet.contains(originBeanFilesName))
				copyProperty(originBean, targetBean, originBeanFilesName);
		}
	}

	public static <O, T> void copyPropertiesIgnore(O originBean, T targetBean, String... ignoreFieldName) {
		HashSet<String> ignoreFieldNameSet = new HashSet<>();
		for (String fieldName : ignoreFieldName) {
			ignoreFieldNameSet.add(fieldName);
		}
		copyPropertiesIgnore(originBean, targetBean, ignoreFieldNameSet);
	}

	public static <O, T> void copyPropertiesIgnore(O originBean, T targetBean, Set<String> ignorePropertiesName) {
		Class<?> originBeanClass = originBean.getClass();
		Field[] originBeanFields = getFields(originBeanClass);
		for (Field field : originBeanFields) {
			String fieldName = field.getName();
			if (!ignorePropertiesName.contains(fieldName)) {
				copyProperty(originBean, targetBean, fieldName);
			}
		}
	}

	public static <O, T> void copyProperty(O originBean, T targetBean, String fieldName) {
		copyProperty(originBean, targetBean, fieldName, fieldName);
	}

	@SuppressWarnings("unchecked")
	public static <O, T> void copyProperty(O originBean, T targetBean, BasicBeanEnum originBeanField,
			BasicBeanEnum targetBeanField) {
		copyProperty(originBean, targetBean, (Function<O, ? super Object>) originBeanField.getter(),
				(BiConsumer<T, ? super Object>) targetBeanField.setter());
	}

	public static <O, T> void copyProperty(O originBean, T targetBean, Function<O, ? super Object> originBeanGetter,
			BiConsumer<T, ? super Object> targetBeanSetter) {
		if (originBeanGetter != null && targetBeanSetter != null) {
			Object value = originBeanGetter.apply(originBean);
			targetBeanSetter.accept(targetBean, value);
		}
	}

	@SuppressWarnings("unchecked")
	public static <O, T> void copyProperty(O originBean, T targetBean, String originBeanFieldName,
			String targetBeanFieldName) {
		Class<?> oringinBeanClass = originBean.getClass();
		Class<?> targetBeanClass = targetBean.getClass();
		Field originBeanField = getField(targetBeanClass, originBeanFieldName);
		Field targetBeanField = getField(targetBeanClass, targetBeanFieldName);
		
		if (originBeanField == null || targetBeanField == null)
			return;
		Function<O, ? super Object> originBeanFieldGetter = (Function<O, Object>) findGetter(oringinBeanClass,
				originBeanFieldName, originBeanField.getType());
		BiConsumer<T, ? super Object> targetBeanFieldSetter = (BiConsumer<T, Object>) findSetter(targetBeanClass,
				targetBeanFieldName, targetBeanField.getType());
		copyProperty(originBean, targetBean, originBeanFieldGetter, targetBeanFieldSetter);
	}

	@SuppressWarnings("unchecked")
	public static <T, F> Function<T, F> findGetter(Class<T> beanClass, String fieldName) {
		try {
			Class<?> fieldClass = beanClass.getDeclaredField(fieldName).getType();
			return (Function<T, F>) findGetter(beanClass, fieldName, fieldClass);
		} catch (NoSuchFieldException | SecurityException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T, F> Function<T, F> findGetter(Class<T> beanClass, String fieldName, Class<F> fieldClass) {
		HashMap<String, Function<?, ?>> map = getterMap.get(beanClass);
		if (map != null && map.containsKey(fieldName)) {
			return (Function<T, F>) map.get(fieldName);
		}
		CallSite site;
		Function<T, F> getter = null;
		String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
		try {
			site = LambdaMetafactory.metafactory(lookup, "apply", MethodType.methodType(Function.class),
					MethodType.methodType(Object.class, Object.class),
					lookup.findVirtual(beanClass, methodName, MethodType.methodType(fieldClass)),
					MethodType.methodType(fieldClass, beanClass));
			getter = (Function<T, F>) site.getTarget().invokeExact();
		} catch (NoSuchMethodException | IllegalAccessException | LambdaConversionException e) {
		} catch (Throwable e2) {
		}

		if (map == null) {
			map = new HashMap<>();
			getterMap.put(beanClass, map);
		}
		map.put(fieldName, getter);
		return getter;
	}

	@SuppressWarnings("unchecked")
	public static <T, F> BiConsumer<T, F> findSetter(Class<T> beanClass, String fieldName) {
		try {
			Class<?> fieldClass = beanClass.getDeclaredField(fieldName).getType();
			return (BiConsumer<T, F>) findSetter(beanClass, fieldName, fieldClass);
		} catch (NoSuchFieldException | SecurityException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T, F> BiConsumer<T, F> findSetter(Class<T> beanClass, String fieldName, Class<F> fieldClass) {
		HashMap<String, BiConsumer<?, ?>> map = setterMap.get(beanClass);
		if (map != null && map.containsKey(fieldName)) {
			return (BiConsumer<T, F>) map.get(fieldName);
		}
		CallSite setterSite;
		BiConsumer<T, F> setter = null;
		String methodName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
		try {
			setterSite = LambdaMetafactory.metafactory(lookup, "accept", MethodType.methodType(BiConsumer.class),
					MethodType.methodType(void.class, Object.class, Object.class),
					lookup.findVirtual(beanClass, methodName, MethodType.methodType(void.class, fieldClass)),
					MethodType.methodType(void.class, beanClass, fieldClass));
			setter = (BiConsumer<T, F>) setterSite.getTarget().invokeExact();
		} catch (NoSuchMethodException | IllegalAccessException | LambdaConversionException e) {
		} catch (Throwable e2) {
		}

		if (map == null) {
			map = new HashMap<>();
			setterMap.put(beanClass, map);
		}
		map.put(fieldName, setter);
		return setter;
	}

	private static Field[] getFields(Class<?> clazz) {
		Field[] fields = fieldsMap.get(clazz);
		if (fields == null) {
			fields = clazz.getDeclaredFields();
			HashMap<String, Field> classFieldsMap = fieldMap.get(clazz);
			if (classFieldsMap == null) {
				classFieldsMap = new HashMap<>();
				fieldMap.put(clazz, classFieldsMap);
			}
			for (Field field : fields) {
				field.setAccessible(true);
				classFieldsMap.put(field.getName(), field);
			}
			fieldsMap.put(clazz, fields);
		}
		return fields;
	}

	private static Field getField(Class<?> clazz, String name) {
		HashMap<String, Field> classFieldsMap = fieldMap.get(clazz);
		if (classFieldsMap == null) {
			classFieldsMap = new HashMap<>();
			fieldMap.put(clazz, classFieldsMap);
		}
		Field field = classFieldsMap.get(name);
		if (field == null) {
			try {
				field = clazz.getDeclaredField(name);
			} catch (NoSuchFieldException | SecurityException e) {
				return null;
			}
			if (field != null) {
				field.setAccessible(true);
				classFieldsMap.put(name, field);
			}
		}
		return field;
	}

}
