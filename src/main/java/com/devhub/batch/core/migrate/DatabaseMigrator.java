package com.devhub.batch.core.migrate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Minimal versioned SQL migration runner (Flyway-style file naming, no
 * dependency): executes classpath db/migration/*.sql in lexical order once,
 * tracking applied versions in SYS_BATCH_MIGRATIONS.
 */
public final class DatabaseMigrator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrator.class);
    private static final String BASE = "db/migration";

    private final DataSource dataSource;

    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public synchronized List<String> migrate() {
        List<String> applied = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            ensureRegistry(c);
            Set<String> done = alreadyApplied(c);
            for (String script : scanScripts()) {
                if (done.contains(script)) continue;
                apply(c, script);
                markApplied(c, script);
                applied.add(script);
                log.info("event=migration.applied version={}", script);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Migration failed", e);
        }
        return applied;
    }

    private void ensureRegistry(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS SYS_BATCH_MIGRATIONS ("
                    + "VERSION VARCHAR(190) PRIMARY KEY, "
                    + "APPLIED_AT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private Set<String> alreadyApplied(Connection c) throws SQLException {
        Set<String> done = new TreeSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION FROM SYS_BATCH_MIGRATIONS")) {
            while (rs.next()) done.add(rs.getString(1));
        }
        return done;
    }

    private void apply(Connection c, String script) throws SQLException {
        String sql = readResource(BASE + "/" + script);
        for (String statement : splitStatements(sql)) {
            try (Statement st = c.createStatement()) {
                st.execute(statement);
            }
        }
    }

    private void markApplied(Connection c, String script) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO SYS_BATCH_MIGRATIONS (VERSION) VALUES (?)")) {
            ps.setString(1, script);
            ps.executeUpdate();
        }
    }

    private String readResource(String path) {
        try (InputStream is = DatabaseMigrator.class.getClassLoader().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read migration resource: " + path, e);
        }
    }

    /** Splits on ';', ignoring '--' comment lines. */
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

    private List<String> scanScripts() {
        TreeSet<String> names = new TreeSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader() != null
                ? Thread.currentThread().getContextClassLoader()
                : DatabaseMigrator.class.getClassLoader();
        try {
            Enumeration<URL> urls = cl.getResources(BASE);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("jar".equals(url.getProtocol())) {
                    collectFromJar(url, names);
                } else {
                    collectFromDirectory(url, names);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot scan migration scripts", e);
        }
        return new ArrayList<>(names);
    }

    private void collectFromJar(URL url, TreeSet<String> names) throws Exception {
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        conn.setUseCaches(false);
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(BASE + "/") && name.endsWith(".sql")) {
                    names.add(name.substring(BASE.length() + 1));
                }
            }
        }
    }

    private void collectFromDirectory(URL url, TreeSet<String> names) throws Exception {
        File dir = new File(url.toURI());
        File[] files = dir.listFiles((d, n) -> n.endsWith(".sql"));
        if (files != null) for (File f : files) names.add(f.getName());
    }
}
