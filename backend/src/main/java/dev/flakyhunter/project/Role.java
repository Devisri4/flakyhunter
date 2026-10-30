package dev.flakyhunter.project;

/** Project roles, weakest first. */
public enum Role {

	VIEWER, MEMBER, OWNER;

	public boolean atLeast(Role required) {
		return compareTo(required) >= 0;
	}

}
