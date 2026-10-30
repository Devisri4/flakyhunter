package dev.flakyhunter.project;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepository extends JpaRepository<Project, UUID> {

	Optional<Project> findBySlug(String slug);

	boolean existsBySlug(String slug);

}
