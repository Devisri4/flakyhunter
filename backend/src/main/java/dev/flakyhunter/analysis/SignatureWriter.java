package dev.flakyhunter.analysis;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Upserts failure signatures for one batch of failures and returns their ids by hash. */
@Component
public class SignatureWriter {

	/** One failure to group; {@code stack} is kept as the sample shown in the UI. */
	public record Failure(SignatureNormalizer.Signature signature, String stack) {
	}

	private final JdbcTemplate jdbc;

	SignatureWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Long> upsert(UUID projectId, OffsetDateTime seenAt, List<Failure> failures) {
		Map<String, Long> ids = new HashMap<>();
		if (failures.isEmpty()) {
			return ids;
		}
		// one row per distinct hash, counting how often it occurred in this batch
		Map<String, Failure> first = new LinkedHashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Failure f : failures) {
			first.putIfAbsent(f.signature().hash(), f);
			counts.merge(f.signature().hash(), 1, Integer::sum);
		}
		String[] hashes = first.keySet().toArray(String[]::new);
		String[] types = new String[hashes.length];
		String[] messages = new String[hashes.length];
		String[] stacks = new String[hashes.length];
		Integer[] occurrences = new Integer[hashes.length];
		for (int i = 0; i < hashes.length; i++) {
			Failure f = first.get(hashes[i]);
			types[i] = f.signature().exceptionType();
			messages[i] = f.signature().normalizedMessage();
			stacks[i] = f.stack();
			occurrences[i] = counts.get(hashes[i]);
		}
		jdbc.query(con -> {
			PreparedStatement ps = con.prepareStatement("""
					insert into failure_signatures (project_id, hash, exception_type, normalized_message, sample_stack,
					                                first_seen, last_seen, occurrences)
					select ?, s.hash, s.type, s.message, s.stack, ?, ?, s.occurrences
					from unnest(?::text[], ?::text[], ?::text[], ?::text[], ?::int[]) as s(hash, type, message, stack, occurrences)
					on conflict (project_id, hash) do update
					   set occurrences = failure_signatures.occurrences + excluded.occurrences,
					       last_seen = greatest(failure_signatures.last_seen, excluded.last_seen),
					       first_seen = least(failure_signatures.first_seen, excluded.first_seen)
					returning id, hash""");
			ps.setObject(1, projectId);
			ps.setObject(2, seenAt);
			ps.setObject(3, seenAt);
			ps.setArray(4, con.createArrayOf("text", hashes));
			ps.setArray(5, con.createArrayOf("text", types));
			ps.setArray(6, con.createArrayOf("text", messages));
			ps.setArray(7, con.createArrayOf("text", stacks));
			ps.setArray(8, con.createArrayOf("int", occurrences));
			return ps;
		}, rs -> {
			ids.put(rs.getString("hash"), rs.getLong("id"));
		});
		return ids;
	}

}
