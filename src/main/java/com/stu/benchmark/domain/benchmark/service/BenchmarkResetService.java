package com.stu.benchmark.domain.benchmark.service;

import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stu.benchmark.domain.benchmark.dto.BenchmarkResetResponse;
import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.global.lock.LockType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkResetService {

	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;

	private final RedissonClient redissonClient;

	@Transactional
	public BenchmarkResetResponse resetBenchmark() {

		long currentEnrollmentCount = enrollmentRepository.count();
		long currentEnrolledCount = courseRepository.findAll().stream()
			.mapToLong(Course::getEnrolledCount)
			.sum();

		// 데이터 초기화
		enrollmentRepository.deleteAllInBatch();
		courseRepository.resetAllEnrolledCounts();

		// Redis 잔여 락 강제 삭제
		RKeys keys = redissonClient.getKeys();
		long totalDeletedLocks = 0;
		for (LockType lockType : LockType.values()) {
			totalDeletedLocks += keys.deleteByPattern(lockType.getPrefix() + "*");
		}

		log.info("[Benchmark Reset] 수강 인원 0으로 변경 완료");
		log.info("[Benchmark Reset] 수강 이력 제거 완료 ({}건)", currentEnrollmentCount);
		log.info("[Benchmark Reset] Redis 락 제거 완료 ({}개)", totalDeletedLocks);

		return new BenchmarkResetResponse(currentEnrolledCount, currentEnrollmentCount);
	}
}
