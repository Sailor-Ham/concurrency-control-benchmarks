package com.stu.benchmark.global.exception;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ErrorResponse(
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	ZonedDateTime timestamp,
	int status,
	String error,
	String message,
	String details
) {

	private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

	public static ErrorResponse of(HttpStatus status, String message, String details) {
		return new ErrorResponse(
			ZonedDateTime.now(ZONE_SEOUL),
			status.value(),
			status.name(),
			message,
			details
		);
	}
}
