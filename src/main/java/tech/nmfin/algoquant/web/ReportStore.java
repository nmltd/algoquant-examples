package tech.nmfin.algoquant.web;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * App-independent metadata and serialization over the immutable snapshot backend.
 *
 * <p><b>Using this example.</b>
 * Persists and retrieves typed app reports over SnapshotStore. Use the app namespace with the
 * saved identifier so different tools share storage without mixing report types; reports are
 * snapshots of computed results rather than instructions to recompute with current prices.
 * </p>
 */
public final class ReportStore {
    private static final Gson JSON = new Gson();
    private final SnapshotStore snapshots;

    public ReportStore(SnapshotStore snapshots) {
        this.snapshots = snapshots;
    }

    public ReportStore(Path directory) throws IOException {
        this(new SnapshotStore(directory));
    }

    public ReportStore(String bucket, String api, SnapshotStore.Token token) {
        this(new SnapshotStore(bucket, api, token));
    }

    public static ReportStore fromEnvironment() throws IOException {
        return new ReportStore(SnapshotStore.fromEnvironment());
    }

    /** Assign a fresh URL for every run, even when the caller reuses a result object. */
    public String save(SavedReport report, String app) throws IOException {
        validateApp(app);
        report.reportId = UUID.randomUUID().toString().replace("-", "");
        report.reportPath = "/" + app + "/reports/" + report.reportId;
        report.appRevision = System.getenv().getOrDefault("SMA2_APP_REVISION", "local");
        return snapshots.save(report.reportId, JSON.toJson(report));
    }

    /** Returns null for another app's snapshot as well as for a missing report. */
    public String read(String id, String app) throws IOException {
        validateApp(app);
        String body = snapshots.read(id);
        if (body == null) return null;
        SavedReport metadata = JSON.fromJson(body, SavedReport.class);
        return metadata != null && ("/" + app + "/reports/" + id).equals(metadata.reportPath)
                ? body
                : null;
    }

    private static void validateApp(String app) {
        if (app == null || !app.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Invalid app name.");
    }
}
