package com.aviation.fmc.validation;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a validation operation.
 * Contains validation status, errors, and warnings.
 */
@Value
public class ValidationResult {

    boolean valid;
    List<ValidationError> errors;
    List<ValidationWarning> warnings;

    public ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, errors, List.of());
    }

    public static ValidationResult failure(ValidationError error) {
        return new ValidationResult(false, List.of(error), List.of());
    }

    public static ValidationResult withWarnings(List<ValidationWarning> warnings) {
        return new ValidationResult(true, List.of(), warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Combine multiple validation results.
     */
    public static ValidationResult combine(List<ValidationResult> results) {
        boolean allValid = results.stream().allMatch(ValidationResult::isValid);
        List<ValidationError> allErrors = new ArrayList<>();
        List<ValidationWarning> allWarnings = new ArrayList<>();

        for (ValidationResult result : results) {
            allErrors.addAll(result.getErrors());
            allWarnings.addAll(result.getWarnings());
        }

        return new ValidationResult(allValid, allErrors, allWarnings);
    }

    @Value
    public static class ValidationError {
        String code;
        String message;
        String field;
        Object invalidValue;

        public static ValidationError of(String code, String message) {
            return new ValidationError(code, message, null, null);
        }

        public static ValidationError of(String code, String message, String field) {
            return new ValidationError(code, message, field, null);
        }

        public static ValidationError of(String code, String message, String field, Object invalidValue) {
            return new ValidationError(code, message, field, invalidValue);
        }
    }

    @Value
    public static class ValidationWarning {
        String code;
        String message;
        String field;

        public static ValidationWarning of(String code, String message) {
            return new ValidationWarning(code, message, null);
        }

        public static ValidationWarning of(String code, String message, String field) {
            return new ValidationWarning(code, message, field);
        }
    }
}
