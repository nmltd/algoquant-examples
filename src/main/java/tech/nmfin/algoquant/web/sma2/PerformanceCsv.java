package tech.nmfin.algoquant.web.sma2;
import java.io.IOException;import java.util.List;import tech.nmfin.algoquant.web.AllPerformanceMeasures;
/** Compatibility facade; both applications use the shared library-export adapter. */
final class PerformanceCsv {static String export(List<AllPerformanceMeasures.Metric> metrics)throws IOException{return tech.nmfin.algoquant.web.PerformanceCsv.export(metrics);}}
