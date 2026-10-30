package dev.flakyhunter.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

	List<ApiKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

	Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);

}
