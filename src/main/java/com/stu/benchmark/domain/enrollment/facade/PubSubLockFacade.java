package com.stu.benchmark.domain.enrollment.facade;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.service.EnrollmentService;
import com.stu.benchmark.global.exception.LockAcquisitionException;
import com.stu.benchmark.global.lock.LockType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubSubLockFacade {

	private final RedissonClient redissonClient;

	private final EnrollmentService enrollmentService;

	@Value("${redisson.lock.wait-time:5000}")
	private long waitTimeMillis;

	@Value("${redisson.lock.lease-time:3000}")
	private long leaseTimeMillis;

	/**
	 * [Case 3: Pub/Sub Lock] 강의 엔터티에 대해 Pub/Sub Lock을 적용하여 동시성 문제를 방지하는 수강신청
	 */
	public void enrollWithPubSubLock(EnrollmentCreateRequest request) {

		String lockKey = LockType.REDISSON.generateKey(request.courseId());
		RLock lock = redissonClient.getLock(lockKey);

		try {
			// waitTime: 락 획득을 시도하는 최대 대기 시간
			// leaseTime: 락이 자동으로 해제되는 시간
			boolean available = lock.tryLock(waitTimeMillis, leaseTimeMillis, TimeUnit.MILLISECONDS);

			if (!available) {
				log.error("[Pub/Sub Lock] 락 획득 타임아웃. courseId: {}, studentId: {}",
					request.courseId(), request.studentId());
				throw new LockAcquisitionException("락 획득 대기 시간 초과");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LockAcquisitionException("Pub/Sub Lock 대기 중 스레드 인터럽트 발생", e);
		}

		try {
			enrollmentService.enroll(request);
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
