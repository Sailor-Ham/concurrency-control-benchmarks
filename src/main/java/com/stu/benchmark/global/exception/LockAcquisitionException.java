package com.stu.benchmark.global.exception;

public class LockAcquisitionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LockAcquisitionException(String message) {
		super(message);
	}

	public LockAcquisitionException(String message, Throwable cause) {
		super(message, cause);
	}
}
