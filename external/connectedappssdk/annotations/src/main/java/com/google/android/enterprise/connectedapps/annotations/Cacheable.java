package com.google.android.enterprise.connectedapps.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a cross profile method to indicate the result should be cached.
 *
 * <p>Annotated methods must return non void types.
 *
 * <p>Annotated methods must also be annotated with the {@link CrossProfile} annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Cacheable {

}
