package com.devhub.batch.core.cli;

import com.devhub.batch.core.RetryPolicy;
import com.devhub.batch.core.Scheduler;
import com.devhub.batch.core.SchedulerBootstrap;
import com.devhub.batch.core.TaskJob;
import com.devhub.batch.core.config.BatchConfig;
import com.devhub.batch.core.crypto.AesGcm;
import com.devhub.batch.core.cron.CronCalculator;
import com.devhub.batch.core.migrate.DatabaseMigrator;
import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.TaskExecutionLog;
import com.devhub.batch.core.repository.JdbcTaskRepository;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.schedule.ScheduleValidator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * CLI dispatcher. Read/admin commands never start the scheduler engine —
 * only the daemon (no arguments) and the blocking engine do.
 *
 * Fix Y6: 'run' executes the task synchronously and waits for its real
 * completion instead of sleeping one second and shutting down.
 */
public class Cli {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BatchConfig config;

    public Cli(BatchConfig config) {
        this.config = config;
    }

    public int dispatch(String[] args) {
        if (args.length == 0) return daemon();
        String cmd = args[0];
        try {
            return switch (cmd) {
                case "daemon"              -> daemon();
                case "list", "ls"          -> list();
                case "add", "create"       -> add(args);
                case "add-once"            -> addOnce(args);
                case "run", "trigger"      -> run(args);
                case "pause"               -> pause(args);
                case "resume"              -> resume(args);
                case "stop"                -> stop(args);
                case "delete", "rm"        -> delete(args);
                case "logs"                -> logs(args);
                case "validate-cron"       -> validateCron(args);
                case "validate-schedule"   -> validateSchedule(args);
                case "status"              -> status();
                case "encrypt"             -> encrypt();
                case "migrate"             -> migrate();
                case "help", "-h", "--help"-> help();
                default -> {
                    System.err.println("Unknown command: " + cmd);
                    yield help();
                }
            };
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /* ---------- Engine commands ---------- */

    private int daemon() {
        config.printSummary();
        var ds = DataSourceFactory.create(config);
        Scheduler scheduler = SchedulerBootstrap.start(ds, config);
        System.out.println("Scheduler started (worker=" + scheduler.getWorkerId() + "). Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            SchedulerBootstrap.shutdown();
            ds.close();
        }));

        // Block until SIGINT/SIGTERM (fix Y6: the daemon never exits on its own).
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    /* ---------- DB-only commands (never start the engine) ---------- */

    private int list() {
        TaskRepository repo = newRepo();
        List<ScheduledTask> tasks = repo.findAll();
        if (tasks.isEmpty()) {
            System.out.println("No tasks. Use: add <name> <beanClass> <cron>");
            return 0;
        }
        System.out.printf("%-4s %-25s %-45s %-20s %-12s %-19s %-19s%n",
                "ID", "NAME", "BEAN_CLASS", "CRON", "STATUS", "LAST_RUN", "NEXT_RUN");
        for (ScheduledTask t : tasks) {
            System.out.printf("%-4d %-25s %-45s %-20s %-12s %-19s %-19s%n",
                    t.getId(),
                    truncate(t.getTaskName(), 25),
                    truncate(t.getBeanClass(), 45),
                    t.getCronExpression() != null ? truncate(t.getCronExpression(), 20) : "-",
                    t.getStatus(),
                    t.getLastRunAt() != null ? t.getLastRunAt().format(FMT) : "-",
                    t.getNextRunAt() != null ? t.getNextRunAt().format(FMT) : "-");
        }
        System.out.println("Total: " + tasks.size() + " task(s)");
        return 0;
    }

    private int add(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: add <name> <beanClass> <cron> [priority] [description...]");
            return 1;
        }
        ScheduledTask t = new ScheduledTask();
        t.setTaskName(args[1]);
        t.setBeanClass(args[2]);
        t.setScheduleType(ScheduleValidator.RECURRING);
        t.setCronExpression(args[3]);
        t.setPriority(args.length > 4 ? Integer.parseInt(args[4]) : 0);
        t.setDescription(joinTail(args, 5));
        t.setStatus("SCHEDULED");
        return saveAndPrint(t, "Task created");
    }

    private int addOnce(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: add-once <name> <beanClass> <yyyy-MM-dd HH:mm[:ss]> [priority] [description...]");
            return 1;
        }
        ScheduledTask t = new ScheduledTask();
        t.setTaskName(args[1]);
        t.setBeanClass(args[2]);
        t.setScheduleType(ScheduleValidator.ONCE);
        t.setScheduledTime(parseDateTime(args[3]));
        t.setPriority(args.length > 4 ? Integer.parseInt(args[4]) : 0);
        t.setDescription(joinTail(args, 5));
        t.setStatus("SCHEDULED");
        return saveAndPrint(t, "One-time task created");
    }

    private int saveAndPrint(ScheduledTask t, String label) {
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            t = repo.save(t);
            System.out.println(label + ":");
            System.out.println("   ID       : " + t.getId());
            System.out.println("   Name     : " + t.getTaskName());
            System.out.println("   BeanClass: " + t.getBeanClass());
            System.out.println("   Cron     : " + (t.getCronExpression() != null ? t.getCronExpression() : "-"));
            System.out.println("   NextRun  : " + (t.getNextRunAt() != null ? t.getNextRunAt().format(FMT) : "-"));
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Rejected: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int run(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: run <id>");
            return 1;
        }
        long id = Long.parseLong(args[1]);
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            ScheduledTask task = repo.findById(id);
            if (task == null) {
                System.err.println("Task " + id + " not found");
                return 1;
            }
            // Fix Y6: execute synchronously and wait for the real outcome.
            new TaskJob(repo, id, config.getWorkerInfo().getWorkerId(), null, null,
                    new RetryPolicy(config.getMaxAttempt(), config.getBackoffBaseSeconds())).run();
            List<TaskExecutionLog> logs = repo.findLogsByTaskId(id, 1);
            if (!logs.isEmpty()) {
                TaskExecutionLog l = logs.get(0);
                System.out.println("Result : " + l.getStatus()
                        + (l.getDurationMs() != null ? " (" + l.getDurationMs() + " ms)" : ""));
                if (l.getResult() != null && !l.getResult().isBlank()) System.out.println(l.getResult());
                if (l.getErrorMessage() != null && !l.getErrorMessage().isBlank()) {
                    System.out.println("Error  : " + l.getErrorMessage());
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int pause(String[] args) {
        return simpleStatusCommand(args, "pause", "PAUSED");
    }

    private int resume(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: resume <id>");
            return 1;
        }
        long id = Long.parseLong(args[1]);
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            ScheduledTask task = repo.findById(id);
            if (task == null) {
                System.err.println("Task " + id + " not found");
                return 1;
            }
            repo.updateStatus(id, "SCHEDULED");
            if (task.getNextRunAt() == null) {
                ScheduledTask refreshed = repo.findById(id);
                repo.updateNextRunAt(id, com.devhub.batch.core.schedule.SchedulePlanner.initialNextRun(refreshed, LocalDateTime.now()));
            }
            System.out.println("Task " + id + " resumed");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int stop(String[] args) {
        return simpleStatusCommand(args, "stop", "STOPPED");
    }

    private int simpleStatusCommand(String[] args, String label, String status) {
        if (args.length < 2) {
            System.err.println("Usage: " + label + " <id>");
            return 1;
        }
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            repo.updateStatus(Long.parseLong(args[1]), status);
            System.out.println("Task " + args[1] + " " + status.toLowerCase());
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int delete(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: delete <id>");
            return 1;
        }
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            repo.delete(Long.parseLong(args[1]));
            System.out.println("Task " + args[1] + " deleted (execution logs cascade)");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int logs(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: logs <id> [limit]");
            return 1;
        }
        long id = Long.parseLong(args[1]);
        int limit = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            ScheduledTask t = repo.findById(id);
            if (t == null) {
                System.err.println("Task " + id + " not found");
                return 1;
            }
            System.out.println("Task: " + t.getTaskName() + " (" + t.getBeanClass() + ")");
            List<TaskExecutionLog> logs = repo.findLogsByTaskId(id, limit);
            if (logs.isEmpty()) {
                System.out.println("No logs yet.");
                return 0;
            }
            for (TaskExecutionLog l : logs) {
                System.out.println("[" + (l.getExecutedAt() != null ? l.getExecutedAt().format(FMT) : "-") + "] "
                        + l.getStatus()
                        + (l.getDurationMs() != null ? " (" + l.getDurationMs() + " ms)" : ""));
                if (l.getErrorMessage() != null && !l.getErrorMessage().isBlank()) {
                    System.out.println("   Error: " + l.getErrorMessage());
                }
                if (l.getResult() != null && !l.getResult().isBlank() && !"SUCCESS".equals(l.getStatus())) {
                    System.out.println("   Result: " + truncate(l.getResult(), 200));
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int validateCron(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: validate-cron <expression>");
            return 1;
        }
        String expr = args[1];
        String error = CronCalculator.validate(expr);
        if (error == null) {
            LocalDateTime next = CronCalculator.nextExecution(expr, CronCalculator.zoneOf(args.length > 2 ? args[2] : null), LocalDateTime.now());
            System.out.println("Valid cron: " + expr);
            System.out.println("   Next execution: " + (next != null ? next.format(FMT) : "(never)"));
            return 0;
        }
        System.out.println("Invalid cron: " + expr);
        System.out.println("   Error: " + error);
        return 1;
    }

    private int validateSchedule(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: validate-schedule <ONCE|RECURRING|CRON> <value>");
            return 1;
        }
        String type = args[1];
        try {
            if ("ONCE".equalsIgnoreCase(type)) {
                ScheduleValidator.Result r = ScheduleValidator.validate(type, null, parseDateTime(args[2]), null, null, "");
                System.out.println(r.valid() ? "Valid ONCE schedule: " + args[2] : "Invalid: " + r.message());
                return r.valid() ? 0 : 1;
            }
            ScheduleValidator.Result r = ScheduleValidator.validate(type, args[2], null, null, null, "");
            if (!r.valid()) {
                System.out.println("Invalid: " + r.message());
                return 1;
            }
            LocalDateTime next = CronCalculator.nextExecution(args[2], CronCalculator.zoneOf(null), LocalDateTime.now());
            System.out.println("Valid " + r.scheduleType() + " schedule: " + args[2]);
            System.out.println("   Next execution: " + (next == null ? "(never)" : next.format(FMT)));
            return 0;
        } catch (Exception e) {
            System.out.println("Invalid schedule: " + e.getMessage());
            return 1;
        }
    }

    private int status() {
        try (var ds = DataSourceFactory.create(config)) {
            TaskRepository repo = new JdbcTaskRepository(ds);
            System.out.println("Tasks by status:");
            for (String status : List.of("SCHEDULED", "RUNNING", "PAUSED", "STOPPED", "ERROR", "TIMEOUT", "COMPLETED")) {
                List<ScheduledTask> tasks = repo.findByStatus(status);
                if (!tasks.isEmpty()) {
                    System.out.printf("  %-12s : %d%n", status, tasks.size());
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /** Reads a secret from stdin and prints enc:<hex> (AES-256-GCM, hex only). */
    private int encrypt() {
        try {
            String masterKey = System.getenv("BATCH_MASTER_KEY");
            if (masterKey == null || masterKey.isBlank()) {
                System.err.println("Error: BATCH_MASTER_KEY is not set (64 hex characters = 32 bytes)");
                return 1;
            }
            String plain;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                plain = reader.readLine();
            }
            if (plain == null || plain.isEmpty()) {
                System.err.println("Error: empty input");
                return 1;
            }
            System.out.println("enc:" + AesGcm.encryptToHex(plain, AesGcm.keyFromHex(masterKey)));
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private int migrate() {
        try (var ds = DataSourceFactory.create(config)) {
            List<String> applied = new DatabaseMigrator(ds).migrate();
            if (applied.isEmpty()) {
                System.out.println("Schema is up to date.");
            } else {
                applied.forEach(s -> System.out.println("Applied: " + s));
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /* ---------- Helpers ---------- */

    private TaskRepository newRepo() {
        return new JdbcTaskRepository(DataSourceFactory.create(config));
    }

    private String joinTail(String[] args, int from) {
        StringBuilder desc = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) desc.append(' ');
            desc.append(args[i]);
        }
        return desc.toString();
    }

    private LocalDateTime parseDateTime(String value) {
        String normalized = value.replace('T', ' ');
        if (normalized.length() == 16) normalized += ":00";
        return LocalDateTime.parse(normalized, FMT);
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private int help() {
        System.out.print("""
                DevHub Batch Core — standalone scheduler CLI

                Usage:
                  java -jar devhub-batch-core.jar                  Run as daemon (engine starts, blocks forever)
                  java -jar devhub-batch-core.jar <command> [args]

                Engine commands:
                  daemon                                Start the scheduler engine (default)
                  migrate                               Run versioned SQL migrations and exit

                DB-only commands (never start the engine):
                  list                                  List all tasks
                  add <name> <beanClass> <cron> [pri] [desc...]
                                                        Add RECURRING task (6-field cron)
                  add-once <name> <beanClass> <date-time> [pri] [desc...]
                                                        Add ONCE task
                  run <id>                              Execute the task synchronously and print the outcome
                  pause <id>                            Pause task
                  resume <id>                           Resume task
                  stop <id>                             Stop task permanently
                  delete <id>                           Delete task (logs cascade)
                  logs <id> [limit=20]                  Show execution logs
                  status                                Count tasks by status
                  validate-cron <expr> [timezone]       Validate cron expression
                  validate-schedule <ONCE|RECURRING|CRON> <value>
                                                        Validate complete schedule
                  encrypt                               Read a secret from stdin, print enc:<hex> (BATCH_MASTER_KEY required)

                Configuration (priority: ENV > -Dconfig.file > classpath config.properties > default):
                  DB_URL / DB_USERNAME / BATCH_DB_PASSWORD
                  BATCH_THREAD_POOL_SIZE, BATCH_LOG_RETENTION_DAYS, ...

                Examples:
                  java -jar devhub-batch-core.jar list
                  java -jar devhub-batch-core.jar add Demo com.example.DemoBatch "0 */2 * * * *" 1 "runs every 2 minutes"
                  java -jar devhub-batch-core.jar validate-schedule RECURRING "0 0 6 * * *"
                  echo -n 's3cret' | java -jar devhub-batch-core.jar encrypt
                """);
        return 0;
    }
}
