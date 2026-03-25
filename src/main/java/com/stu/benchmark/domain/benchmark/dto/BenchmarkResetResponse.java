package com.stu.benchmark.domain.benchmark.dto;

public record BenchmarkResetResponse(
	Long enrolledCount,
	Long enrollmentCount
) {
}
