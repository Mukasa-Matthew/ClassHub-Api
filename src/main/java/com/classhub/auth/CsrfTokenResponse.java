package com.classhub.auth;

public record CsrfTokenResponse(String token, String headerName) {}
