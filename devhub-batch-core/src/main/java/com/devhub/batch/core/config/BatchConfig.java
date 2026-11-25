package com.devhub.batch.core.config;

import com.devhub.batch.core.crypto.AesGcm;
import com.devhub.batch.core.crypto.HexCodec;
import com.devhub.batch.core.model.WorkerInfo;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layered configuration: ENV var > -Dconfig.file > classpath config.properties > default.
 *
 * Secrets: {@code ${VAR}} placeholders are interpolated from the environment;
 * {@code enc:<hex>} values are decrypted with AES-256-GCM using the
 * {@code BATCH_MASTER_KEY} environment variable (64 hex chars). Only hex is
 * used — the banned Base64 decoder never appears in this pipeline.
 */
public final class BatchConfig {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final Properties props;
    private final Map<String, String> env;

    public BatchConfig(Properties props, Map<String, String> env) {
        this.props = props == null ? new Properties() : props;
        this.env = env == null ? Map.of() : env;
    }

    public static BatchConfig load() {
        Properties fileProps = new Properties();
        String path = System.getProperty("config.file");
        boolean loaded = false;
        if (path != null && !path.isBlank()) {
            try (InputStream is = new FileInputStream(path)) {
                fileProps.load(is);
                loaded = true;
            } catch (Exception e) {
                System.err.println("[Config] Cannot load -Dconfig.file=" + path + ": " + e.getMessage());
            }
        }
        if (!loaded) {
            try (InputStream is = BatchConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is != null) fileProps.load(is);
            } catch (Exception e) {
                System.err.println("[Config] Cannot load classpath config.properties: " + e.getMessage());
            }
        }
        return new BatchConfig(fileProps, System.getenv());
    }

    /* ---------- Lookups ---------- */

    public String get(String envKey, String propKey, String def) {
        String envValue = env.get(envKey);
        if (envValue != null && !envValue.isBlank()) return envValue.trim();
        String prop = props.getProperty(propKey);
        if (prop != null) {
            String interpolated = interpolate(prop);
            if (interpolated != null && !interpolated.isBlank()) return interpolated.trim();
        }
        return def;
    }

    public int getInt(String envKey, String propKey, String def) {
        try {
            return Integer.parseInt(get(envKey, propKey, def));
        } catch (NumberFormatException e) {
            return Integer.parseInt(def);
        }
    }

    public boolean getBool(String envKey, String propKey, String def) {
        String v = get(envKey, propKey, def).toLowerCase();
        return "true".equals(v) || "1".equals(v);
    }

    /** Replaces ${VAR} with the environment value (missing VAR -> empty). */
    private String interpolate(String raw) {
        if (raw == null || !raw.contains("${")) return raw;
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = env.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /* ---------- Typed accessors ---------- */

    public String getDbUrl() { return get("DB_URL", "db.url", "jdbc:mysql://localhost:3306/devhub_rbac"); }
    public String getDbUsername() { return get("DB_USERNAME", "db.username", "root"); }

    public String getDbPassword() {
        String raw = get("BATCH_DB_PASSWORD", "db.password", "");
        if (raw.startsWith("enc:")) {
            String masterKey = env.get("BATCH_MASTER_KEY");
            if (masterKey == null || masterKey.isBlank()) {
                throw new IllegalStateException("enc: secret found but BATCH_MASTER_KEY is not set");
            }
            return AesGcm.decryptFromHex(raw.substring(4), AesGcm.keyFromHex(masterKey));
        }
        return raw;
    }

    public int getDbPoolSize() { return getInt("DB_POOL_SIZE", "db.pool.size", "5"); }
    public int getThreadPoolSize() { return getInt("BATCH_THREAD_POOL_SIZE", "batch.thread-pool-size", "5"); }
    public int getPollIntervalMs() { return getInt("BATCH_POLL_INTERVAL_MS", "batch.poll-interval-ms", "1000"); }
    public int getHeartbeatIntervalMs() { return getInt("BATCH_HEARTBEAT_INTERVAL_MS", "batch.heartbeat-interval-ms", "5000"); }
    public int getReconcileIntervalMs() { return getInt("BATCH_RECONCILE_INTERVAL_MS", "batch.reconcile-interval-ms", "5000"); }
    public int getStaleWorkerThresholdMs() { return getInt("BATCH_STALE_WORKER_MS", "batch.stale-worker-threshold-ms", "30000"); }
    public int getMisfireGraceMs() { return getInt("BATCH_MISFIRE_GRACE_MS", "batch.misfire-grace-ms", "30000"); }
    public int getLogRetentionDays() { return getInt("BATCH_LOG_RETENTION_DAYS", "batch.log-retention-days", "30"); }
    public int getMaxAttempt() { return getInt("BATCH_MAX_ATTEMPT", "batch.max-attempt", "3"); }
    public int getBackoffBaseSeconds() { return getInt("BATCH_BACKOFF_BASE_SECONDS", "batch.backoff-base-seconds", "60"); }
    public boolean isCleanupOnStartup() { return getBool("BATCH_CLEANUP_ON_STARTUP", "batch.cleanup-on-startup", "true"); }
    public boolean isMigrateOnStartup() { return getBool("BATCH_MIGRATE_ON_STARTUP", "batch.migrate-on-startup", "true"); }
    public String getLeaderLockName() { return get("BATCH_LEADER_LOCK_NAME", "batch.leader-lock-name", "devhub-batch-core-leader"); }

    public WorkerInfo getWorkerInfo() {
        return WorkerInfo.detect(
                get("WORKER_ID", "worker.id", ""),
                get("WORKER_OWNER_ID", "worker.owner-id", "unknown"),
                get("WORKER_OWNER_NAME", "worker.owner-name", "unknown"),
                get("WORKER_OWNER_EMAIL", "worker.owner-email", "unknown"),
                get("WORKER_MACHINE_NAME", "worker.machine-name", ""),
                get("WORKER_PRIVATE_IP", "worker.private-ip", ""),
                get("WORKER_PUBLIC_IP", "worker.public-ip", ""),
                get("WORKER_MAC_ADDRESS", "worker.mac-address", ""),
                get("WORKER_APP_VERSION", "worker.app-version", "devhub-batch-core"),
                get("WORKER_ENVIRONMENT", "worker.environment", "default"),
                get("WORKER_LOCATION", "worker.location", "unknown"),
                get("WORKER_CONTAINER_ID", "worker.container-id", "unknown"));
    }

    /** Hex helper for the CLI encrypt command. */
    public static String masterKeyOrThrow(Map<String, String> environment) {
        String masterKey = environment.get("BATCH_MASTER_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("BATCH_MASTER_KEY is not set (64 hex characters = 32 bytes)");
        }
        return HexCodec.encode(AesGcm.keyFromHex(masterKey));
    }

    /** Logs a bootstrap summary without any secret values. */
    public void printSummary() {
        System.out.println("[Config] db_pool=" + getDbPoolSize()
                + " thread_pool=" + getThreadPoolSize()
                + " poll_ms=" + getPollIntervalMs()
                + " heartbeat_ms=" + getHeartbeatIntervalMs()
                + " reconcile_ms=" + getReconcileIntervalMs()
                + " log_retention_days=" + getLogRetentionDays()
                + " max_attempt=" + getMaxAttempt()
                + " backoff_base_s=" + getBackoffBaseSeconds()
                + " cleanup_on_startup=" + isCleanupOnStartup()
                + " migrate_on_startup=" + isMigrateOnStartup());
    }
}
