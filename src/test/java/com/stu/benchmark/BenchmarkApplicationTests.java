package com.stu.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.stu.benchmark.global.config.TestConfig;

@SpringBootTest
@Import(TestConfig.class)
class BenchmarkApplicationTests {

	@Test
	void contextLoads() {
	}

}
