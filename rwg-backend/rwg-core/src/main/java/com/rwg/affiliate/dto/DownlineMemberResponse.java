package com.rwg.affiliate.dto;

import java.time.Instant;
import java.util.UUID;

/** Một thành viên tuyến dưới của đại lý. */
public record DownlineMemberResponse(
        UUID userId,
        String username,
        int level,
        Instant joinedAt
) {
}
