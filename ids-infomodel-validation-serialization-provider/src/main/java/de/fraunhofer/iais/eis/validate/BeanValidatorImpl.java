package de.fraunhofer.iais.eis.validate;

import de.fraunhofer.iais.eis.spi.BeanValidator;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BeanValidatorImpl implements BeanValidator {

    public <T> void validate(T objToValidate) throws ConstraintViolationException {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<T>> constraintViolations = validator.validate(objToValidate);
        if (!constraintViolations.isEmpty()) {
            Collection<String> messages = new HashSet<>();
            constraintViolations.stream().forEach(x -> messages.add(x.getPropertyPath() + " " + x.getMessage()));

            ConstraintViolationException exc = new ConstraintViolationException(messages);
            throw exc;
        }
    }

}
