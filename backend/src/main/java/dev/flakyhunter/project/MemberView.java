package dev.flakyhunter.project;

import java.util.UUID;

public record MemberView(UUID userId, String email, String displayName, Role role) {
}
