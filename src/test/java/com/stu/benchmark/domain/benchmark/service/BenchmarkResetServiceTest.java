package com.stu.benchmark.domain.benchmark.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.stu.benchmark.domain.benchmark.dto.BenchmarkResetResponse;
import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.entity.Enrollment;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;
import com.stu.benchmark.global.config.TestConfig;
import com.stu.benchmark.global.lock.LockType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BenchmarkResetServiceTest {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private BenchmarkResetService benchmarkResetService;

	@BeforeEach
	void setUp() {
		// 기존 데이터 및 Redis 완전 초기화
		enrollmentRepository.deleteAllInBatch();
		studentRepository.deleteAllInBatch();
		courseRepository.deleteAllInBatch();
		redissonClient.getKeys().flushdb();
	}

	@Test
	@DisplayName("[Benchmark] 벤치마크 초기화를 수행하면 초기화 직전의 상태를 반환하고, DB 수강 이력과 Redis 잔여 락을 모두 초기화합니다.")
	void resetBenchmark_should_returnPreviousStateAndClearData_when_dataExists() {

		// given
		Course course = Course.builder()
			.title("Benchmark Test Course")
			.maxCapacity(100L)
			.build();

		// 강의 수강 인원 증가 (3명)
		course.increaseEnrolledCount();
		course.increaseEnrolledCount();
		course.increaseEnrolledCount();

		Course savedCourse = courseRepository.save(course);
		long courseId = savedCourse.getId();

		// 테스트용 학생 및 수강 이력 3건 생성
		for (int i = 1; i <= 3; i++) {

			Student student = studentRepository.save(
				Student.builder()
					.studentNumber("202612340" + i)
					.name("Benchmark Student " + i)
					.build()
			);

			enrollmentRepository.save(
				Enrollment.builder()
					.studentId(student.getId())
					.courseId(courseId)
					.build()
			);
		}

		String redissonLockKey = LockType.REDISSON.getPrefix() + courseId;
		String lettuceLockKey = LockType.LETTUCE.getPrefix() + courseId;

		redissonClient.getBucket(redissonLockKey).set("dummy-lock-data");
		redissonClient.getBucket(lettuceLockKey).set("dummy-lock-data");

		// when
		BenchmarkResetResponse response = benchmarkResetService.resetBenchmark();

		// then
		assertThat(response.enrolledCount()).isEqualTo(3L);
		assertThat(response.enrollmentCount()).isEqualTo(3L);

		assertThat(enrollmentRepository.count()).isZero();

		Course resetCourse = courseRepository.findById(courseId)
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));
		assertThat(resetCourse.getEnrolledCount()).isZero();

		RKeys keys = redissonClient.getKeys();
		assertThat(keys.countExists(redissonLockKey)).isZero();
		assertThat(keys.countExists(lettuceLockKey)).isZero();
	}
}
