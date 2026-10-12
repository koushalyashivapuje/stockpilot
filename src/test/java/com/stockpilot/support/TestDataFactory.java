package com.stockpilot.support;

import com.stockpilot.entity.*;
import com.stockpilot.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Shared helper for building test fixtures directly through repositories. */
@Component
public class TestDataFactory {

    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private int counter = 0;

    public User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Test User " + (counter++))
                .build());
    }

    public Workspace createWorkspace(String name) {
        return workspaceRepository.save(Workspace.builder()
                .name(name)
                .slug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + (counter++))
                .build());
    }

    public WorkspaceMembership addMember(Workspace workspace, User user, Role role) {
        return membershipRepository.save(WorkspaceMembership.builder()
                .workspace(workspace).user(user).role(role).build());
    }

    public Product createProduct(Workspace workspace, String sku, BigDecimal price, int stock, int lowStockThreshold) {
        return productRepository.save(Product.builder()
                .workspace(workspace)
                .name("Product " + sku)
                .sku(sku)
                .unitPrice(price)
                .stockQuantity(stock)
                .lowStockThreshold(lowStockThreshold)
                .active(true)
                .build());
    }
}
