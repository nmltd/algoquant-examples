package tech.nmfin.algoquant.web;

import java.io.*;
import tech.nmfin.algoquant.web.AllPerformanceMeasures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

/**
 * Small OOXML export of one immutable snapshot; no desktop or spreadsheet runtime required.
 *
 * <p><b>Using this example.</b>
 * Packages existing snapshot data into OOXML worksheets and obtains scalar measure rows
 * through PerformanceCsv. Numeric values remain numeric cells while display styles control
 * decimal formatting. This utility writes exports without recalculating strategy performance.
 * </p>
 */
public final class SpreadsheetWorkbook {
    public static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    public static byte[] export(String[] names,String[] sheets) throws IOException {
        names=sheetNames(names);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out)) {
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder book=new StringBuilder("<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder relations=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for(int i=1;i<=names.length;i++) {
                types.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                book.append("<sheet name=\"").append(xml(names[i-1])).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                relations.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
                entry(zip,"xl/worksheets/sheet"+i+".xml",sheets[i-1]);
            }
            entry(zip,"[Content_Types].xml",types.append("</Types>").toString());
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml",book.append("</sheets></workbook>").toString());
            entry(zip,"xl/_rels/workbook.xml.rels",relations.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>").toString());
            entry(zip,"xl/styles.xml","<styleSheet xmlns=\""+NS+"\"><numFmts count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/><numFmt numFmtId=\"165\" formatCode=\"0.00%\"/><numFmt numFmtId=\"166\" formatCode=\"yyyy-mm-dd\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF146B57\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"5\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            zip.finish();return out.toByteArray();
        }
    }
    public static String statistics(List<AllPerformanceMeasures.Metric> metrics) throws IOException {
        StringBuilder sheet=start(new int[]{48,20,20,23,34,38,95});
        header(sheet,"Measure","Value","Unit","Status","Group","Library class","Parameters / explanation");
        int last=2;
        if(metrics==null||metrics.isEmpty()) {
            sheet.append("<row r=\"2\">");text(sheet,"A2","This saved report predates statistics for this strategy. Run a new backtest.",0);sheet.append("</row>");
        } else try(CsvListReader csv=new CsvListReader(new StringReader(PerformanceCsv.export(metrics)),CsvPreference.STANDARD_PREFERENCE)) {
            csv.read();List<String> values=csv.read();
            if(values==null||values.size()!=metrics.size()+1)throw new IOException("Incomplete library CSV.");
            for(int i=0;i<metrics.size();i++) {
                AllPerformanceMeasures.Metric m=metrics.get(i);int r=i+2;last=r;sheet.append("<row r=\"").append(r).append("\">");
                text(sheet,"A"+r,m.label,0);double value=Double.parseDouble(values.get(i+1));
                if(Double.isFinite(value))number(sheet,"B"+r,value,"fraction".equals(m.unit)?3:2);else text(sheet,"B"+r,"N/A",0);
                text(sheet,"C"+r,m.unit,0);text(sheet,"D"+r,m.status,0);text(sheet,"E"+r,m.group,0);
                text(sheet,"F"+r,m.sourceClass,0);text(sheet,"G"+r,m.detail,0);sheet.append("</row>");
            }
        }
        return finish(sheet,"G",last);
    }
    public static String[] sheetNames(String[] names) {
        Set<String> used=new HashSet<>();String[] out=names.clone();
        for(int i=0;i<out.length;i++){
            String base=out[i].replace(':',' ').replace('/',' ').replace((char)92,' ').replace('?',' ').replace('*',' ').replace('[',' ').replace(']',' ').trim();if(base.isEmpty())base="Series";
            if(base.length()>31)base=base.substring(0,31);String name=base;int n=2;
            while(!used.add(name.toLowerCase(Locale.ROOT))){String suffix=" ("+(n++)+")";name=base.substring(0,Math.min(base.length(),31-suffix.length()))+suffix;}
            out[i]=name;
        }return out;
    }
    public static String column(int index) {
        StringBuilder result=new StringBuilder();for(int n=index+1;n>0;n=(n-1)/26)result.insert(0,(char)('A'+(n-1)%26));return result.toString();
    }
    public static String table(String[] headers,List<List<Object>> rows) {
        int[] widths=new int[headers.length];Arrays.fill(widths,24);
        StringBuilder sheet=start(widths);header(sheet,headers);int row=1;
        for(List<Object> values:rows){row++;sheet.append("<row r='").append(row).append("'>");
            for(int i=0;i<values.size();i++){Object value=values.get(i);String cell=column(i)+row;
                if(value instanceof LocalDate)number(sheet,cell,(double)ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),(LocalDate)value),4);
                else if(value instanceof Number)number(sheet,cell,((Number)value).doubleValue(),2);
                else if(value!=null)text(sheet,cell,String.valueOf(value),0);
            }sheet.append("</row>");
        }return finish(sheet,column(headers.length-1),row);
    }
    public static StringBuilder start(int[] widths) {
        StringBuilder s=new StringBuilder("<worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for(int i=0;i<widths.length;i++)s.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        return s.append("</cols><sheetData>");
    }
    public static void header(StringBuilder s,String... labels) {
        s.append("<row r=\"1\" ht=\"24\" customHeight=\"1\">");
        for(int i=0;i<labels.length;i++)text(s,column(i)+"1",labels[i],1);
        s.append("</row>");
    }
    public static String finish(StringBuilder s,String last,int row){return s.append("</sheetData><autoFilter ref=\"A1:").append(last).append(row).append("\"/></worksheet>").toString();}
    public static void text(StringBuilder s,String cell,String value,int style) {
        s.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value==null?"":value)).append("</t></is></c>");
    }
    public static void number(StringBuilder s,String cell,Double value,int style) {
        if(value!=null&&Double.isFinite(value))s.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\"><v>").append(value).append("</v></c>");
    }
    public static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    public static void entry(ZipOutputStream zip,String name,String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+body).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
