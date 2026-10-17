package tech.nmfin.algoquant.web.sma2;

import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

/** One immutable simulation snapshot, with a statistics tab for each strategy and daily inputs/NAV. */
final class PerformanceWorkbook {
    static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static byte[] export(SMA2App.Result report) throws IOException {
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            CellStyle header=book.createCellStyle();Font font=book.createFont();
            font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());header.setFont(font);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle number=book.createCellStyle();number.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            CellStyle percent=book.createCellStyle();percent.setDataFormat(book.createDataFormat().getFormat("0.00%"));
            CellStyle date=book.createCellStyle();date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            statistics(book,"SMA2 statistics",report.performanceMeasures,header,number,percent);
            statistics(book,"Buy and hold statistics",report.buyHoldPerformanceMeasures,header,number,percent);
            Sheet history=book.createSheet("Daily data");
            String benchmark="FULL_EQUITY_V1".equals(report.sizingModel)?"fully_invested_buy_hold_nav":"legacy_one_share_benchmark_nav";
            heading(history,header,"date","adjusted_close","fast_sma","slow_sma","sma2_nav",benchmark);
            int row=1;
            for(SMA2App.Point point:report.series) {
                Row r=history.createRow(row++);
                numeric(r,0,(double)ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),LocalDate.parse(point.date)),date);
                numeric(r,1,point.close,number);numeric(r,2,point.fast,number);numeric(r,3,point.slow,number);
                numeric(r,4,point.nav,number);numeric(r,5,point.benchmark,number);
            }
            finish(history,6);for(int i=0;i<6;i++)history.setColumnWidth(i,(i==5?34:i==1?23:18)*256);
            // Context is stored beside source data, outside the six requested data columns.
            text(history.getRow(0),7,"Simulation");
            String[][] context={{"Symbol",report.symbol},{"Start",report.start},{"End",report.end},
                {"Fast SMA (trading days)",String.valueOf(report.fast)},{"Slow SMA (trading days)",String.valueOf(report.slow)},
                {"Report ID",report.reportId},{"Generated at",report.generatedAt},{"Source",report.source}};
            for(int i=0;i<context.length;i++){Row r=history.getRow(i+1);if(r==null)r=history.createRow(i+1);text(r,7,context[i][0]);text(r,8,context[i][1]);}
            history.setColumnWidth(7,26*256);history.setColumnWidth(8,48*256);
            book.write(out);return out.toByteArray();
        }
    }
    private static void statistics(XSSFWorkbook book,String name,List<AllPerformanceMeasures.Metric> metrics,
            CellStyle header,CellStyle number,CellStyle percent) throws IOException {
        Sheet sheet=book.createSheet(name);
        heading(sheet,header,"Measure","Value","Unit","Status","Group","Library class","Parameters / explanation");
        if(metrics==null||metrics.isEmpty()) {
            text(sheet.createRow(1),0,"This saved report predates statistics for this strategy. Run a new backtest.");
        } else {
            // Keep the library exporter as the authoritative serialization of each scalar value.
            try(CsvListReader csv=new CsvListReader(new StringReader(PerformanceCsv.export(metrics)),CsvPreference.STANDARD_PREFERENCE)) {
                csv.read();List<String> values=csv.read();
                if(values==null||values.size()!=metrics.size()+1)throw new IOException("Incomplete library CSV.");
                for(int i=0;i<metrics.size();i++) {
                    AllPerformanceMeasures.Metric m=metrics.get(i);Row row=sheet.createRow(i+1);
                    text(row,0,m.label);double value=Double.parseDouble(values.get(i+1));
                    if(Double.isFinite(value))numeric(row,1,value,"fraction".equals(m.unit)?percent:number);
                    else text(row,1,"N/A");
                    text(row,2,m.unit);text(row,3,m.status);text(row,4,m.group);
                    text(row,5,m.sourceClass);text(row,6,m.detail);
                }
            }
        }
        finish(sheet,7);int[] widths={48,20,20,23,34,38,95};
        for(int i=0;i<widths.length;i++)sheet.setColumnWidth(i,widths[i]*256);
    }
    private static void heading(Sheet sheet,CellStyle style,String... labels) {
        Row row=sheet.createRow(0);row.setHeightInPoints(24);
        for(int i=0;i<labels.length;i++){Cell c=row.createCell(i);c.setCellValue(labels[i]);c.setCellStyle(style);}
    }
    private static void text(Row row,int column,String value){row.createCell(column).setCellValue(value==null?"":value);}
    private static void numeric(Row row,int column,Double value,CellStyle style) {
        Cell c=row.createCell(column);if(value!=null&&Double.isFinite(value))c.setCellValue(value);c.setCellStyle(style);
    }
    private static void finish(Sheet sheet,int columns) {
        sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,sheet.getLastRowNum(),0,columns-1));
    }
}
