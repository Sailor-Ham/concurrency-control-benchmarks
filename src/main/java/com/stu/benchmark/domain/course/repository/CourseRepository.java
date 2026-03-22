package com.stu.benchmark.domain.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stu.benchmark.domain.course.entity.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
}
