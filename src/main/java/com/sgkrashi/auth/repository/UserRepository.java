package com.sgkrashi.auth.repository;

import com.sgkrashi.auth.entity.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // roles is @ManyToMany(fetch = EAGER) already, but @EntityGraph here keeps
    // the join in the SAME query as the Specification-filtered page, rather
    // than triggering a second eager-fetch query per row.
    @Override
    @EntityGraph(attributePaths = "roles")
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}
