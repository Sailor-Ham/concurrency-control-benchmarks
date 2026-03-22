package com.stu.benchmark.domain.enrollment.facade;

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
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;
import com.stu.benchmark.global.config.TestConfig;
import com.stu.benchmark.global.exception.LockAcquisitionException;
import com.stu.benchmark.global.lock.LockType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@Import(TestConfig.class)
class SpinLockFacadeTest {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private SpinLockFacade spinLockFacade;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private Long testStudentId;
	private Long testCourseId;

	@BeforeEach
	void setUp() {

		// 기존 데이터 초기화
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
	@DisplayName("[Spin Lock] 학생 한 명이 수강신청을 하면 성공하고 인원수가 증가합니다.")
	void enrollWithSpinLock_should_increaseEnrolledCountAndSaveEnrollment_when_validRequest() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);

		// when
		spinLockFacade.enrollWithSpinLock(request);

		// then
		Course course = courseRepository.findById(testCourseId)
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		assertThat(course.getEnrolledCount()).isEqualTo(1L);
		assertThat(enrollmentRepository.count()).isEqualTo(1L);

		String lockKey = LockType.LETTUCE.generateKey(testCourseId);
		assertThat(redisTemplate.hasKey(lockKey)).isFalse();
	}

	@Test
	@DisplayName("[Spin Lock] 100명이 동시에 수강신청할 때 정원(30명)만큼만 성공합니다.")
	void enrollWithSpinLock_should_enrollExactlyMaxCapacity_when_concurrent() throws InterruptedException {

		// given
		int maxCapacity = 30;
		int totalStudent = 100;

		Course concurrentCourse = courseRepository.save(Course.builder()
			.title("Concurrent Test Course")
			.maxCapacity((long)maxCapacity)
			.build());

		List<Long> studentIds = new ArrayList<>();
		for (int i = 0; i < totalStudent; i++) {
			Student student = studentRepository.save(Student.builder()
				.name("Concurrent Student " + i)
				.studentNumber(String.format("S%09d", i))
				.build());

			studentIds.add(student.getId());
		}

		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(totalStudent);
		ExecutorService executor = Executors.newFixedThreadPool(totalStudent);
		AtomicInteger successCount = new AtomicInteger(0);

		// when
		for (int i = 0; i < totalStudent; i++) {

			final Long studentId = studentIds.get(i);
			final Long courseId = concurrentCourse.getId();

			executor.submit(() -> {
				try {
					startLatch.await();
					spinLockFacade.enrollWithSpinLock(new EnrollmentCreateRequest(studentId, courseId));
					successCount.incrementAndGet();
				} catch (IllegalStateException e) {
					// 정원	초과 또는 락 획득 실패로 인한 예외는 정상 흐름이므로 무시
				} catch (LockAcquisitionException e) {
					// 락 타임아웃 예외 (Spin Lock 대기 시간 초과) 정상 흐름
					log.info("Lock Timeout: {}", e.getMessage());
				} catch (Exception e) {
					log.error("Unexpected error", e);
				} finally {
					doneLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		doneLatch.await(30, TimeUnit.SECONDS);
		executor.shutdown();

		// then
		Course updatedCourse = courseRepository.findById(concurrentCourse.getId())
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		assertThat(successCount.get()).isEqualTo(maxCapacity);
		assertThat(updatedCourse.getEnrolledCount()).isEqualTo(maxCapacity);
		assertThat(enrollmentRepository.count()).isEqualTo(maxCapacity);

		String lockKey = LockType.LETTUCE.generateKey(concurrentCourse.getId());
		assertThat(redisTemplate.hasKey(lockKey)).isFalse();
	}
}
