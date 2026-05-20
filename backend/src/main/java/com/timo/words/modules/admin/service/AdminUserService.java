package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminOperationLogRepository operationLogRepository;

    public Page<User> listUsers(String keyword, String role, String status, int page, int size) {
        Specification<User> spec = Specification.where(null);

        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nickname")), "%" + keyword.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("email")), "%" + keyword.toLowerCase() + "%")
            ));
        }
        if (role != null && !role.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }

        return userRepository.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public User getUserDetail(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
    }

    public void updateRole(Long userId, String newRole, Long operatorId, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        if (!"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.SUPER_ADMIN_REQUIRED);
        }

        if (userId.equals(operatorId)) {
            throw new BusinessException(400, "不能修改自己的角色");
        }

        String oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);

        logOperation(operatorId, operator.getEmail(), "ROLE_CHANGE", "user", userId,
                oldRole + " -> " + newRole, request);
    }

    public void updateStatus(Long userId, String newStatus, Long operatorId, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        if (userId.equals(operatorId)) {
            throw new BusinessException(400, "不能封禁自己");
        }

        String oldStatus = user.getStatus();
        user.setStatus(newStatus);
        userRepository.save(user);

        logOperation(operatorId, operator.getEmail(),
                "BANNED".equals(newStatus) ? "BAN" : "UNBAN",
                "user", userId, oldStatus + " -> " + newStatus, request);
    }

    public void deleteUser(Long userId, Long operatorId, HttpServletRequest request) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.SUPER_ADMIN_REQUIRED);
        }

        if (userId.equals(operatorId)) {
            throw new BusinessException(400, "不能删除自己");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        if ("SUPER_ADMIN".equals(user.getRole())) {
            long superAdminCount = userRepository.countByRole("SUPER_ADMIN");
            if (superAdminCount <= 1) {
                throw new BusinessException(400, "不能删除最后一个超级管理员");
            }
        }

        userRepository.delete(user);

        logOperation(operatorId, operator.getEmail(), "DELETE_USER", "user", userId,
                "删除用户: " + user.getEmail(), request);
    }

    private void logOperation(Long adminId, String adminEmail, String type, String targetType, Long targetId, String detail, HttpServletRequest request) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(adminId);
        log.setAdminEmail(adminEmail);
        log.setOperationType(type);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(request.getRemoteAddr());
        operationLogRepository.save(log);
    }
}
