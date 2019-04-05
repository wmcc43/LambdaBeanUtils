import java.util.function.BiConsumer;
import java.util.function.Function;

public interface BasicBeanEnum {
	
	public Function<?, ?> getter();
	
	public BiConsumer<?, ?> setter();
	
	public Class<?> getMetaClass();
	
	public BasicBeanEnum[] getValues();
	
	public BasicBeanEnum getValue(String name);
	
	public String getName();
}
