package com.stu.benchmark.domain.enrollment.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.enrollment.dto.EnrollmentCreateRequest;
import com.stu.benchmark.domain.enrollment.repository.EnrollmentRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;
import com.stu.benchmark.global.config.TestConfig;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EnrollmentServiceNoLockTest {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private EnrollmentService enrollmentService;

	private Long testStudentId;
	private Long testCourseId;

	@BeforeEach
	void setUp() {

		// 기존 데이터 초기화 (FK 제약: Enrollment → Student/Course 순서로 삭제)
		enrollmentRepository.deleteAllInBatch();
		studentRepository.deleteAllInBatch();
		courseRepository.deleteAllInBatch();

		// 테스트 데이터 삽입
		Course course = Course.builder()
			.title("Test Course")
			.maxCapacity(1L)
			.build();
		Course savedCourse = courseRepository.save(course);

		Student student = Student.builder()
			.name("Test Student")
			.studentNumber("2026123456")
			.build();
		Student savedStudent = studentRepository.save(student);

		testStudentId = savedStudent.getId();
		testCourseId = savedCourse.getId();
	}

	@Test
	@DisplayName("[NoLock] 학생 한 명이 수강신청을 하면 성공하고 인원수가 증가합니다.")
	void enrollWithNoLock_should_increaseEnrolledCountAndSaveEnrollment_when_validRequest() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);

		// when
		enrollmentService.enroll(request);

		// then
		Course course = courseRepository.findById(testCourseId)
			.orElseThrow(() -> new IllegalArgumentException("강의가 존재하지 않습니다."));

		assertThat(course.getEnrolledCount()).isEqualTo(1L);
		assertThat(enrollmentRepository.count()).isEqualTo(1L);
	}

	@Test
	@DisplayName("[NoLock] 존재하지 않는 학생이 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalArgumentException_when_studentNotFound() {

		// given
		Long invalidStudentId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(invalidStudentId, testCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("학생이 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[NoLock] 학생이 존재하지 않는 강의에 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalArgumentException_when_courseNotFound() {

		// given
		Long invalidCourseId = 999L;
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, invalidCourseId);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("강의가 존재하지 않습니다.");
	}

	@Test
	@DisplayName("[NoLock] 같은 학생이 동일 강의에 중복 수강신청을 하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalStateException_when_duplicateEnrollment() {

		// given
		EnrollmentCreateRequest request = new EnrollmentCreateRequest(testStudentId, testCourseId);
		enrollmentService.enroll(request);

		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("해당 강의는 이미 수강신청되었습니다.");
	}

	@Test
	@DisplayName("[NoLock] 수강신청 인원이 최대 수용 인원을 초과하면 예외가 발생합니다.")
	void enrollWithNoLock_should_throwIllegalStateException_when_capacityExceeded() {

		// given
		Student anotherStudent = studentRepository.save(Student.builder()
			.name("Another Student")
			.studentNumber("2026654321")
			.build());

		EnrollmentCreateRequest firstRequest = new EnrollmentCreateRequest(testStudentId, testCourseId);
		enrollmentService.enroll(firstRequest);

		// when & then
		EnrollmentCreateRequest secondRequest = new EnrollmentCreateRequest(anotherStudent.getId(), testCourseId);

		assertThatThrownBy(() -> enrollmentService.enroll(secondRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("수강 인원이 초과되었습니다.");
	}
}
