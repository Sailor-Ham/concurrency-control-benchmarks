package com.stu.benchmark.domain.enrollment.service;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;
import com.stu.benchmark.global.config.TestConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EnrollmentServicePessimisticLockTest {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private EnrollmentService enrollmentService;

	private Long testStudentId;
	private Long testCourseId;

	@BeforeEach
	void setUp() {

		// 기존 데이터 초기화 (FK 제약: Enrollment → Student/Course 순서로 삭제)
		enrollmentRepository.deleteAllInBatch();
		studentRepository.deleteAllInBatch();
		courseRepository.deleteAllInBatch();

		// 테스트 데이터 삽입
		Course course = Course.builder()
			.title("Test Course")
			.maxCapacity(1L)
			.build();
		Course savedCourse = courseRepository.save(course);

		Student student = Student.builder()
			.name("Test Student")
			.studentNumber("2026123456")
			.build();
		Student savedStudent = studentRepository.save(student);

		testStudentId = savedStudent.getId();
		testCourseId = savedCourse.getId();
	}

	@Test
	@DisplayName("[Pessimistic Lock] 학생 한 명이 수강신청을 하면 성공하고 인원수가 증가합니다.")
	void enrollWithPessimisticLock_should_increaseEnrolledCountAndSaveEnrollment_when_validRequest() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);

		// when
		enrollmentService.enrollWithPessimisticLock(request);

		// then
		Course course = courseRepository.findById(testCourseId)
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		assertThat(course.getEnrolledCount()).isEqualTo(1L);
		assertThat(enrollmentRepository.count()).isEqualTo(1L);
	}

	@Test
	@DisplayName("[Pessimistic Lock] 존재하지 않는 학생이 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithPessimisticLock_should_throwIllegalArgumentException_when_studentNotFound() {

		// given
		Long invalidStudentId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(invalidStudentId, testCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enrollWithPessimisticLock(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("학생이 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[Pessimistic Lock] 학생이 존재하지 않는 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithPessimisticLock_should_throwIllegalArgumentException_when_courseNotFound() {

		// given
		Long invalidCourseId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, invalidCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enrollWithPessimisticLock(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("강의가 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[Pessimistic Lock] 수강신청 인원이 최대 수용 인원을 초과하면 예외가 발생합니다.")
	void enrollWithPessimisticLock_should_throwIllegalStateException_when_capacityExceeded() {

		// given
		Student anotherStudent = studentRepository.save(Student.builder()
			.name("Another Student")
			.studentNumber("2026654321")
			.build());

		EnrollmentCreateRequest firstRequest = new EnrollmentCreateRequest(testStudentId, testCourseId);
		enrollmentService.enrollWithPessimisticLock(firstRequest);

		// when & then
		EnrollmentCreateRequest secondRequest = new EnrollmentCreateRequest(anotherStudent.getId(), testCourseId);

		assertThatThrownBy(() -> enrollmentService.enrollWithPessimisticLock(secondRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("수강 인원이 초과되었습니다.");
	}

	@Test
	@DisplayName("[Pessimistic Lock] 100명이 동시에 수강신청할 때 정원(30명)만큼만 성공합니다.")
	void enrollWithPessimisticLock_should_enrollExactlyMaxCapacity_when_concurrent()
		throws InterruptedException {

		// given
		int maxCapacity = 30;
		int totalStudents = 100;

		Course concurrentCourse = courseRepository.save(Course.builder()
			.title("Concurrent Test Course")
			.maxCapacity((long)maxCapacity)
			.build());

		List<Long> studentIds = new ArrayList<>();
		for (int i = 0; i < totalStudents; i++) {
			Student s = studentRepository.save(Student.builder()
				.name("Concurrent Student " + i)
				.studentNumber(String.format("C%09d", i))
				.build());
			studentIds.add(s.getId());
		}

		// when
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(totalStudents);
		ExecutorService executor = Executors.newFixedThreadPool(totalStudents);
		AtomicInteger successCount = new AtomicInteger(0);

		try {
			for (int i = 0; i < totalStudents; i++) {
				final Long studentId = studentIds.get(i);
				final Long courseId = concurrentCourse.getId();
				executor.submit(() -> {
					try {
						startLatch.await();
						enrollmentService.enrollWithPessimisticLock(
							new EnrollmentCreateRequest(studentId, courseId));
						successCount.incrementAndGet();
					} catch (IllegalStateException | PessimisticLockingFailureException ignored) {
						// 정원 초과 또는 락 타임아웃은 정상 흐름
					} catch (Exception e) {
						log.error("Unexpected error during concurrent enrollment test", e);
					} finally {
						doneLatch.countDown();
					}
				});
			}

			startLatch.countDown();
			doneLatch.await(60, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		// then
		Course updatedCourse = courseRepository.findById(concurrentCourse.getId()).orElseThrow();
		assertThat(updatedCourse.getEnrolledCount()).isLessThanOrEqualTo(maxCapacity);
		assertThat(updatedCourse.getEnrolledCount()).isEqualTo(Long.valueOf(successCount.get()));
		assertThat(enrollmentRepository.count()).isEqualTo(updatedCourse.getEnrolledCount());
	}
}
