package com.ucms.identity.web.dto;

public record LoginResponse(String token, String role, String name) {}
