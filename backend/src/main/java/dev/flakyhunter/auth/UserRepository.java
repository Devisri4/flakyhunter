package dev.flakyhunter.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Emails are stored lower-case, so callers pass a lower-cased email. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
