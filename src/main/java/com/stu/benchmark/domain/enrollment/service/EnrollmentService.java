package com.stu.benchmark.domain.enrollment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.entity.Enrollment;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final CourseRepository courseRepository;
	private final StudentRepository studentRepository;
	private final EnrollmentRepository enrollmentRepository;

	/**
	 * [Baseline] 별도의 락이 없는 수강신청
	 */
	@Transactional
	public void enroll(EnrollmentCreateRequest request) {

		// 학생, 강의 조회
		Student student = studentRepository.findById(request.studentId())
			.orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
		Course course = courseRepository.findById(request.courseId())
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		// 수강신청
		if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
			throw new IllegalStateException("해당 강의는 이미 수강신청되었습니다.");
		}

		course.increaseEnrolledCount();

		// 수강신청 정보 저장
		enrollmentRepository.save(
			Enrollment.builder()
				.studentId(student.getId())
				.courseId(course.getId())
				.build()
		);
	}

	/**
	 * [Case 1: Pessimistic Lock] 강의 엔터티에 대해 Pessimistic Lock을 적용하여 동시성 문제를 방지하는 수강신청
	 */
	@Transactional
	public void enrollWithPessimisticLock(EnrollmentCreateRequest request) {

		// 학생, 강의(비관적 락) 조회
		Student student = studentRepository.findById(request.studentId())
			.orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
		Course course = courseRepository.findByIdWithPessimisticLock(request.courseId())
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		// 수강신청
		if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
			throw new IllegalStateException("해당 강의는 이미 수강신청되었습니다.");
		}

		course.increaseEnrolledCount();

		// 수강신청 정보 저장
		enrollmentRepository.save(
			Enrollment.builder()
				.studentId(student.getId())
				.courseId(course.getId())
				.build()
		);
	}
}
