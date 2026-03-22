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

	public static ErrorResponse of(HttpStatus status, String message, String details) {
		return new ErrorResponse(
			ZonedDateTime.now(ZoneId.of("Asia/Seoul")),
			status.value(),
			status.name(),
			message,
			details
		);
	}
}
