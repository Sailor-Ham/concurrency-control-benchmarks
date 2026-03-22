package com.stu.benchmark.domain.enrollment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stu.benchmark.domain.enrollment.entity.Enrollment;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
	boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
}
