package tech.nmfin.algoquant.web;

/**
 * Common snapshot metadata inherited by each app without changing saved JSON field names.
 *
 * <p><b>Using this example.</b>
 * Base metadata for an immutable result snapshot. App result classes add their inputs, daily
 * series and statistics; retaining the original fields allows stored reports to be rendered
 * and exported later.
 * </p>
 */
public class SavedReport {
    public String reportId;
    public String reportPath;
    public String appRevision;
}
