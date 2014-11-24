package propertor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Created by Pietro Caselani
 * On 9/30/14
 * Propertor
 */
@Retention(CLASS)
@Target(FIELD)
public @interface Property {
	Visibility getter() default Visibility.PUBLIC;
	Visibility setter() default Visibility.PUBLIC;
}