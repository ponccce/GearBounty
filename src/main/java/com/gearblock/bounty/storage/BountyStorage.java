package com.gearblock.bounty.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage. Every write runs on one background thread, so the game never waits for the disk
 * and writes always happen in order. The SQLite driver ships with Paper, so nothing is shaded.
 */
public final class BountyStorage {

    private static final Logger LOG = Logger.getLogger("GearBounty");

    private final Path file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GearBounty-Storage");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public BountyStorage(Path file) {
        this.file = file;
    }

    public void open() throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new SQLException("Could not create " + file.getParent(), e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS bounties (
                        id          INTEGER PRIMARY KEY,
                        target      TEXT    NOT NULL,
                        target_name TEXT    NOT NULL,
                        placer      TEXT    NOT NULL,
                        placer_name TEXT    NOT NULL,
                        amount      REAL    NOT NULL,
                        created_at  INTEGER NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_bounties_target ON bounties(target)");
        }
    }

    /** Reads everything once at startup. Runs on the calling thread. */
    public List<Bounty> loadAll() throws SQLException {
        List<Bounty> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, target, target_name, placer, placer_name, amount, created_at FROM bounties")) {
            while (rs.next()) {
                result.add(new Bounty(
                        rs.getLong(1),
                        UUID.fromString(rs.getString(2)),
                        rs.getString(3),
                        UUID.fromString(rs.getString(4)),
                        rs.getString(5),
                        rs.getDouble(6),
                        rs.getLong(7)));
            }
        }
        return result;
    }

    public void insert(Bounty b) {
        io.execute(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO bounties (id, target, target_name, placer, placer_name, amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, b.id());
                ps.setString(2, b.target().toString());
                ps.setString(3, b.targetName());
                ps.setString(4, b.placer().toString());
                ps.setString(5, b.placerName());
                ps.setDouble(6, b.amount());
                ps.setLong(7, b.createdAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Could not save bounty " + b.id(), e);
            }
        });
    }

    public void delete(Collection<Bounty> bounties) {
        if (bounties.isEmpty()) {
            return;
        }
        List<Long> ids = bounties.stream().map(Bounty::id).toList();
        io.execute(() -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM bounties WHERE id = ?")) {
                for (long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Could not delete bounties " + ids, e);
            }
        });
    }

    /** Finishes pending writes, then closes the database. */
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warning("Some bounty writes did not finish before shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not close the bounty database", e);
        }
    }
}
