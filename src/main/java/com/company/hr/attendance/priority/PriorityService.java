package com.company.hr.attendance.priority;

import com.company.hr.attendance.diligence.DiligenceApi;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.organization.PositionsApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PriorityService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public record Weights(int punctuality,int hourBalance,int tenure,int positionMatch,int managerRating) {
        public int total() {return punctuality+hourBalance+tenure+positionMatch+managerRating;}
        public static Weights defaults() {return new Weights(35,25,15,15,10);}
    }
    public record Score(UUID employeeId,String employeeName,int score,int punctuality,int hourBalance,int tenure,
        int positionMatch,int managerRating,int absencePenalty,long previousWeekMinutes,long currentWeekMinutes,int unexcusedAbsences) {}
    public record Team(UUID managerId,LocalDate weekStart,UUID positionId,Weights weights,List<Score> items) {}
    public record Own(LocalDate weekStart,int score) {}
    private final IdentityApi identity;private final WorkScheduleApi schedules;private final DiligenceApi diligence;
    private final PositionsApi positions;private final JdbcTemplate jdbc;private final Clock clock;
    public PriorityService(IdentityApi identity,WorkScheduleApi schedules,DiligenceApi diligence,PositionsApi positions,JdbcTemplate jdbc,@Qualifier("diligenceClock") Clock clock) {
        this.identity=identity;this.schedules=schedules;this.diligence=diligence;this.positions=positions;this.jdbc=jdbc;this.clock=clock;
    }
    public Team team(UUID actorId,UUID requestedManager,LocalDate requestedWeek,UUID positionId) {
        var manager=manager(actorId,requestedManager);var week=week(requestedWeek);
        String positionName=positionId==null?null:positions.select(positionId,null).name();
        var ids=identity.managedEmployeeIds(manager);
        var profiles=identity.scoreProfiles(ids);
        var active=ids.stream().map(profiles::get).filter(Objects::nonNull).filter(profile->!profile.locked()).toList();
        var activeIds=active.stream().map(IdentityApi.ScoreProfile::id).toList();
        var weights=weights(manager);
        var signals=diligence.signals(activeIds,week.minusWeeks(4),week.minusDays(1)).stream()
            .collect(Collectors.groupingBy(DiligenceApi.Signal::employeeId));
        var prior=schedules.employeeShifts(activeIds,week.minusWeeks(1),week.minusDays(1));
        var current=schedules.employeeShifts(activeIds,week,week.plusDays(6));
        var minutes=new HashMap<UUID,Long>();
        for(var shift:prior) minutes.merge(shift.employeeId(),Math.max(0,Duration.between(shift.startsAt(),shift.endsAt()).toMinutes()),Long::sum);
        var currentMinutes=new HashMap<UUID,Long>();
        for(var shift:current) currentMinutes.merge(shift.employeeId(),Math.max(0,Duration.between(shift.startsAt(),shift.endsAt()).toMinutes()),Long::sum);
        long maxMinutes=activeIds.stream().mapToLong(id->minutes.getOrDefault(id,0L)).max().orElse(0);
        long minMinutes=activeIds.stream().mapToLong(id->minutes.getOrDefault(id,0L)).min().orElse(0);
        var ratings=ratings(manager);
        var rows=new ArrayList<Score>();
        for(var profile:active){
            var recent=signals.getOrDefault(profile.id(),List.of());
            int eligible=recent.stream().mapToInt(DiligenceApi.Signal::eligibleShifts).sum();
            int timely=recent.stream().mapToInt(DiligenceApi.Signal::onTimeShifts).sum();
            int punctuality=eligible==0?50:(int)Math.round(100d*timely/eligible);
            long assigned=minutes.getOrDefault(profile.id(),0L);
            int balance=maxMinutes==minMinutes?100:(int)Math.round(100d*(maxMinutes-assigned)/(maxMinutes-minMinutes));
            long tenureMonths=Math.max(0,ChronoUnit.MONTHS.between(YearMonth.from(profile.createdAt().atZone(ZONE)),YearMonth.from(week)));
            int tenure=(int)Math.round(100d*Math.min(24,tenureMonths)/24);
            int match=positionId==null?50:positionId.equals(profile.positionId())?100:
                profile.skills().stream().anyMatch(skill->skill.equalsIgnoreCase(positionName))?75:0;
            int rating=ratings.getOrDefault(profile.id(),3)*20;
            int absences=(int)recent.stream().filter(DiligenceApi.Signal::unexcusedAbsent).count();
            int penalty=Math.min(20,absences*5);
            int raw=(int)Math.round((punctuality*weights.punctuality()+balance*weights.hourBalance()+tenure*weights.tenure()
                +match*weights.positionMatch()+rating*weights.managerRating())/100d);
            rows.add(new Score(profile.id(),profile.name(),Math.max(0,raw-penalty),punctuality,balance,tenure,match,
                rating,penalty,assigned,currentMinutes.getOrDefault(profile.id(),0L),absences));
        }
        rows.sort(Comparator.comparingInt(Score::score).reversed().thenComparingLong(Score::currentWeekMinutes).thenComparing(Score::employeeName));
        return new Team(manager,week,positionId,weights,List.copyOf(rows));
    }
    public Own own(UUID actorId,LocalDate requestedWeek) {
        var actor=identity.person(actorId);
        if(actor.locked()||!actor.role().equals("EMPLOYEE")||actor.managerId()==null)throw new AccessDeniedException("Employee unavailable");
        var result=team(actor.managerId(),actor.managerId(),requestedWeek,null);
        return new Own(result.weekStart(),result.items().stream().filter(item->item.employeeId().equals(actorId)).findFirst()
            .orElseThrow(()->new AccessDeniedException("Employee unavailable")).score());
    }
    public Weights getWeights(UUID actorId,UUID requestedManager){return weights(manager(actorId,requestedManager));}
    public Weights saveWeights(UUID actorId,UUID requestedManager,Weights value){
        var manager=manager(actorId,requestedManager);validate(value);
        jdbc.update("INSERT INTO priority_weights(manager_id,punctuality,hour_balance,tenure,position_match,manager_rating,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(manager_id) DO UPDATE SET punctuality=EXCLUDED.punctuality,hour_balance=EXCLUDED.hour_balance,tenure=EXCLUDED.tenure,position_match=EXCLUDED.position_match,manager_rating=EXCLUDED.manager_rating,updated_by=EXCLUDED.updated_by,updated_at=EXCLUDED.updated_at",
            manager,value.punctuality(),value.hourBalance(),value.tenure(),value.positionMatch(),value.managerRating(),actorId,java.sql.Timestamp.from(clock.instant()));
        return value;
    }
    public void rate(UUID actorId,UUID requestedManager,UUID employeeId,int rating){
        var manager=manager(actorId,requestedManager);
        if(rating<1||rating>5)throw new PriorityException(HttpStatus.BAD_REQUEST,"INVALID_PRIORITY_RATING","Rating must be between 1 and 5.");
        var employee=identity.person(employeeId);
        if(employee.locked()||!employee.role().equals("EMPLOYEE")||!manager.equals(employee.managerId()))throw new AccessDeniedException("Employee unavailable");
        jdbc.update("INSERT INTO priority_ratings(manager_id,employee_id,rating,updated_by,updated_at) VALUES (?,?,?,?,?) ON CONFLICT(manager_id,employee_id) DO UPDATE SET rating=EXCLUDED.rating,updated_by=EXCLUDED.updated_by,updated_at=EXCLUDED.updated_at",
            manager,employeeId,rating,actorId,java.sql.Timestamp.from(clock.instant()));
    }
    public Map<UUID,Integer> ratingsFor(UUID actorId,UUID requestedManager){return ratings(manager(actorId,requestedManager));}
    private Map<UUID,Integer> ratings(UUID manager){
        var result=new HashMap<UUID,Integer>();
        jdbc.query("SELECT employee_id,rating FROM priority_ratings WHERE manager_id=?",rs->{result.put(rs.getObject(1,UUID.class),rs.getInt(2));},manager);
        return result;
    }
    private Weights weights(UUID manager){
        var rows=jdbc.query("SELECT punctuality,hour_balance,tenure,position_match,manager_rating FROM priority_weights WHERE manager_id=?",
            (rs,n)->new Weights(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)),manager);
        return rows.isEmpty()?Weights.defaults():rows.getFirst();
    }
    private UUID manager(UUID actorId,UUID requested){
        var actor=identity.person(actorId);
        if(actor.locked()||!Set.of("MANAGER","ADMIN").contains(actor.role()))throw new AccessDeniedException("Management access required");
        UUID id=actor.role().equals("MANAGER")?actorId:requested;
        if(id==null)throw new PriorityException(HttpStatus.BAD_REQUEST,"MANAGER_REQUIRED","Select a manager.");
        if(actor.role().equals("MANAGER")&&requested!=null&&!requested.equals(actorId))throw new AccessDeniedException("Manager unavailable");
        var selected=identity.person(id);
        if(selected.locked()||!selected.role().equals("MANAGER"))throw new AccessDeniedException("Manager unavailable");
        return id;
    }
    private LocalDate week(LocalDate requested){
        var today=clock.instant().atZone(ZONE).toLocalDate();
        var date=requested==null?today:requested;
        if(date.isBefore(LocalDate.of(1900,1,1))||date.isAfter(today.plusWeeks(12)))
            throw new PriorityException(HttpStatus.BAD_REQUEST,"INVALID_PRIORITY_WEEK","Week is out of range.");
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    private void validate(Weights value){
        if(value==null||value.total()!=100||IntStream.of(value.punctuality(),value.hourBalance(),value.tenure(),value.positionMatch(),value.managerRating()).anyMatch(v->v<0||v>100))
            throw new PriorityException(HttpStatus.BAD_REQUEST,"INVALID_PRIORITY_WEIGHTS","Weights must be 0–100 and sum to 100.");
    }
}
