package com.stu.benchmark.global.init;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stu.benchmark.domain.course.entity.Course;
import com.stu.benchmark.domain.course.repository.CourseRepository;
import com.stu.benchmark.domain.student.entity.Student;
import com.stu.benchmark.domain.student.repository.StudentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("bench")
@RequiredArgsConstructor
public class BenchmarkDataInitializer implements CommandLineRunner {

	private static final String INIT_LOCK_KEY = "lock:init:benchmark-data";

	private final CourseRepository courseRepository;
	private final StudentRepository studentRepository;

	private final RedissonClient redissonClient;

	@Override
	@Transactional
	public void run(String... args) {

		RLock lock = redissonClient.getLock(INIT_LOCK_KEY);

		try {
			boolean isLocked = lock.tryLock(0, 60, TimeUnit.SECONDS);

			if (!isLocked) {
				log.info(">>> [DataInitializer] 다른 인스턴스(WAS)에서 데이터 초기화를 진행 중입니다. 현재 인스턴스는 스킵합니다.");
				return;
			}

			initializeData();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error(">>> [DataInitializer] 데이터 초기화 락 획득 중 인터럽트 발생", e);
		} finally {
			if (lock.isLocked() && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private void initializeData() {

		int currentYear = LocalDate.now().getYear();

		// 테스트 강의 생성
		if (courseRepository.count() == 0) {

			Course savedCourse = courseRepository.save(
				Course.builder()
					.title("Concurrency Control")
					.maxCapacity(100L)
					.build()
			);

			log.info(">>> [DataInitializer] '{}' 강의가 정원 {}명으로 생성되었습니다.",
				savedCourse.getTitle(), savedCourse.getMaxCapacity());
		}

		// 테스트 학생 생성
		if (studentRepository.count() == 0) {
			List<Student> students = IntStream.rangeClosed(1, 1200)
				.mapToObj(i -> {
					String studentNumber = String.format("%d12%04d", currentYear, i);
					return Student.builder()
						.studentNumber(studentNumber)
						.name("Student " + i)
						.build();
				})
				.toList();

			studentRepository.saveAll(students);

			log.info(">>> [DataInitializer] {}명의 학생이 생성되었습니다.", students.size());
		}
	}
}
