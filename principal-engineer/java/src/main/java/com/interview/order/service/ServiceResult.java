package com.interview.order.service;

public class ServiceResult<T> {
    private final int status;
    private final T value;
    private final String error;

    private ServiceResult(int status, T value, String error) {
        this.status = status;
        this.value = value;
        this.error = error;
    }

    public static <T> ServiceResult<T> ok(T value) {
        return new ServiceResult<>(200, value, null);
    }

    public static <T> ServiceResult<T> created(T value) {
        return new ServiceResult<>(201, value, null);
    }

    public static <T> ServiceResult<T> badRequest(String error) {
        return new ServiceResult<>(400, null, error);
    }

    public static <T> ServiceResult<T> notFound(String error) {
        return new ServiceResult<>(404, null, error);
    }

    public static <T> ServiceResult<T> conflict(String error) {
        return new ServiceResult<>(409, null, error);
    }

    public int getStatus() {
        return status;
    }

    public T getValue() {
        return value;
    }

    public String getError() {
        return error;
    }
}
