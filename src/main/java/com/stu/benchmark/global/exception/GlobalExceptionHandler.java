package com.stu.benchmark.global.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.LockTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * @Valid 유효성 검증 실패 시 발생
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException e,
		HttpServletRequest request
	) {

		log.warn("Validation failed: {} - {}", request.getRequestURI(), e.getMessage());

		String details = e.getBindingResult().getFieldErrors().stream()
			.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
			.collect(Collectors.joining(", "));

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.BAD_REQUEST,
			"Validation failed",
			details
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 잘못된 요청 파라미터 전달 시 발생
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
		IllegalArgumentException e,
		HttpServletRequest request
	) {

		log.warn("Invalid request occurred: {} - {}", request.getRequestURI(), e.getMessage());

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.BAD_REQUEST,
			"Invalid request",
			"잘못된 요청 파라미터가 전달되었습니다: " + e.getMessage()
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 데이터베이스 제약 조건 위반 (예: 중복 수강신청) 시 발생
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
		DataIntegrityViolationException e,
		HttpServletRequest request
	) {

		log.warn("Data integrity violation: {} - {}", request.getRequestURI(), e.getMessage());

		String rootCauseMessage = e.getMostSpecificCause().getMessage();
		boolean isDuplicateEnrollment = rootCauseMessage != null
			&& rootCauseMessage.contains("uk_enrollment_student_id_course_id");

		String details = isDuplicateEnrollment
			? "이미 수강신청이 완료된 강의입니다."
			: "데이터 처리 중 제약 조건 위반이 발생했습니다.";

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.CONFLICT,
			"Business rule violation",
			details
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 비즈니스 로직 위반 시 발생
	 */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleIllegalStateException(
		IllegalStateException e,
		HttpServletRequest request
	) {

		log.warn("Business rule violation: {} - {}", request.getRequestURI(), e.getMessage());

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.CONFLICT,
			"Business rule violation",
			e.getMessage()
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 분산 락(Distributed Lock) 획득 대기 시간 초과 또는 인터럽트 발생 시
	 */
	@ExceptionHandler(LockAcquisitionException.class)
	public ResponseEntity<ErrorResponse> handleLockAcquisitionException(
		LockAcquisitionException e,
		HttpServletRequest request
	) {

		log.warn("Lock acquisition failed: {} - {}", request.getRequestURI(), e.getMessage());

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.SERVICE_UNAVAILABLE,
			"LOCK_TIMEOUT",
			"현재 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요."
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 데이터베이스 락(Lock) 경합 실패 및 타임아웃 시 발생
	 */
	@ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class})
	public ResponseEntity<ErrorResponse> handlePessimisticLockingFailureException(
		Exception e,
		HttpServletRequest request
	) {

		log.warn("Pessimistic lock failure: {} - {}", request.getRequestURI(), e.getMessage());

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.CONFLICT,
			"Resource is currently locked",
			"데이터베이스 락 경합으로 인해 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요."
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 낙관적 락(Optimistic Lock) 버전 충돌 시 발생
	 * 600 Vuser 환경에서는 이 에러가 대량으로 발생하여 '실패율' 지표의 핵심이 됩니다.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleObjectOptimisticLockingFailureException(
		ObjectOptimisticLockingFailureException e,
		HttpServletRequest request
	) {

		log.warn("Optimistic lock collision: {} - {}", request.getRequestURI(), e.getMessage());

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.CONFLICT,
			"OPTIMISTIC_LOCK_COLLISION",
			"요청하신 데이터가 다른 사용자에 의해 변경되었습니다. 다시 시도해주세요."
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}

	/**
	 * 기타 예상치 못한 서버 내부 오류 발생
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(
		Exception e,
		HttpServletRequest request
	) {

		log.error("Unexpected server error: {}", request.getRequestURI(), e);

		ErrorResponse response = ErrorResponse.of(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"An unexpected error occurred",
			"서버 내부 오류가 발생했습니다. 시스템 관리자에게 문의하세요."
		);

		return ResponseEntity
			.status(response.status())
			.body(response);
	}
}
