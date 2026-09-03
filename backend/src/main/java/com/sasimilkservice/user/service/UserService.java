package com.sasimilkservice.user.service;

import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.user.domain.UserAccount;
import com.sasimilkservice.user.repository.UserAccountRepository;
import com.sasimilkservice.user.spi.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService implements UserDirectory {

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

    // ---- UserDirectory ----

    @Override
    public Optional<String> displayName(Long userId) {
        return userId == null ? Optional.empty()
                : users.findById(userId).map(u -> u.getFullName() == null || u.getFullName().isBlank()
                        ? u.getUsername() : u.getFullName());
    }

    @Override
    public Map<Long, String> displayNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream().collect(Collectors.toMap(
                UserAccount::getId,
                u -> u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName()));
    }
}
