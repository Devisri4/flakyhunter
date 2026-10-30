package dev.flakyhunter.ingestion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import dev.flakyhunter.parsing.ReportParseException;

final class Gzip {

	private Gzip() {
	}

	static boolean isGzip(byte[] bytes) {
		return bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
	}

	static byte[] compress(byte[] plain) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, plain.length / 4));
		try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
			gz.write(plain);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** Decompresses, refusing to produce more than {@code limit} bytes (zip-bomb guard). */
	static byte[] decompress(byte[] compressed, long limit) {
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[64 * 1024];
			long total = 0;
			int read;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > limit) {
					throw new ReportParseException("The upload is larger than " + (limit / 1024 / 1024)
							+ " MB once decompressed");
				}
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new ReportParseException("A .gz file could not be decompressed", e);
		}
	}

}
