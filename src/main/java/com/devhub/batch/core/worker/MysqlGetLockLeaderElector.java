package com.devhub.batch.core.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Leader election via MySQL advisory lock:
 *   SELECT GET_LOCK(lockName, 0) / SELECT RELEASE_LOCK(lockName).
 *
 * The lock is bound to one dedicated connection, which is kept open while the
 * node is leader. If the connection dies, leadership is lost and another node
 * can take over (no fenced scheduler left behind).
 */
public class MysqlGetLockLeaderElector implements LeaderElector {

    private static final Logger log = LoggerFactory.getLogger(MysqlGetLockLeaderElector.class);

    private final DataSource dataSource;
    private final String lockName;
    private Connection lockConnection;
    private volatile boolean held;

    public MysqlGetLockLeaderElector(DataSource dataSource, String lockName) {
        this.dataSource = dataSource;
        this.lockName = lockName;
    }

    @Override
    public synchronized boolean tryAcquire() {
        try {
            if (held && lockConnection != null && !lockConnection.isClosed()) {
                return true;
            }
            releaseQuietly();
            Connection c = dataSource.getConnection();
            try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                ps.setString(1, lockName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 1) {
                        lockConnection = c;
                        held = true;
                        log.debug("Leader lock acquired: {}", lockName);
                        return true;
                    }
                }
            }
            c.close();
            return false;
        } catch (SQLException e) {
            log.warn("Leader election failed: {}", e.getMessage());
            releaseQuietly();
            return false;
        }
    }

    @Override
    public synchronized void release() {
        if (lockConnection == null) {
            held = false;
            return;
        }
        try (PreparedStatement ps = lockConnection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, lockName);
            ps.executeQuery();
        } catch (SQLException e) {
            log.debug("Release lock failed (ignored): {}", e.getMessage());
        }
        releaseQuietly();
        log.debug("Leader lock released: {}", lockName);
    }

    private void releaseQuietly() {
        if (lockConnection != null) {
            try { lockConnection.close(); } catch (SQLException ignored) { }
            lockConnection = null;
        }
        held = false;
    }
}
