package com.timo.words.modules.auth.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.auth.service.ForgotPasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    @Data
    public static class SendCodeRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "验证码不能为空")
        @Size(min = 6, max = 6, message = "验证码为6位数字")
        private String code;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 50, message = "密码长度6-50位")
        private String newPassword;
    }

    @PostMapping("/forgot-password/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        forgotPasswordService.sendVerificationCode(request.getEmail());
        return Result.success(null);
    }

    @PostMapping("/forgot-password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        forgotPasswordService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return Result.success(null);
    }
}
