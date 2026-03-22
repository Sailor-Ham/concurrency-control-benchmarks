package com.stu.benchmark.domain.student.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

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
@DynamicUpdate
@Table(
	name = "student",
	uniqueConstraints = {
		@jakarta.persistence.UniqueConstraint(
			name = "uk_student_student_number",
			columnNames = "student_number"
		)
	}
)
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Student {

	@Id
	@Column(name = "student_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;

	@Column(nullable = false, length = 20)
	String studentNumber;

	@Column(nullable = false, length = 50)
	String name;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	LocalDateTime updatedAt;

	@Builder
	public Student(String studentNumber, String name) {

		if (studentNumber == null || studentNumber.isBlank()) {
			throw new IllegalArgumentException("학번은 필수입니다.");
		}

		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("이름은 필수입니다.");
		}

		this.studentNumber = studentNumber;
		this.name = name;
	}
}
