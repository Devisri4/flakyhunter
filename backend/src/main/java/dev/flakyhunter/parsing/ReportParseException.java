package dev.flakyhunter.parsing;

/** The report is broken or too big. Retrying won't help, so the job fails with this message. */
public class ReportParseException extends RuntimeException {

	public ReportParseException(String message) {
		super(message);
	}

	public ReportParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
