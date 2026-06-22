package com.kkpp.admin.adminauth.dto;

import java.util.UUID;

public record AdminUserResponse(
        UUID publicId,
        String email,
        String name,
        String role
) {
}
