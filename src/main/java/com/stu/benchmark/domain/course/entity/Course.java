package com.stu.benchmark.domain.course.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
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
@Table(name = "course")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Course {

	@Id
	@Column(name = "course_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;

	@Column(nullable = false, length = 100)
	String title;

	@Column(nullable = false)
	Long maxCapacity;

	@ColumnDefault("0")
	@Column(nullable = false)
	Long enrolledCount;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	LocalDateTime updatedAt;

	@Builder
	public Course(String title, Long maxCapacity) {
		this.title = title;
		this.maxCapacity = maxCapacity;
		this.enrolledCount = 0L;
	}

	public void increaseEnrolledCount() {

		if (this.enrolledCount >= this.maxCapacity) {
			throw new IllegalStateException("수강 인원이 초과되었습니다.");
		}

		this.enrolledCount++;
	}
}
