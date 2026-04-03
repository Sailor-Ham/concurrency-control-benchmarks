package com.stu.benchmark.domain.enrollment.facade;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.service.EnrollmentService;
import com.stu.benchmark.global.benchmark.LockBenchmarkWrapper;
import com.stu.benchmark.global.exception.LockAcquisitionException;
import com.stu.benchmark.global.lock.LockType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZookeeperLockFacade {

	private final CuratorFramework curatorFramework;

	private final EnrollmentService enrollmentService;

	private final LockBenchmarkWrapper benchmarkWrapper;

	@Value("${zookeeper.lock.wait-time}")
	private long waitTimeMillis;

	/**
	 * [Case 4: Zookeeper Lock] 강의 엔터티에 대해 Zookeeper Lock을 적용하여 동시성 문제를 방지하는 수강신청
	 */
	public void enrollWithZookeeperLock(EnrollmentCreateRequest request) {

		String lockPath = LockType.ZOOKEEPER.generateKey(request.courseId());
		InterProcessMutex mutex = new InterProcessMutex(curatorFramework, lockPath);

		benchmarkWrapper.executeWithMetrics(
			"zookeeper",    // lockType 라벨

			// [Wait Time 측정 구간] 자신의 순번 앞 노드에 Watcher를 걸고 락 획득을 대기하는 시간
			() -> {
				boolean available;

				try {
					// waitTime: 앞 순번의 노드가 삭제될 때까지(락 획득을 위해) 대기하는 최대 시간
					available = mutex.acquire(waitTimeMillis, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
					if (e instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					throw new RuntimeException("Zookeeper Lock 대기 중 오류 발생", e);
				}

				if (!available) {
					log.error("[Zookeeper Lock] 락 획득 타임아웃. courseId: {}, studentId: {}",
						request.courseId(), request.studentId());
					throw new LockAcquisitionException("락 획득 대기 시간 초과");
				}
			},

			// [Service Time 측정 구간] 락 획득 후 순수 비즈니스 로직 처리 시간
			() -> enrollmentService.enroll(request),

			// [Lock Releaser] 락을 점유한 스레드인지 확인 후 안전하게 해제
			() -> {
				if (mutex.isAcquiredInThisProcess()) {
					try {
						mutex.release();
					} catch (Exception e) {
						// 락 해제 실패는 다음 대기자에게 치명적이므로(물론 세션 타임아웃으로 결국 풀리긴 하지만) 명시적 에러 로깅
						log.error("[Zookeeper Lock] 락 해제 중 오류 발생. courseId: {}", request.courseId(), e);
					}
				}
			}
		);
	}
}
