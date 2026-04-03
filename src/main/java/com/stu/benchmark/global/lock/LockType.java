package com.stu.benchmark.global.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LockType {

	LETTUCE("lock:lettuce:course:"),
	REDISSON("lock:redisson:course:"),

	ZOOKEEPER("/locks/zookeeper/course/"),
	;

	private final String prefix;

	public String generateKey(Long id) {
		return this.prefix + id;
	}
}
