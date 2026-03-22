package com.stu.benchmark.global.lock;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {

	private static final String UNLOCK_LUA_SCRIPT = """
		if redis.call('get', KEYS[1]) == ARGV[1] then
			return redis.call('del', KEYS[1])
		else
			return 0
		end
		""";

	private final StringRedisTemplate redisTemplate;

	/**
	 * 락 획득 시도 (SETNX 명령어 사용)
	 */
	public Boolean lock(Long key, String value) {
		String lockKey = LockType.LETTUCE.generateKey(key);
		return redisTemplate
			.opsForValue()
			.setIfAbsent(lockKey, value, Duration.ofMillis(3000));
	}

	/**
	 * 락 해제 (Lua 스크립트를 통한 원자적 해제)
	 */
	public Boolean unlock(Long key, String value) {

		String lockKey = LockType.LETTUCE.generateKey(key);

		DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(UNLOCK_LUA_SCRIPT);
		redisScript.setResultType(Long.class);

		// Lua 스크립트 실행: KEYS[1] = lockKey, ARGV[1] = value
		Long result = redisTemplate.execute(
			redisScript,
			Collections.singletonList(lockKey),
			value
		);

		// 삭제 성공 시 1 반환, 내 락이 아니거나 없으면 0 반환
		return result > 0L;
	}
}
