package com.sasimilkservice.user.service;

import com.sasimilkservice.common.security.Permission;
import com.sasimilkservice.user.domain.Role;
import com.sasimilkservice.user.domain.UserAccount;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Flattens a user's roles into a permission-code set. The {@code ALL} sentinel is
 * expanded to every concrete {@link Permission}.
 */
@Component
public class PermissionResolver {

    public Set<String> effectivePermissions(UserAccount user) {
        Set<String> perms = new HashSet<>();
        for (Role role : user.getRoles()) {
            perms.addAll(role.getPermissions());
        }
        if (perms.contains(Permission.ALL_CODE)) {
            perms.addAll(Permission.allConcrete()); // retain ALL so hasAuthority('ALL') also matches
        }
        return perms;
    }
}
