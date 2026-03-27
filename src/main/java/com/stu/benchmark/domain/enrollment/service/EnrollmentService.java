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
import com.stu.benchmark.global.benchmark.LockBenchmarkWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final CourseRepository courseRepository;
	private final StudentRepository studentRepository;
	private final EnrollmentRepository enrollmentRepository;

	private final LockBenchmarkWrapper benchmarkWrapper;

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

		// 학생 조회
		Student student = studentRepository.findById(request.studentId())
			.orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));

		final Course[] courseHolder = new Course[1];

		benchmarkWrapper.executeWithMetrics(
			"pessimistic",

			// [Wait Time 측정] DB에 SELECT FOR UPDATE를 날려 락을 획득할 때까지의 시간
			() -> {
				courseHolder[0] = courseRepository.findByIdWithPessimisticLock(request.courseId())
					.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));
			},

			// [Service Time 측정] 락 획득 후 순수 비즈니스 로직 수행 시간
			() -> {
				Course course = courseHolder[0];

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
			},

			// [Lock Releaser] 비관적 락은 트랜잭션 종료 시 자동 해제되므로 null
			null
		);
	}
}
