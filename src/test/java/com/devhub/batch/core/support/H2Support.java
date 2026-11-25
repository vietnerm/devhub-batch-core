package com.devhub.batch.core.support;

import com.devhub.batch.core.repository.JdbcTaskRepository;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory H2 (MySQL compatibility mode) database with the scheduler schema,
 * used by fast unit tests. Each instance gets a fresh database name.
 */
public final class H2Support implements AutoCloseable {

    private final HikariDataSource ds;
    private final JdbcTaskRepository repo;

    private H2Support(HikariDataSource ds, JdbcTaskRepository repo) {
        this.ds = ds;
        this.repo = repo;
    }

    public static H2Support create() {
        String name = "batch_" + Long.toUnsignedString(System.nanoTime());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + name
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(4);
        ds.setPoolName("h2-" + name);
        applySchema(ds);
        return new H2Support(ds, new JdbcTaskRepository(ds));
    }

    private static void applySchema(DataSource ds) {
        for (String statement : splitStatements(readSchema())) {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute(statement);
            } catch (SQLException e) {
                throw new IllegalStateException("schema-h2.sql failed: " + statement, e);
            }
        }
    }

    private static String readSchema() {
        try (InputStream is = H2Support.class.getResourceAsStream("/schema-h2.sql");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read schema-h2.sql", e);
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : sql.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("--") || line.isEmpty()) continue;
            current.append(line).append('\n');
            if (line.endsWith(";")) {
                String statement = current.toString().trim();
                statement = statement.substring(0, statement.length() - 1).trim();
                if (!statement.isEmpty()) statements.add(statement);
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) statements.add(tail);
        return statements;
    }

    public JdbcTaskRepository repo() { return repo; }

    public DataSource dataSource() { return ds; }

    /** Direct JDBC helper for test setup/verification. */
    public int update(String sql, Object... args) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns the first column of the first row as a string (null when SQL NULL). */
    public String queryString(String sql, Object... args) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Object value = rs.getObject(1);
                return rs.wasNull() ? null : String.valueOf(value);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        ds.close();
    }
}
