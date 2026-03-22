package com.stu.benchmark.domain.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollmentCreateRequest(

	@NotNull(message = "학생 ID는 필수입니다.")
	Long studentId,

	@NotNull(message = "강의 ID는 필수입니다.")
	Long courseId
) {
}
