package dev.flakyhunter.project;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.flakyhunter.auth.User;
import dev.flakyhunter.auth.UserRepository;
import dev.flakyhunter.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

	private final ProjectMemberRepository members;
	private final UserRepository users;
	private final ProjectAccess access;

	MemberService(ProjectMemberRepository members, UserRepository users, ProjectAccess access) {
		this.members = members;
		this.users = users;
		this.access = access;
	}

	@Transactional(readOnly = true)
	public List<MemberView> list(String slug) {
		return members.findMembersOfProject(access.require(slug, Role.OWNER).project().getId());
	}

	@Transactional
	public MemberView add(String slug, String email, Role role) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		User user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> ApiException.notFound("No FlakyHunter account uses that email yet"));
		ProjectMember.Key key = new ProjectMember.Key(projectId, user.getId());
		if (members.existsById(key)) {
			throw ApiException.conflict("That person is already a member");
		}
		members.save(new ProjectMember(projectId, user.getId(), role));
		return new MemberView(user.getId(), user.getEmail(), user.getDisplayName(), role);
	}

	@Transactional
	public void changeRole(String slug, UUID userId, Role role) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		ProjectMember member = find(projectId, userId);
		if (member.getRole() == Role.OWNER && role != Role.OWNER) {
			ensureAnotherOwner(projectId);
		}
		member.setRole(role);
		members.save(member);
	}

	@Transactional
	public void remove(String slug, UUID userId) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		ProjectMember member = find(projectId, userId);
		if (member.getRole() == Role.OWNER) {
			ensureAnotherOwner(projectId);
		}
		members.delete(member);
	}

	private ProjectMember find(UUID projectId, UUID userId) {
		return members.findById(new ProjectMember.Key(projectId, userId))
				.orElseThrow(() -> ApiException.notFound("Member not found"));
	}

	private void ensureAnotherOwner(UUID projectId) {
		if (members.countByRole(projectId, Role.OWNER) <= 1) {
			throw ApiException.conflict("A project needs at least one owner");
		}
	}

}
