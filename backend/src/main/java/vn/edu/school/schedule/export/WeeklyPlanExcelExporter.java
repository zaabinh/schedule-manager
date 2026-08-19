package vn.edu.school.schedule.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.weeklyplan.WeeklyPlanService;
import vn.edu.school.schedule.weeklyplan.api.DaySessionResponse;
import vn.edu.school.schedule.weeklyplan.api.EventResponse;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;

@Service
public class WeeklyPlanExcelExporter {
    private final JdbcTemplate jdbc;
    private final WeeklyPlanService plans;
    public WeeklyPlanExcelExporter(JdbcTemplate jdbc, WeeklyPlanService plans) { this.jdbc = jdbc; this.plans = plans; }

    public ExportedWorkbook export(UUID planId, AuthenticatedUser actor) {
        var weeks = jdbc.query("SELECT school_week_id FROM weekly_plans WHERE id=?", (rs,row)->rs.getObject(1,UUID.class), planId);
        if (weeks.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","Không tìm thấy kế hoạch tuần.");
        WeeklyPlanResponse plan = plans.getByWeek(weeks.getFirst(), actor);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Kế hoạch tuần");
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 1);
            sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.LeftMargin, 0.25);
            sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.RightMargin, 0.25);
            sheet.setColumnWidth(0, 18 * 256); sheet.setColumnWidth(1, 55 * 256); sheet.setColumnWidth(2, 55 * 256);
            Styles styles = new Styles(workbook);
            int rowIndex = 0;
            if ("DRAFT".equals(plan.status())) {
                Row draft = sheet.createRow(rowIndex++); merge(sheet,rowIndex-1,0,2); value(draft,0,"DRAFT — CHƯA CÔNG BỐ",styles.warning());
            }
            Row title = sheet.createRow(rowIndex++); merge(sheet,rowIndex-1,0,2);
            value(title,0,"KẾ HOẠCH " + plan.displayLabel().toUpperCase() + " (" + plan.startDate() + " – " + plan.endDate() + ")",styles.title());
            rowIndex++;
            for (var section : plan.sections()) {
                Row row = sheet.createRow(rowIndex++);
                value(row,0,section.title(),styles.sectionTitle());
                merge(sheet,rowIndex-1,1,2); value(row,1,safe(section.content()),styles.wrap());
            }
            Row duty = sheet.createRow(rowIndex++);
            value(duty,0,"Lớp trực",styles.sectionTitle());
            value(duty,1,"Sáng: " + (plan.morningDutyClass()==null?"Chưa phân công":plan.morningDutyClass().name()),styles.wrap());
            value(duty,2,"Chiều: " + (plan.afternoonDutyClass()==null?"Chưa phân công":plan.afternoonDutyClass().name()),styles.wrap());
            rowIndex++;
            Row header=sheet.createRow(rowIndex++); value(header,0,"Thứ / Ngày",styles.header()); value(header,1,"SÁNG",styles.header()); value(header,2,"CHIỀU",styles.header());
            for (var day : plan.days()) {
                Row row=sheet.createRow(rowIndex++); row.setHeightInPoints(42);
                value(row,0,day.dayLabel()+"\n"+day.date(),styles.wrap());
                value(row,1,session(day.sessions(),"MORNING"),styles.wrap());
                value(row,2,session(day.sessions(),"AFTERNOON"),styles.wrap());
            }
            sheet.setRepeatingRows(new CellRangeAddress(header.getRowNum(),header.getRowNum(),-1,-1));
            workbook.write(output);
            return new ExportedWorkbook("ke-hoach-"+plan.displayLabel().toLowerCase().replace(' ','-')+".xlsx",output.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException("Cannot create workbook",failure); }
    }

    private String session(java.util.List<DaySessionResponse> sessions,String name){return sessions.stream().filter(s->name.equals(s.session())).findFirst().map(s->{StringBuilder text=new StringBuilder(safe(s.baseContent()));for(EventResponse event:s.events()){if(!text.isEmpty())text.append('\n');if(event.startTime()!=null)text.append(event.startTime()).append(" · ");text.append(safe(event.content()));if(event.location()!=null&&!event.location().isBlank())text.append(" — ").append(safe(event.location()));}return text.toString();}).orElse("");}
    static String safe(String value){if(value==null)return "";String trimmed=value.stripLeading();return !trimmed.isEmpty()&&"=+-@".indexOf(trimmed.charAt(0))>=0?"'"+value:value;}
    private static void value(Row row,int column,String value,CellStyle style){Cell cell=row.createCell(column);cell.setCellValue(value);cell.setCellStyle(style);}
    private static void merge(org.apache.poi.ss.usermodel.Sheet sheet,int row,int first,int last){sheet.addMergedRegion(new CellRangeAddress(row,row,first,last));}
    public record ExportedWorkbook(String fileName,byte[] content){}
    private record Styles(CellStyle title,CellStyle warning,CellStyle sectionTitle,CellStyle header,CellStyle wrap){
        Styles(XSSFWorkbook wb){this(style(wb,true,14,IndexedColors.WHITE,IndexedColors.DARK_GREEN,true),style(wb,true,12,IndexedColors.DARK_RED,IndexedColors.LIGHT_YELLOW,true),style(wb,true,10,IndexedColors.DARK_GREEN,IndexedColors.LIGHT_GREEN,true),style(wb,true,10,IndexedColors.WHITE,IndexedColors.DARK_GREEN,true),style(wb,false,10,IndexedColors.BLACK,IndexedColors.WHITE,true));}
        private static CellStyle style(XSSFWorkbook wb,boolean bold,int size,IndexedColors color,IndexedColors fill,boolean border){CellStyle style=wb.createCellStyle();Font font=wb.createFont();font.setFontName("Arial");font.setFontHeightInPoints((short)size);font.setBold(bold);font.setColor(color.getIndex());style.setFont(font);style.setWrapText(true);style.setVerticalAlignment(VerticalAlignment.CENTER);style.setAlignment(bold?HorizontalAlignment.CENTER:HorizontalAlignment.LEFT);style.setFillForegroundColor(fill.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);if(border){style.setBorderTop(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);style.setBorderBottom(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);}return style;}
    }
}
