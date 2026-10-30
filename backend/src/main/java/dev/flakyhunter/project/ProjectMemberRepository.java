package dev.flakyhunter.project;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Key> {

	@Query("""
			select new dev.flakyhunter.project.ProjectSummary(p.slug, p.name, p.defaultBranch, m.role, p.createdAt)
			from ProjectMember m join Project p on p.id = m.id.projectId
			where m.id.userId = :userId
			order by lower(p.name)""")
	List<ProjectSummary> findProjectsOfUser(UUID userId);

	@Query("""
			select new dev.flakyhunter.project.MemberView(u.id, u.email, u.displayName, m.role)
			from ProjectMember m join User u on u.id = m.id.userId
			where m.id.projectId = :projectId
			order by lower(u.displayName)""")
	List<MemberView> findMembersOfProject(UUID projectId);

	@Query("select count(m) from ProjectMember m where m.id.projectId = :projectId and m.role = :role")
	long countByRole(UUID projectId, Role role);

}
