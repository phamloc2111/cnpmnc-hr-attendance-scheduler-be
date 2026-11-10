package com.company.hr.attendance.attendance.infrastructure;

import com.company.hr.attendance.attendance.domain.AttendanceExplanation;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceExplanationRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public AttendanceExplanationRepository(JdbcTemplate jdbc,NamedParameterJdbcTemplate named){this.jdbc=jdbc;this.named=named;}
    private static final RowMapper<AttendanceExplanation> ROW=(rs,index)->new AttendanceExplanation(
        rs.getObject("id",UUID.class),rs.getObject("schedule_id",UUID.class),rs.getObject("employee_id",UUID.class),rs.getObject("work_date",LocalDate.class),
        Category.valueOf(rs.getString("category")),rs.getObject("attendance_record_id",UUID.class),rs.getString("reason"),rs.getTimestamp("submitted_at").toInstant(),
        Status.valueOf(rs.getString("status")),rs.getString("decision_reason"),rs.getBoolean("exempt_late_penalty"),rs.getObject("decided_by",UUID.class),
        rs.getTimestamp("decided_at")==null?null:rs.getTimestamp("decided_at").toInstant());
    private static OffsetDateTime time(Instant value){return OffsetDateTime.ofInstant(value,ZoneOffset.UTC);}
    public boolean insert(AttendanceExplanation row){return jdbc.update("INSERT INTO attendance_explanations(id,schedule_id,employee_id,work_date,category,attendance_record_id,reason,submitted_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        row.id(),row.scheduleId(),row.employeeId(),Date.valueOf(row.workDate()),row.category().name(),row.attendanceRecordId(),row.reason(),time(row.submittedAt()))==1;}
    public Optional<AttendanceExplanation> find(UUID id){return jdbc.query("SELECT * FROM attendance_explanations WHERE id=?",ROW,id).stream().findFirst();}
    public List<AttendanceExplanation> forSchedules(Collection<UUID> ids){if(ids.isEmpty())return List.of();return named.query("SELECT * FROM attendance_explanations WHERE schedule_id IN (:ids)",Map.of("ids",ids),ROW);}
    public List<AttendanceExplanation> forEmployee(UUID employee,LocalDate from,LocalDate to){return jdbc.query("SELECT * FROM attendance_explanations WHERE employee_id=? AND work_date BETWEEN ? AND ? ORDER BY submitted_at DESC,id",ROW,employee,Date.valueOf(from),Date.valueOf(to));}
    public List<AttendanceExplanation> forEmployees(Collection<UUID> employees,LocalDate from,LocalDate to,Status status,int limit,int offset){
        if(employees!=null && employees.isEmpty())return List.of();var args=new HashMap<String,Object>();if(employees!=null)args.put("ids",employees);args.put("from",Date.valueOf(from));args.put("to",Date.valueOf(to));args.put("limit",limit);args.put("offset",offset);
        var filter=status==null?"":" AND status=:status";if(status!=null)args.put("status",status.name());
        return named.query("SELECT * FROM attendance_explanations WHERE "+(employees==null?"":"employee_id IN (:ids) AND ")+"work_date BETWEEN :from AND :to"+filter+" ORDER BY submitted_at DESC,id LIMIT :limit OFFSET :offset",args,ROW);
    }
    public long countForEmployees(Collection<UUID> employees,LocalDate from,LocalDate to,Status status){
        if(employees!=null && employees.isEmpty())return 0;var args=new HashMap<String,Object>();if(employees!=null)args.put("ids",employees);args.put("from",Date.valueOf(from));args.put("to",Date.valueOf(to));
        var filter=status==null?"":" AND status=:status";if(status!=null)args.put("status",status.name());
        return named.queryForObject("SELECT count(*) FROM attendance_explanations WHERE "+(employees==null?"":"employee_id IN (:ids) AND ")+"work_date BETWEEN :from AND :to"+filter,args,Long.class);
    }
    public boolean decide(UUID id,UUID actor,Instant now,Status status,String reason,boolean exempt){return jdbc.update(
        "UPDATE attendance_explanations SET status=?,decision_reason=?,exempt_late_penalty=?,decided_by=?,decided_at=? WHERE id=? AND status='PENDING'",
        status.name(),reason,exempt,actor,time(now),id)==1;}
    public List<UUID> rejectedRecords(UUID employee,LocalDate from,LocalDate to){return jdbc.query("SELECT id FROM attendance_records WHERE employee_id=? AND work_date BETWEEN ? AND ? AND status='REJECTED' AND schedule_id IS NOT NULL ORDER BY recorded_at DESC",
        (rs,index)->rs.getObject(1,UUID.class),employee,Date.valueOf(from),Date.valueOf(to));}
}
