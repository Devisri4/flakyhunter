package dev.flakyhunter.parsing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Picks the parser per file: by the upload's declared format, or (AUTO) by the file's first character. */
@Component
public class ReportParsers {

	public enum Format {
		AUTO, JUNIT, PLAYWRIGHT
	}

	private final JUnitXmlParser junit;
	private final PlaywrightJsonParser playwright;

	ReportParsers(JUnitXmlParser junit, PlaywrightJsonParser playwright) {
		this.junit = junit;
		this.playwright = playwright;
	}

	public List<ParsedTest> parse(Format format, List<byte[]> files) {
		List<InputStream> xml = new ArrayList<>();
		List<InputStream> json = new ArrayList<>();
		for (byte[] file : files) {
			Format actual = format == Format.AUTO ? detect(file) : format;
			(actual == Format.PLAYWRIGHT ? json : xml).add(new ByteArrayInputStream(file));
		}
		Map<String, ParsedTest> merged = new LinkedHashMap<>();
		if (!xml.isEmpty()) {
			junit.parse(xml).forEach(t -> merge(merged, t));
		}
		if (!json.isEmpty()) {
			playwright.parse(json).forEach(t -> merge(merged, t));
		}
		return List.copyOf(merged.values());
	}

	static Format detect(byte[] file) {
		for (byte b : file) {
			// skip whitespace and a UTF-8 byte-order mark
			if (b == ' ' || b == '\n' || b == '\r' || b == '\t' || b == (byte) 0xEF || b == (byte) 0xBB || b == (byte) 0xBF) {
				continue;
			}
			if (b == '{') {
				return Format.PLAYWRIGHT;
			}
			return Format.JUNIT;
		}
		return Format.JUNIT; // empty: the JUnit parser reports it clearly
	}

	private static void merge(Map<String, ParsedTest> merged, ParsedTest t) {
		merged.merge(t.suite() + "::" + t.name(), t, ParsedTest::mergeWith);
	}

}
