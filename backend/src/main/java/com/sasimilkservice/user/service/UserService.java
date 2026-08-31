package com.sasimilkservice.user.service;

import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.user.domain.UserAccount;
import com.sasimilkservice.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserAccountRepository users;
    private final PermissionResolver permissionResolver;

    public UserService(UserAccountRepository users, PermissionResolver permissionResolver) {
        this.users = users;
        this.permissionResolver = permissionResolver;
    }

    public UserAccount require(long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    public UserAccount requireByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("User " + username + " not found"));
    }

    public java.util.Set<String> permissionsOf(UserAccount user) {
        return permissionResolver.effectivePermissions(user);
    }

    public List<UserAccount> findAll() {
        return users.findAll();
    }
}
