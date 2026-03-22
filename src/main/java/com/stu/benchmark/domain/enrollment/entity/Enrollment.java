package com.stu.benchmark.domain.enrollment.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Entity
@DynamicInsert
@Table(
	name = "enrollment",
	uniqueConstraints = {
		@jakarta.persistence.UniqueConstraint(
			name = "uk_enrollment_student_id_course_id",
			columnNames = {"student_id", "course_id"}
		)
	}
)
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Enrollment {

	@Id
	@Column(name = "enrollment_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;

	@Column(name = "student_id", nullable = false)
	Long studentId;

	@Column(name = "course_id", nullable = false)
	Long courseId;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	LocalDateTime createdAt;

	@Builder
	public Enrollment(Long studentId, Long courseId) {

		if (studentId == null || courseId == null) {
			throw new IllegalArgumentException("studentId와 courseId는 null일 수 없습니다.");
		}
		
		this.studentId = studentId;
		this.courseId = courseId;
	}
}
