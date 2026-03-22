package com.stu.benchmark.global.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LockType {

	LETTUCE("lock:lettue:course:"),
	;

	private final String prefix;

	public String generateKey(Long id) {
		return this.prefix + id;
	}
}
