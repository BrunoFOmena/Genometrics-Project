package com.ngs.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, UUID> {
    List<AiInteraction> findTop20ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
