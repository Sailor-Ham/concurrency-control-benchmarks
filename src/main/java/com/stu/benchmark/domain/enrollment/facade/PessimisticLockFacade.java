package com.stu.benchmark.domain.enrollment.facade;

import org.springframework.stereotype.Component;

import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.service.EnrollmentService;
import com.stu.benchmark.global.benchmark.LockBenchmarkWrapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PessimisticLockFacade {

	private final EnrollmentService enrollmentService;

	private final LockBenchmarkWrapper benchmarkWrapper;

	/**
	 * [Case 1: Pessimistic Lock] 강의 엔터티에 대해 Pessimistic Lock을 적용하여 동시성 문제를 방지하는 수강신청.
	 *
	 * <p>비관적 락은 DB 트랜잭션 내부에서 자동으로 관리되므로, 애플리케이션 레벨에서 락 대기 시간(Wait Time)을
	 * Spin/Pub-Sub Lock처럼 정밀하게 분리하기 어렵습니다.
	 * 따라서 전체 실행 시간이 Service Time으로 측정되며, Wait Time은 0으로 기록됩니다.
	 */
	public void enrollWithPessimisticLock(EnrollmentCreateRequest request) {
		benchmarkWrapper.executeWithMetrics(
			"pessimistic",

			// [Wait Time 측정 구간] DB 레벨 비관적 락은 트랜잭션 내부에서 자동 처리되므로
			// 애플리케이션 레벨에서 분리 측정이 불가. Wait Time은 0으로 기록됨.
			() -> {},

			// [Service Time 측정 구간] 비관적 락 획득 + 비즈니스 로직 전체 수행 시간
			() -> enrollmentService.enrollWithPessimisticLock(request),

			// [Lock Releaser] 비관적 락은 트랜잭션 종료 시 자동 해제되므로 null
			null
		);
	}
}
