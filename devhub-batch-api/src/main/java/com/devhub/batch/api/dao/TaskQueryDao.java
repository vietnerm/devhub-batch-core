package com.devhub.batch.api.dao;

import com.devhub.batch.api.dto.TaskDtos.TaskDto;
import com.devhub.batch.api.dto.TaskDtos.TaskPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read model over SYS_SCHEDULED_TASKS — pagination/search/stats the scheduler
 * itself never needs. Strictly read-only; every write still goes through the
 * core TaskRepository so invariants (NEXT_RUN_AT single source of truth, Y5)
 * are preserved.
 */
@Repository
public class TaskQueryDao {

    private static final Set<String> SORTABLE = Set.of("ID", "TASK_NAME", "STATUS", "NEXT_RUN_AT", "LAST_RUN_AT", "PRIORITY");

    private final JdbcTemplate jdbc;

    public TaskQueryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TaskPage page(String status, String q, int page, int size, String sort, String dir) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            where.append(" AND STATUS = ?");
            args.add(status.trim().toUpperCase());
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            where.append(" AND (TASK_NAME LIKE ? OR BEAN_CLASS LIKE ? OR COALESCE(FUNCTION_CODE,'') LIKE ? OR COALESCE(DESCRIPTION,'') LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        String column = sort != null ? sort.trim().toUpperCase() : "ID";
        if (!SORTABLE.contains(column)) column = "ID";
        String direction = "desc".equalsIgnoreCase(dir) ? "DESC" : "ASC";

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SYS_SCHEDULED_TASKS" + where, Long.class, args.toArray());

        String sql = "SELECT * FROM SYS_SCHEDULED_TASKS" + where
                + " ORDER BY " + column + " " + direction + ", ID DESC"
                + " LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) Math.max(0, page - 1) * size);

        List<TaskDto> items = jdbc.query(sql, (rs, i) -> TaskDto.fromRow(rs), pageArgs.toArray());
        return new TaskPage(items, total, page, size);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT STATUS, COUNT(*) AS C FROM SYS_SCHEDULED_TASKS GROUP BY STATUS", rs -> {
            counts.put(rs.getString("STATUS"), rs.getLong("C"));
        });
        return counts;
    }
}
