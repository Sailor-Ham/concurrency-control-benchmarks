package com.stu.benchmark.global.benchmark;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockBenchmarkWrapper {

	private final MeterRegistry meterRegistry;

	// Timer 인스턴스를 캐싱하여 매 요청마다 빌더 객체가 생성되는 오버헤드를 방지
	private final ConcurrentHashMap<String, Timer> waitTimers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Timer> serviceTimers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Timer> totalTimers = new ConcurrentHashMap<>();

	public void executeWithMetrics(
		String lockType,
		Runnable lockAcquirer,
		Runnable businessLogic,
		Runnable lockReleaser
	) {

		long tArrival = System.nanoTime();    // 큐 도착 시간
		// tArrival로 초기화: lockAcquirer 실패 시에도 finally 블록에서 항상 재할당되어 wait time이 기록됨
		long tServiceStart = tArrival;

		// 락 획득 시도 및 대기 시간(W) 측정
		// finally로 감싸 타임아웃/인터럽트 등 실패 케이스에서도 wait time을 기록
		try {
			lockAcquirer.run();
		} finally {
			tServiceStart = System.nanoTime();
			getWaitTimer(lockType).record(tServiceStart - tArrival, TimeUnit.NANOSECONDS);
		}

		// lockAcquirer에서 예외가 발생하면 여기까지 오지 않으므로 비즈니스 로직은 실행되지 않음
		try {
			// 순수 비즈니스 로직 수행 (Service Time (S))
			businessLogic.run();
		} finally {
			long tComplete = System.nanoTime();
			getServiceTimer(lockType).record(tComplete - tServiceStart, TimeUnit.NANOSECONDS);
			getTotalTimer(lockType).record(tComplete - tArrival, TimeUnit.NANOSECONDS);

			// 락 해제 중 예외가 발생해도 비즈니스 로직 결과에 영향을 주지 않도록 처리
			if (lockReleaser != null) {
				try {
					lockReleaser.run();
				} catch (Exception e) {
					log.error("[{}] 락 해제 중 오류 발생", lockType, e);
				}
			}
		}
	}

	private Timer getWaitTimer(String lockType) {
		return waitTimers.computeIfAbsent(lockType, type ->
			Timer.builder("benchmark.lock.wait.time")
				.description("Lock Wait Time (W)")
				.tag("lock_type", type)
				.publishPercentileHistogram()
				.register(meterRegistry)
		);
	}

	private Timer getServiceTimer(String lockType) {
		return serviceTimers.computeIfAbsent(lockType, type ->
			Timer.builder("benchmark.lock.service.time")
				.description("Lock Service Time (S)")
				.tag("lock_type", type)
				.publishPercentileHistogram()
				.register(meterRegistry)
		);
	}

	private Timer getTotalTimer(String lockType) {
		return totalTimers.computeIfAbsent(lockType, type ->
			Timer.builder("benchmark.lock.total.time")
				.description("Total Response Time (W + S)")
				.tag("lock_type", type)
				.publishPercentileHistogram()
				.register(meterRegistry)
		);
	}
}
