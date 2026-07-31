package com.sgkrashi.inquiry.repository;

import com.sgkrashi.inquiry.entity.Inquiry;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InquiryRepository extends JpaRepository<Inquiry, Long>, JpaSpecificationExecutor<Inquiry> {

    Page<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByStatus(InquiryStatus status);
}
