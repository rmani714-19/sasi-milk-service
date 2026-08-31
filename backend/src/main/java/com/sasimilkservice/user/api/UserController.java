package com.sasimilkservice.user.api;

import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.AppUserPrincipal;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.user.domain.UserAccount;
import com.sasimilkservice.user.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me() {
        AppUserPrincipal p = CurrentUser.principal()
                .orElseThrow(() -> new NotFoundException("No authenticated user"));
        UserAccount u = users.require(p.userId());
        return new MeResponse(u.getPublicId().toString(), u.getUsername(), u.getFullName(),
                u.getEmail(), u.isMustChangePassword(), new TreeSet<>(p.permissions()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public List<UserSummary> list() {
        return users.findAll().stream()
                .map(u -> new UserSummary(u.getPublicId().toString(), u.getUsername(),
                        u.getFullName(), u.getStatus().name()))
                .toList();
    }

    public record MeResponse(String id, String username, String fullName, String email,
                             boolean mustChangePassword, Set<String> permissions) {
    }

    public record UserSummary(String id, String username, String fullName, String status) {
    }
}
