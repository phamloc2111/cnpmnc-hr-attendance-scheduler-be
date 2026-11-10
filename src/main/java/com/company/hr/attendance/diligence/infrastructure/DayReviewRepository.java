package com.company.hr.attendance.diligence.infrastructure;
import com.company.hr.attendance.diligence.domain.DayReview;
import com.company.hr.attendance.diligence.domain.DayReview.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DayReviewRepository {
    public record Key(UUID employee,LocalDate date) {}
    public record Audit(long revision,UUID actorId,Instant at,String snapshot) {}
    private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
    public DayReviewRepository(JdbcTemplate jdbc,NamedParameterJdbcTemplate named){this.jdbc=jdbc;this.named=named;}
    private static final RowMapper<DayReview> ROW=(rs,row)->new DayReview(rs.getObject("employee_id",UUID.class),rs.getObject("work_date",LocalDate.class),
        Absence.valueOf(rs.getString("absence")),rs.getBoolean("early_leave_approved"),Missing.valueOf(rs.getString("missing")),rs.getString("note"),rs.getLong("revision"),rs.getObject("reviewed_by",UUID.class),rs.getTimestamp("reviewed_at").toInstant());
    public Map<Key,DayReview> find(Collection<UUID> employees,LocalDate from,LocalDate to){
        if(employees.isEmpty())return Map.of();var result=new HashMap<Key,DayReview>();
        named.query("SELECT * FROM diligence_day_reviews WHERE employee_id IN (:ids) AND work_date BETWEEN :from AND :to",
            Map.of("ids",employees,"from",java.sql.Date.valueOf(from),"to",java.sql.Date.valueOf(to)),ROW)
            .forEach(review->result.put(new Key(review.employeeId(),review.workDate()),review));return result;
    }
    public DayReview find(UUID employee,LocalDate date){return jdbc.query("SELECT * FROM diligence_day_reviews WHERE employee_id=? AND work_date=?",ROW,employee,java.sql.Date.valueOf(date)).stream().findFirst().orElse(DayReview.empty(employee,date));}
    public boolean save(DayReview review,long expected){
        var args=new Object[]{review.absence().name(),review.earlyLeaveApproved(),review.missing().name(),review.note(),review.revision(),review.reviewedBy(),OffsetDateTime.ofInstant(review.reviewedAt(),ZoneOffset.UTC),review.employeeId(),java.sql.Date.valueOf(review.workDate())};
        if(expected==0)return jdbc.update("INSERT INTO diligence_day_reviews(absence,early_leave_approved,missing,note,revision,reviewed_by,reviewed_at,employee_id,work_date) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(employee_id,work_date) DO NOTHING",args)==1;
        var parameters=Arrays.copyOf(args,10);parameters[9]=expected;
        return jdbc.update("UPDATE diligence_day_reviews SET absence=?,early_leave_approved=?,missing=?,note=?,revision=?,reviewed_by=?,reviewed_at=? WHERE employee_id=? AND work_date=? AND revision=?",parameters)==1;
    }
    public void audit(DayReview review,String snapshot){jdbc.update("INSERT INTO diligence_review_audit(id,employee_id,work_date,revision,reviewed_by,reviewed_at,snapshot) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),review.employeeId(),java.sql.Date.valueOf(review.workDate()),review.revision(),review.reviewedBy(),OffsetDateTime.ofInstant(review.reviewedAt(),ZoneOffset.UTC),snapshot);}
    public List<Audit> history(UUID employee,LocalDate date){return jdbc.query("SELECT revision,reviewed_by,reviewed_at,snapshot FROM diligence_review_audit WHERE employee_id=? AND work_date=? ORDER BY revision DESC LIMIT 20",
        (rs,row)->new Audit(rs.getLong(1),rs.getObject(2,UUID.class),rs.getTimestamp(3).toInstant(),rs.getString(4)),employee,java.sql.Date.valueOf(date));}
}
