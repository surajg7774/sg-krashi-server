package com.sgkrashi.inquiry.repository;

import com.sgkrashi.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
