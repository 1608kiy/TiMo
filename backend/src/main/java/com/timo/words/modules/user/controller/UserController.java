package com.timo.words.modules.user.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.user.service.UserService;
import com.timo.words.modules.user.service.UserService.ProfileDTO;
import com.timo.words.modules.user.service.UserService.UpdatePreferencesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Tag(name = "用户模块")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<ProfileDTO> getProfile(Authentication auth) {
        return Result.success(userService.getProfile((Long) auth.getPrincipal()));
    }

    @Operation(summary = "更新偏好设置")
    @PutMapping("/preferences")
    public Result<ProfileDTO> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            Authentication auth) {
        return Result.success(userService.updatePreferences((Long) auth.getPrincipal(), request));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result<ProfileDTO> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(400, "仅支持图片文件");
        }
        // Validate file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "文件大小不能超过5MB");
        }
        String ext = "";
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf(".")).toLowerCase();
        }
        // Whitelist allowed extensions
        if (!java.util.Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(ext)) {
            return Result.error(400, "不支持的图片格式");
        }
        String filename = "avatar-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path uploadDir = Paths.get("uploads/avatars");
        Files.createDirectories(uploadDir);
        file.transferTo(uploadDir.resolve(filename));
        String avatarUrl = "/uploads/avatars/" + filename;
        return Result.success(userService.updateAvatar((Long) auth.getPrincipal(), avatarUrl));
    }

    @Operation(summary = "注销账户")
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(Authentication auth) {
        userService.deleteAccount((Long) auth.getPrincipal());
        return Result.success(null);
    }
}
