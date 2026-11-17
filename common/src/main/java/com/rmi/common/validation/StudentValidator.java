package com.rmi.common.validation;

import com.rmi.common.model.Student;

public final class StudentValidator {
    private StudentValidator() {}

    public static void validateId(String studentId) {
        if (studentId == null || studentId.isBlank() || studentId.length() > 20) {
            throw new IllegalArgumentException("Mã sinh viên phải có từ 1 đến 20 ký tự");
        }
    }

    public static void validate(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("Dữ liệu sinh viên không được để trống");
        }
        validateId(student.studentId());
        if (!student.deleted()) {
            if (student.fullName() == null || student.fullName().isBlank()
                    || student.fullName().length() > 100) {
                throw new IllegalArgumentException("Họ tên phải có từ 1 đến 100 ký tự");
            }
            if (student.className() == null || student.className().isBlank()
                    || student.className().length() > 50) {
                throw new IllegalArgumentException("Tên lớp phải có từ 1 đến 50 ký tự");
            }
        }
    }
}
