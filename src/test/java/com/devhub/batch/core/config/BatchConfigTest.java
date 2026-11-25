package com.devhub.batch.core.config;

import com.devhub.batch.core.crypto.AesGcm;
import com.devhub.batch.core.crypto.HexCodec;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchConfigTest {

    private static BatchConfig config(Properties props, Map<String, String> env) {
        return new BatchConfig(props, env);
    }

    @Test
    void defaults() {
        BatchConfig c = config(new Properties(), Map.of());
        assertEquals("jdbc:mysql://localhost:3306/devhub_rbac", c.getDbUrl());
        assertEquals("root", c.getDbUsername());
        assertEquals("", c.getDbPassword());
        assertEquals(5, c.getDbPoolSize());
        assertEquals(5, c.getThreadPoolSize());
        assertEquals(1000, c.getPollIntervalMs());
        assertEquals(5000, c.getHeartbeatIntervalMs());
        assertEquals(5000, c.getReconcileIntervalMs());
        assertEquals(30000, c.getStaleWorkerThresholdMs());
        assertEquals(30000, c.getMisfireGraceMs());
        assertEquals(30, c.getLogRetentionDays());
        assertTrue(c.isCleanupOnStartup());
        assertTrue(c.isMigrateOnStartup());
        assertEquals("devhub-batch-core-leader", c.getLeaderLockName());
    }

    @Test
    void envWinsOverProperties() {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:mysql://from-file/db");
        BatchConfig c = config(props, Map.of("DB_URL", "jdbc:mysql://from-env/db"));
        assertEquals("jdbc:mysql://from-env/db", c.getDbUrl());
    }

    @Test
    void placeholderInterpolationFromEnv() {
        Properties props = new Properties();
        props.setProperty("db.password", "${MY_DB_PASS}");
        BatchConfig c = config(props, Map.of("MY_DB_PASS", "s3cret"));
        assertEquals("s3cret", c.getDbPassword());
    }

    @Test
    void encryptedPasswordIsDecryptedWithMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String keyHex = HexCodec.encode(key);
        String cipher = AesGcm.encryptToHex("real-secret", key);

        Properties props = new Properties();
        props.setProperty("db.password", "enc:" + cipher);
        BatchConfig c = config(props, Map.of("BATCH_MASTER_KEY", keyHex));
        assertEquals("real-secret", c.getDbPassword());
    }

    @Test
    void encryptedPasswordWithoutMasterKeyFailsFast() {
        Properties props = new Properties();
        props.setProperty("db.password", "enc:00ff");
        BatchConfig c = config(props, Map.of());
        assertThrows(IllegalStateException.class, c::getDbPassword);
    }

    @Test
    void missingMasterKeyIsRejectedByHelper() {
        assertThrows(IllegalStateException.class, () -> BatchConfig.masterKeyOrThrow(Map.of()));
    }

    @Test
    void workerInfoUsesConfiguredIdentityWithoutNetworkCalls() {
        Properties props = new Properties();
        props.setProperty("worker.id", "w-fixed");
        props.setProperty("worker.environment", "staging");
        BatchConfig c = config(props, Map.of());
        assertEquals("w-fixed", c.getWorkerInfo().getWorkerId());
        assertEquals("staging", c.getWorkerInfo().getEnvironment());
        assertEquals("unknown", c.getWorkerInfo().getPublicIp());
    }
}
