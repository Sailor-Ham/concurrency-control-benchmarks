package com.stu.benchmark.global.config;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZookeeperConfig {

	@Value("${zookeeper.host}")
	private String host;

	@Value("${zookeeper.port}")
	private int port;

	@Value("${zookeeper.session-timeout}")
	private int sessionTimeout;

	@Value("${zookeeper.connection-timeout}")
	private int connectionTimeout;

	@Value("${zookeeper.retry.base-sleep-time}")
	private int baseSleepTime;

	@Value("${zookeeper.retry.max-retries}")
	private int maxRetries;

	@Bean(initMethod = "start", destroyMethod = "close")
	public CuratorFramework curatorFramework() {

		RetryPolicy retryPolicy = new ExponentialBackoffRetry(baseSleepTime, maxRetries);

		return CuratorFrameworkFactory.builder()
			.connectString(host + ":" + port)
			.sessionTimeoutMs(sessionTimeout)
			.connectionTimeoutMs(connectionTimeout)
			.retryPolicy(retryPolicy)
			.build();
	}
}
