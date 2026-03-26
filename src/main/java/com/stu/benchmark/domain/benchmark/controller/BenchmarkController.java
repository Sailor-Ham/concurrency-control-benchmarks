package com.stu.benchmark.domain.benchmark.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stu.benchmark.domain.benchmark.dto.BenchmarkResetResponse;
import com.stu.benchmark.domain.benchmark.service.BenchmarkResetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Profile("bench")
@RequiredArgsConstructor
@RequestMapping("/v1/benchmarks")
public class BenchmarkController {

	private final BenchmarkResetService benchmarkResetService;

	/**
	 * 벤치마크 테스트 환경 초기화
	 */
	@PostMapping("/reset")
	public ResponseEntity<BenchmarkResetResponse> resetBenchmark() {

		log.info(">>> [Benchmark API] 테스트 환경 초기화 요청 수신");

		BenchmarkResetResponse response = benchmarkResetService.resetBenchmark();

		return ResponseEntity.ok(response);
	}
}
