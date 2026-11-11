package com.ucms.identity.web.dto;

import java.util.UUID;

/** One granted (module, action) at one scope, after overrides — what the UI gates on. */
public record EffectivePermission(String scopeType, UUID scopeId, String module, String actionKey) {}
