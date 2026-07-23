package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private String phone;

    /** USER / MERCHANT / OPERATOR / ADMIN */
    private String role;

    /** ACTIVE / DISABLED / LOCKED */
    private String status;

    private Integer failedLoginCount;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime passwordChangedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
