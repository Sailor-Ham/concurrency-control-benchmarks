package com.stu.benchmark.domain.enrollment.facade;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.service.EnrollmentService;
import com.stu.benchmark.global.benchmark.LockBenchmarkWrapper;
import com.stu.benchmark.global.exception.LockAcquisitionException;
import com.stu.benchmark.global.lock.RedisLockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpinLockFacade {

	private final RedisLockRepository redisLockRepository;

	private final EnrollmentService enrollmentService;

	private final LockBenchmarkWrapper benchmarkWrapper;

	/**
	 * [Case 2: Spin Lock] 강의 엔터티에 대해 Spin Lock을 적용하여 동시성 문제를 방지하는 수강신청
	 */
	public void enrollWithSpinLock(EnrollmentCreateRequest request) {

		// 락 소유권을 증명할 현재 요청만의 고유 식별자(UUID) 생성
		String lockValue = UUID.randomUUID().toString();

		// 래퍼를 통한 측정 시작
		benchmarkWrapper.executeWithMetrics(
			"spin",    // lockType 라벨

			// [Wait Time 측정 구간] Redis에 락을 요청하고, 실패하면 sleep 후 재시도하는 전체 대기 시간
			() -> {
				// 락 획득을 기다릴 최대 시간 (3초로 설정, 필요 시 조정)
				long timeoutMillis = 3000;
				long startTime = System.currentTimeMillis();

				// Spin Lock: 락을 얻을 때까지 sleep하면서 while 문 계속 시도
				while (!redisLockRepository.lock(request.courseId(), lockValue)) {

					// 타임아웃 검사: 3초 이상 락을 얻지 못했으면 예외 발생
					if (System.currentTimeMillis() - startTime > timeoutMillis) {
						log.error("[Spin Lock] 락 획득 타임아웃. courseId: {}, studentId: {}",
							request.courseId(), request.studentId());
						throw new LockAcquisitionException("락 획득 대기 시간 초과");
					}

					// 락 획득 실패 시 Redis에 너무 많은 부하를 주지 않도록 10ms 대기 후 재시도
					// TODO: 10, 30, 50ms 조정하며 성능 평가 해보기
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new LockAcquisitionException("Spin Lock 대기 중 스레드 인터럽트 발생", e);
					}
				}
			},

			// [Service Time 측정 구간] 락 획득 후 순수 비즈니스 로직 처리 시간
			() -> enrollmentService.enroll(request),

			// [Lock Releaser] 비즈니스 로직 수행(또는 예외 발생) 후 무조건 실행될 락 해제 로직
			() -> redisLockRepository.unlock(request.courseId(), lockValue)
		);
	}
}
