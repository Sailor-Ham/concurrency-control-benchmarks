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
class EnrollmentServiceNoLockTest {

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
	@DisplayName("[NoLock] 학생 한 명이 수강신청을 하면 성공하고 인원수가 증가합니다.")
	void enrollWithNoLock_should_increaseEnrolledCountAndSaveEnrollment_when_validRequest() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);

		// when
		enrollmentService.enroll(request);

		// then
		Course course = courseRepository.findById(testCourseId)
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		assertThat(course.getEnrolledCount()).isEqualTo(1L);
		assertThat(enrollmentRepository.count()).isEqualTo(1L);
	}

	@Test
	@DisplayName("[NoLock] 존재하지 않는 학생이 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalArgumentException_when_studentNotFound() {

		// given
		Long invalidStudentId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(invalidStudentId, testCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("학생이 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[NoLock] 학생이 존재하지 않는 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalArgumentException_when_courseNotFound() {

		// given
		Long invalidCourseId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, invalidCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("강의가 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[NoLock] 같은 학생이 동일 강의에 중복 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalStateException_when_duplicateEnrollment() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);
		enrollmentService.enroll(request);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("해당 강의는 이미 수강신청되었습니다.");
	}

	@Test
	@DisplayName("[NoLock] 수강신청 인원이 최대 수용 인원을 초과하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalStateException_when_capacityExceeded() {

		// given
		Student anotherStudent = studentRepository.save(Student.builder()
			.name("Another Student")
			.studentNumber("2026654321")
			.build());

		EnrollmentCreateRequest firstRequest = new EnrollmentCreateRequest(testStudentId, testCourseId);
		enrollmentService.enroll(firstRequest);

		// when & then
		EnrollmentCreateRequest secondRequest = new EnrollmentCreateRequest(anotherStudent.getId(), testCourseId);

		assertThatThrownBy(() -> enrollmentService.enroll(secondRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("수강 인원이 초과되었습니다.");
	}

	@Test
	@DisplayName("[NoLock] 100명이 동시에 수강신청을 하면 레이스 컨디션이 발생하여 정원(30명)을 초과해 신청됩니다.")
	void enrollWithNoLock_should_exceedMaxCapacity_when_concurrent() throws InterruptedException {

		// given
		int maxCapacity = 30;
		int totalStudents = 100;

		Course concurrentCourse = courseRepository.save(Course.builder()
			.title("Concurrent No-Lock Test Course")
			.maxCapacity((long)maxCapacity)
			.build());

		List<Long> studentIds = new ArrayList<>();
		for (int i = 0; i < totalStudents; i++) {
			Student s = studentRepository.save(Student.builder()
				.name("Concurrent Student " + i)
				.studentNumber(String.format("N%09d", i))
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
						enrollmentService.enroll(new EnrollmentCreateRequest(studentId, courseId));
						successCount.incrementAndGet();
					} catch (IllegalStateException ignored) {
						// 늦게 진입한 스레드가 정원 초과 예외를 만나는 것은 정상 흐름
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
		long actualEnrollmentCount = enrollmentRepository.count();
		Course updatedCourse = courseRepository.findById(concurrentCourse.getId()).orElseThrow();
		assertThat(actualEnrollmentCount).isGreaterThan(maxCapacity);
		assertThat(actualEnrollmentCount).isGreaterThan(updatedCourse.getEnrolledCount());
	}
}
