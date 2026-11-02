package com.flowlane.reporting;

import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectRepository;
import com.flowlane.task.TaskRepository;
import com.flowlane.workflow.StatusCategory;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    public record ProjectProgress(UUID id, String key, String name, String color, long total, long done, long overdue) {}

    public record Workload(UUID userId, String displayName, long openTasks) {}

    public record WeekBucket(LocalDate weekStart, long completed) {}

    public record Overview(Map<StatusCategory, Long> byCategory, long overdue, long completedThisWeek, long completedLastWeek,
                           List<ProjectProgress> projects, List<Workload> workload, List<WeekBucket> completedPerWeek) {}

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AccessGuard guard;
    private final Clock clock;

    public ReportService(TaskRepository tasks, ProjectRepository projects, UserRepository users, AccessGuard guard, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID workspaceId, int weeks) {
        int w = Math.min(Math.max(weeks, 1), 26);
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, CurrentUser.id());
        Map<StatusCategory, Long> byCategory = new EnumMap<>(StatusCategory.class);
        for (StatusCategory c : StatusCategory.values()) byCategory.put(c, 0L);
        LocalDate today = LocalDate.now(clock);
        List<ProjectProgress> progress = new ArrayList<>();
        long overdue = 0;
        if (!visible.isEmpty()) {
            for (Object[] row : tasks.countByCategory(visible)) {
                byCategory.put((StatusCategory) row[0], (Long) row[1]);
            }
            for (Project p : projects.findAllById(visible)) {
                if (p.getArchivedAt() != null) continue;
                long od = tasks.countOverdueByProject(p.getId(), today);
                overdue += od;
                progress.add(new ProjectProgress(p.getId(), p.getProjectKey(), p.getName(), p.getColor(),
                        tasks.countByProject(p.getId()), tasks.countDoneByProject(p.getId()), od));
            }
        }
        Map<UUID, Long> open = tasks.countOpenByAssignee(workspaceId).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        Map<UUID, User> u = users.findAllById(open.keySet()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Workload> workload = open.entrySet().stream().filter(e -> u.containsKey(e.getKey()))
                .map(e -> new Workload(e.getKey(), u.get(e.getKey()).getDisplayName(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.openTasks(), a.openTasks())).toList();

        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(w - 1L);
        long[] buckets = new long[w];
        if (!visible.isEmpty()) {
            for (Instant done : tasks.completedSince(visible, first.atStartOfDay().toInstant(ZoneOffset.UTC))) {
                LocalDate d = done.atZone(ZoneOffset.UTC).toLocalDate();
                int idx = (int) ((d.toEpochDay() - first.toEpochDay()) / 7);
                if (idx >= 0 && idx < w) buckets[idx]++;
            }
        }
        List<WeekBucket> perWeek = new ArrayList<>();
        for (int i = 0; i < w; i++) perWeek.add(new WeekBucket(first.plusWeeks(i), buckets[i]));
        long thisCount = buckets[w - 1];
        long lastCount = w > 1 ? buckets[w - 2] : 0;
        return new Overview(byCategory, overdue, thisCount, lastCount, progress, workload, perWeek);
    }

    /** CSV of the overview for export. Values are quoted so names with commas stay intact. */
    public String overviewCsv(UUID workspaceId, int weeks) {
        Overview o = overview(workspaceId, weeks);
        StringBuilder sb = new StringBuilder("section,name,value\n");
        o.byCategory().forEach((k, v) -> sb.append("category,").append(k).append(',').append(v).append('\n'));
        sb.append("summary,overdue,").append(o.overdue()).append('\n');
        for (ProjectProgress p : o.projects()) {
            sb.append("project_total,\"").append(p.name().replace("\"", "\"\"")).append("\",").append(p.total()).append('\n');
            sb.append("project_done,\"").append(p.name().replace("\"", "\"\"")).append("\",").append(p.done()).append('\n');
        }
        for (Workload wl : o.workload()) {
            sb.append("workload,\"").append(wl.displayName().replace("\"", "\"\"")).append("\",").append(wl.openTasks()).append('\n');
        }
        for (WeekBucket b : o.completedPerWeek()) {
            sb.append("completed_week,").append(b.weekStart()).append(',').append(b.completed()).append('\n');
        }
        return sb.toString();
    }
}
