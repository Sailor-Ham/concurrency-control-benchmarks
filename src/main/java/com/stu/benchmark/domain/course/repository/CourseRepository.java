package com.stu.benchmark.domain.course.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stu.benchmark.domain.course.entity.Course;

import jakarta.persistence.LockModeType;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Course c WHERE c.id = :courseId")
	Optional<Course> findByIdWithPessimisticLock(@Param("courseId") Long courseId);
}
