package com.aviation.fmc.parser;

import com.aviation.fmc.validation.ValidationResult;
import lombok.Value;

import java.util.List;

/**
 * Represents the output of a flight plan parsing operation.
 * Contains the parsed flight plan, validation results, and any warnings/errors.
 * 
 * Invariants:
 * - If success is true, flightPlan must be non-null
 * - Errors indicate parsing failures, warnings indicate potential issues
 */
@Value
public class ParserOutput {

    /**
     * Whether parsing was successful.
     */
    boolean success;

    /**
     * The parsed flight plan (null if parsing failed).
     */
    FlightPlan flightPlan;

    /**
     * Validation results containing errors and warnings.
     */
    ValidationResult validationResult;

    /**
     * Raw input that was parsed.
     */
    String rawInput;

    /**
     * Format of the input (e.g., "ICAO", "FAA", "ARINC424").
     */
    String inputFormat;

    /**
     * Parse warnings (non-fatal issues).
     */
    List<ParseWarning> warnings;

    /**
     * Parse errors (fatal issues that prevented successful parsing).
     */
    List<ParseError> errors;

    public static ParserOutput success(FlightPlan flightPlan, String rawInput, String format) {
        return new ParserOutput(
            true,
            flightPlan,
            ValidationResult.success(),
            rawInput,
            format,
            List.of(),
            List.of()
        );
    }

    public static ParserOutput successWithWarnings(
            FlightPlan flightPlan, 
            String rawInput, 
            String format,
            List<ParseWarning> warnings) {
        return new ParserOutput(
            true,
            flightPlan,
            ValidationResult.withWarnings(warnings.stream()
                .map(w -> ValidationResult.ValidationWarning.of(w.code, w.message))
                .toList()),
            rawInput,
            format,
            warnings,
            List.of()
        );
    }

    public static ParserOutput failure(
            String rawInput,
            String format,
            List<ParseError> errors) {
        return new ParserOutput(
            false,
            null,
            ValidationResult.failure(errors.stream()
                .map(e -> ValidationResult.ValidationError.of(e.code, e.message))
                .toList()),
            rawInput,
            format,
            List.of(),
            errors
        );
    }

    @Value
    public static class ParseWarning {
        String code;
        String message;
        int lineNumber;
        int columnNumber;

        public static ParseWarning of(String code, String message) {
            return new ParseWarning(code, message, 0, 0);
        }

        public static ParseWarning of(String code, String message, int line, int column) {
            return new ParseWarning(code, message, line, column);
        }
    }

    @Value
    public static class ParseError {
        String code;
        String message;
        int lineNumber;
        int columnNumber;
        String offendingText;

        public static ParseError of(String code, String message) {
            return new ParseError(code, message, 0, 0, null);
        }

        public static ParseError of(String code, String message, String offendingText) {
            return new ParseError(code, message, 0, 0, offendingText);
        }

        public static ParseError of(String code, String message, int line, int column, String offendingText) {
            return new ParseError(code, message, line, column, offendingText);
        }
    }
}
