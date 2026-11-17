package com.rmi.common.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rmi.common.model.Student;
import org.junit.jupiter.api.Test;

class StudentValidatorTest {
    @Test
    void acceptsValidStudent() {
        assertDoesNotThrow(() -> StudentValidator.validate(
                new Student("SV001", "Nguyễn Văn A", "CNTT01", 0, false)));
    }

    @Test
    void rejectsMissingName() {
        assertThrows(IllegalArgumentException.class, () -> StudentValidator.validate(
                new Student("SV001", "", "CNTT01", 0, false)));
    }

    @Test
    void deletedStudentOnlyRequiresId() {
        assertDoesNotThrow(() -> StudentValidator.validate(
                new Student("SV001", null, null, 1, true)));
    }
}
