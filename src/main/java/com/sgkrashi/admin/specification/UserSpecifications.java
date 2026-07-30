package com.sgkrashi.admin.specification;

import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/** Same one-Specification-per-filter, null-means-skip pattern as {@code ProductSpecifications} (Module 5). */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> nameOrEmailContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern));
        };
    }

    public static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> {
            if (roleName == null || roleName.isBlank()) {
                return null;
            }
            query.distinct(true);
            Join<User, Role> roles = root.join("roles");
            return cb.equal(roles.get("name"), roleName);
        };
    }

    public static Specification<User> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null
                ? null
                : cb.equal(root.get("isActive"), isActive);
    }
}
