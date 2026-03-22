package com.stu.benchmark.global.exception;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.LockTimeoutException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

class GlobalExceptionHandlerTest {

	// ISO 8601 타임스탬프 패턴 검증 (예: 2026-03-22T19:45:30.123+09:00)
	private static final String ISO_8601_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}$";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("응답의 timestamp가 ISO 8601 형식(SSSXXX)을 준수하는지 확인합니다.")
	void timestamp_format_check() throws Exception {
		mockMvc.perform(get("/test/400"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(jsonPath("$.timestamp").value(matchesRegex(ISO_8601_REGEX)));
	}

	@Test
	@DisplayName("IllegalArgumentException 발생 시 400 상태 코드와 에러 메시지를 반환합니다.")
	void handleIllegalArgumentException_should_return400() throws Exception {
		mockMvc.perform(get("/test/400"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.details").value("잘못된 요청 파라미터가 전달되었습니다: 잘못된 파라미터입니다."));
	}

	@Test
	@DisplayName("유효성 검증(@Valid) 실패 시 400 상태 코드와 필드별 에러 상세 내용을 반환합니다.")
	void handleMethodArgumentNotValidException_should_return400() throws Exception {
		String invalidJson = "{\"name\": \"\", \"age\": null}";

		mockMvc.perform(post("/test/valid")
				.contentType("application/json")
				.content(invalidJson))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.details").value(containsString("name: 이름은 필수입니다.")))
			.andExpect(jsonPath("$.details").value(containsString("age: 나이는 필수입니다.")));
	}

	@Test
	@DisplayName("IllegalStateException 발생 시 409 상태 코드와 에러 메시지를 반환합니다.")
	void handleIllegalStateException_should_return409() throws Exception {
		mockMvc.perform(get("/test/409"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("BUSINESS_RULE_VIOLATION"))
			.andExpect(jsonPath("$.details").value("이미 처리된 요청입니다."));
	}

	@Test
	@DisplayName("중복 수강신청(DataIntegrityViolation) 발생 시 특정 메시지가 포함된 409 응답을 반환합니다.")
	void handleDataIntegrityViolation_duplicateEnrollment() throws Exception {
		mockMvc.perform(get("/test/db/duplicate"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("DATA_INTEGRITY_VIOLATION"))
			.andExpect(jsonPath("$.details").value("이미 수강신청이 완료된 강의입니다."));
	}

	@Test
	@DisplayName("기타 DB 제약 조건 위반 발생 시 일반적인 에러 메시지를 반환합니다.")
	void handleDataIntegrityViolation_general() throws Exception {
		mockMvc.perform(get("/test/db/general"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("DATA_INTEGRITY_VIOLATION"))
			.andExpect(jsonPath("$.details").value("데이터 처리 중 제약 조건 위반이 발생했습니다."));
	}

	@Test
	@DisplayName("PessimisticLockingFailureException 발생 시 409 상태 코드와 락 충돌 메시지를 반환합니다.")
	void handlePessimisticLockingFailureException_should_return409() throws Exception {
		mockMvc.perform(get("/test/lock/pessimistic"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("PESSIMISTIC_LOCK_CONFLICT"));
	}

	@Test
	@DisplayName("LockTimeoutException 발생 시 409 상태 코드와 락 충돌 메시지를 반환합니다.")
	void handleLockTimeoutException_should_return409() throws Exception {
		mockMvc.perform(get("/test/lock/timeout"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("PESSIMISTIC_LOCK_CONFLICT"));
	}

	@Test
	@DisplayName("낙관적 락(Optimistic Lock) 충돌 시 409 상태 코드와 충돌 메시지를 반환합니다.")
	void handleOptimisticLockingFailure_should_return409() throws Exception {
		mockMvc.perform(get("/test/lock/optimistic"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("OPTIMISTIC_LOCK_COLLISION"))
			.andExpect(jsonPath("$.details").value("요청하신 데이터가 다른 사용자에 의해 변경되었습니다. 다시 시도해주세요."));
	}

	@Test
	@DisplayName("분산 락 획득 실패(LockAcquisitionException) 시 503 상태 코드와 메시지를 반환합니다.")
	void handleLockAcquisitionException_should_return503() throws Exception {
		mockMvc.perform(get("/test/lock/acquisition"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("LOCK_TIMEOUT"));
	}

	@Test
	@DisplayName("일반 Exception 발생 시 500 상태 코드와 정적 에러 메시지를 반환합니다.")
	void handleException_should_return500() throws Exception {
		mockMvc.perform(get("/test/500"))
			// .andDo(print())    // 실제 응답 내용을 콘솔에 출력하여 디버깅에 도움을 줍니다.
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.message").value("INTERNAL_SERVER_ERROR"));
	}

	/**
	 * 예외 핸들러 테스트를 위한 테스트 컨트롤러
	 */
	@RestController
	@RequestMapping("/test")
	static class TestController {

		@GetMapping("/400")
		public String throwIllegalArgumentException() {
			throw new IllegalArgumentException("잘못된 파라미터입니다.");
		}

		@GetMapping("/409")
		public String throwIllegalStateException() {
			throw new IllegalStateException("이미 처리된 요청입니다.");
		}

		@GetMapping("/db/duplicate")
		public String throwDuplicateKeyException() {
			// 중복 수강신청 제약조건 예외 모킹
			throw new DataIntegrityViolationException(
				"Conflict",
				new RuntimeException("Duplicate entry '1-10' for key 'uk_enrollment_student_id_course_id'")
			);
		}

		@GetMapping("/db/general")
		public String throwGeneralDataException() {
			// 일반적인 DB 제약조건 위반 예외 모킹
			throw new DataIntegrityViolationException(
				"General DB error",
				new RuntimeException("Some other constraint failed")
			);
		}

		@GetMapping("/lock/pessimistic")
		public String throwPessimisticLockingFailureException() {
			throw new PessimisticLockingFailureException("비관적 락 획득 실패");
		}

		@GetMapping("/lock/timeout")
		public String throwLockTimeoutException() {
			throw new LockTimeoutException("락 획득 시간 초과");
		}

		@GetMapping("/lock/optimistic")
		public String throwOptimisticLock() {
			throw new ObjectOptimisticLockingFailureException(Object.class, "id");
		}

		@GetMapping("/lock/acquisition")
		public String throwLockAcquisitionException() {
			throw new LockAcquisitionException("Redis lock timeout");
		}

		@GetMapping("/500")
		public String throwException() throws Exception {
			throw new Exception("DB 연결 실패 등 노출되면 안 되는 서버 내부 오류");
		}

		@PostMapping("/valid")
		public String throwMethodArgumentNotValidException(@Valid @RequestBody TestRequest request) {
			return "유효성 검증 통과";
		}

		record TestRequest(
			@NotBlank(message = "이름은 필수입니다.")
			String name,

			@NotNull(message = "나이는 필수입니다.")
			Integer age
		) {
		}
	}
}
