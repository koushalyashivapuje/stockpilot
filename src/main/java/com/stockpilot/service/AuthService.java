package com.stockpilot.service;

import com.stockpilot.dto.auth.*;
import com.stockpilot.entity.*;
import com.stockpilot.exception.BusinessRuleException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.UnauthorizedException;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.repository.WorkspaceMembershipRepository;
import com.stockpilot.repository.WorkspaceRepository;
import com.stockpilot.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WorkspaceAccessService workspaceAccessService;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthService(UserRepository userRepository, WorkspaceRepository workspaceRepository,
                        WorkspaceMembershipRepository membershipRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, WorkspaceAccessService workspaceAccessService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();
        user = userRepository.save(user);

        String slug = slugify(request.workspaceName());
        if (workspaceRepository.existsBySlug(slug)) {
            slug = slug + "-" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Long.toString(System.nanoTime()).getBytes()).substring(0, 6).toLowerCase();
        }

        Workspace workspace = Workspace.builder()
                .name(request.workspaceName())
                .slug(slug)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .workspace(workspace)
                .user(user)
                .role(Role.OWNER)
                .build();
        membershipRepository.save(membership);

        return buildAuthResponse(user, List.of(membership));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        List<WorkspaceMembership> memberships = membershipRepository.findByUser_Id(user.getId());
        return buildAuthResponse(user, memberships);
    }

    /**
     * Owner-only, demo-simplified staff provisioning. See {@link InviteStaffRequest} javadoc
     * for why this exists instead of a real invitation/email flow.
     */
    @Transactional
    public String addStaffMember(Long workspaceId, Long actingUserId, InviteStaffRequest request) {
        workspaceAccessService.requireOwner(workspaceId, actingUserId);

        var existing = userRepository.findByEmailIgnoreCase(request.email());
        User user;
        String temporaryPassword = null;

        if (existing.isPresent()) {
            user = existing.get();
        } else {
            temporaryPassword = generateTemporaryPassword();
            user = User.builder()
                    .email(request.email().toLowerCase())
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .fullName(request.fullName())
                    .build();
            user = userRepository.save(user);
        }

        if (membershipRepository.existsByWorkspace_IdAndUser_Id(workspaceId, user.getId())) {
            throw new BusinessRuleException("This user is already a member of the workspace.");
        }

        var workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessRuleException("Workspace not found."));

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .workspace(workspace)
                .user(user)
                .role(Role.STAFF)
                .build();
        membershipRepository.save(membership);

        return temporaryPassword; // null if the user already had an account
    }

    private AuthResponse buildAuthResponse(User user, List<WorkspaceMembership> memberships) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        List<AuthResponse.WorkspaceMembershipDto> workspaces = memberships.stream()
                .map(m -> new AuthResponse.WorkspaceMembershipDto(
                        m.getWorkspace().getId(), m.getWorkspace().getName(), m.getWorkspace().getSlug(),
                        m.getRole().name()))
                .toList();
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), workspaces);
    }

    private String slugify(String input) {
        String base = input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base.isBlank() ? "workspace" : base;
    }

    private String generateTemporaryPassword() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
