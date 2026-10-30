package dev.flakyhunter.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

	List<ApiKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

	Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);

	Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

	/** Written at most once a minute per key, so busy CI doesn't turn every upload into an extra write. */
	@Modifying
	@Query("""
			update ApiKey k set k.lastUsedAt = :now
			where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :threshold)""")
	int touch(UUID id, Instant now, Instant threshold);

}
