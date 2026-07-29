package com.sgkrashi.media.repository;

import com.sgkrashi.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(String ownerType, Long ownerId);

    /** Batched lookup for a page of owners at once — avoids one query per row in a list view. */
    List<MediaAsset> findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(String ownerType, List<Long> ownerIds);
}
