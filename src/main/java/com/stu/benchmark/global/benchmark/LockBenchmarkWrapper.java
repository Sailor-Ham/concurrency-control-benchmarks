package com.stu.benchmark.global.benchmark;

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

	public void executeWithMetrics(
		String lockType,
		Runnable lockAcquirer,
		Runnable businessLogic,
		Runnable lockReleaser
	) {

		long tArrival = System.nanoTime();    // 큐 도착 시간

		// 락 획득 시도 및 대기 시간(W) 측정
		lockAcquirer.run();
		long tServiceStart = System.nanoTime();

		// Micrometer 타이머에 Wait Time 기록
		Timer.builder("benchmark.lock.wait.time")
			.description("Lock Wait Time (W)")
			.tag("lock_type", lockType)
			.register(meterRegistry)
			.record(tServiceStart - tArrival, TimeUnit.NANOSECONDS);

		try {
			// 순수 비즈니스 로직 수행 (Service Time (S))
			businessLogic.run();
		} finally {
			long tComplete = System.nanoTime();

			// Micrometer 타이머에 Service Time 기록
			Timer.builder("benchmark.lock.service.time")
				.description("Lock Service Time (S)")
				.tag("lock_type", lockType)
				.register(meterRegistry)
				.record(tComplete - tServiceStart, TimeUnit.NANOSECONDS);

			// 락 해제
			if (lockReleaser != null) {
				lockReleaser.run();
			}
		}
	}
}
