package com.stu.benchmark.global.lock;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {

	private final StringRedisTemplate redisTemplate;

	/**
	 * 락 획득 시도 (SETNX 명령어 사용)
	 */
	public Boolean lock(Long key) {
		String lockKey = LockType.LETTUCE.generateKey(key);
		return redisTemplate
			.opsForValue()
			.setIfAbsent(lockKey, "lock", Duration.ofMillis(3000));
	}

	/**
	 * 락 해제 (DEL 명령어 사용)
	 */
	public Boolean unlock(Long key) {
		String lockKey = LockType.LETTUCE.generateKey(key);
		return redisTemplate.delete(lockKey);
	}
}
