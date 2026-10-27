package ai.flowguard.core.web;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks LedgerGuard REST controllers under {@code /api/v1/{segment}}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping
public @interface CustomLedgerRestController {

    @AliasFor(annotation = RequestMapping.class, attribute = "value")
    String value();

    /** Explicit Spring bean name when several controllers share the same {@link #value()} path prefix. */
    @AliasFor(annotation = RestController.class, attribute = "value")
    String name() default "";
}
