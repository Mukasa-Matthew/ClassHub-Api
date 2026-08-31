package com.classhub.common.api;

public record Pagination(int page, int size, long totalElements, int totalPages) {
}
