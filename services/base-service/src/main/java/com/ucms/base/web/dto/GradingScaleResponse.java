package com.ucms.base.web.dto;

import com.ucms.base.domain.GradingScale;
import java.util.UUID;

public record GradingScaleResponse(UUID id, String name, String bands, String status) {
    public static GradingScaleResponse from(GradingScale g) {
        return new GradingScaleResponse(g.getId(), g.getName(), g.getBands(), g.getStatus().name());
    }
}
