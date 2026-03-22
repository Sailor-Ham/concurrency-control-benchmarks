package com.stu.benchmark.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

	private static final String TEST_REDIS_PASSWORD = "testRedisPass123!";

	@Bean
	@ServiceConnection(name = "redis")
	public GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
			.withExposedPorts(6379)
			.waitingFor(Wait.forListeningPort());
	}

	@Bean
	@ServiceConnection
	public MySQLContainer<?> mysqlContainer() {
		return new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("test_db")
			.withUsername("test_user")
			.withPassword("testPass123!");
	}
}
