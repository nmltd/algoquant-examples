package tech.nmfin.algoquant.web.sma2;
import java.io.*;import java.nio.file.Path;import java.util.UUID;
import com.google.gson.Gson;import tech.nmfin.algoquant.web.SnapshotStore;
/** SMA2 metadata adapter over the shared immutable snapshot store. */
final class ReportStore {
 final SnapshotStore store;
 interface Token extends SnapshotStore.Token {}
 ReportStore(Path directory)throws IOException{store=new SnapshotStore(directory);}
 ReportStore(String bucket,String api,Token token){store=new SnapshotStore(bucket,api,token);}
 private ReportStore(SnapshotStore store){this.store=store;}
 static ReportStore fromEnvironment()throws IOException{return new ReportStore(SnapshotStore.fromEnvironment());}
 String save(SMA2App.Result report)throws IOException {
  report.reportId=UUID.randomUUID().toString().replace("-","");report.reportPath="/sma2/reports/"+report.reportId;
  report.appRevision=System.getenv().getOrDefault("SMA2_APP_REVISION","local");
  return store.save(report.reportId,new Gson().toJson(report));
 }
 String read(String id)throws IOException{return store.read(id);}
 static void validateId(String id){SnapshotStore.validateId(id);}
}
