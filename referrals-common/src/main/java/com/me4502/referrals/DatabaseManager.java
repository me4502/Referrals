package com.me4502.referrals;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class DatabaseManager {

    private String jdbcUrl;
    private String username;
    private String password;

    private HikariDataSource dataSource;

    public DatabaseManager(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(this.jdbcUrl);
        dataSource.setUsername(this.username);
        dataSource.setPassword(this.password);

        if (!doesTableExist("referrals")) {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement("CREATE TABLE referrals (`id` INTEGER PRIMARY KEY AUTO_INCREMENT, `player` CHAR(36) UNIQUE, `source` VARCHAR(36), `is_player` TINYINT(1), `time` DATETIME, `rewarded` TINYINT(1));");
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect() {
        try {
            if (!dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception e) {
        }
    }

    public void addPlayerReferral(UUID player, UUID source, boolean rewarded) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO referrals (`player`, `source`, `is_player`, `time`, `rewarded`) VALUES (?, ?, ?, ?, ?);");
            statement.setString(1, player.toString());
            statement.setString(2, source.toString());
            statement.setBoolean(3, true);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setBoolean(5, rewarded);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addWebsiteReferral(UUID player, String source) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO referrals (`player`, `source`, `is_player`, `time`, `rewarded`) VALUES (?, ?, ?, ?, ?);");
            statement.setString(1, player.toString());
            statement.setString(2, source);
            statement.setBoolean(3, false);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setBoolean(5, true);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean hasAlreadyReferred(UUID player) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT id FROM referrals WHERE `player` = ?;");
            statement.setString(1, player.toString());

            return statement.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public List<UUID> getPendingRewards(UUID source) {
        List<UUID> pendingRewards = new ArrayList<>();

        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `player` FROM referrals WHERE `source` = ? AND `is_player` = TRUE AND `rewarded` = FALSE;");
            statement.setString(1, source.toString());

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                pendingRewards.add(UUID.fromString(resultSet.getString("player")));
            }

            statement.close();

            statement = connection.prepareStatement("UPDATE referrals SET `rewarded` = TRUE WHERE `source` = ? AND `is_player` = TRUE AND `rewarded` = FALSE;");
            statement.setString(1, source.toString());

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return pendingRewards;
    }

    public Map<UUID, Timestamp> getPlayersReferred(UUID source) {
        Map<UUID, Timestamp> players = new HashMap<>();

        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `player`, `time` FROM referrals WHERE `source` = ? AND `is_player` = TRUE;");
            statement.setString(1, source.toString());

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("player"));
                Timestamp timestamp = resultSet.getTimestamp("time");

                players.put(uuid, timestamp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return players;
    }

    public void clearAll() {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM referrals;");

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearAll(UUID source) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM referrals WHERE `source` = ? AND `is_player`;");
            statement.setString(1, source.toString());

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean doesTableExist(String name) {
        boolean ret = false;

        try (Connection connection = getConnection()) {
            DatabaseMetaData dbm = connection.getMetaData();
            ResultSet set = dbm.getTables(null, null, name, null);
            if (set.next()) {
                ret = true;
            }
            set.close();
        } catch (SQLException ex) {
            ret = false;
        }

        return ret;
    }

    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
