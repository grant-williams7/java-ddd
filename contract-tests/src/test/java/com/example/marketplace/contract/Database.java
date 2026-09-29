package com.example.marketplace.contract;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct access to the database behind the API, for setup the API can't do and
 * for checking stored state. Configured by {@code CONTRACT_DATABASE_URL}
 * (JDBC), {@code CONTRACT_DATABASE_USER} and {@code CONTRACT_DATABASE_PASSWORD}.
 */
final class Database {

    private static final String URL =
            Environment.get("CONTRACT_DATABASE_URL", "jdbc:postgresql://localhost:5432/marketplace");
    private static final String USER = Environment.get("CONTRACT_DATABASE_USER", "marketplace");
    private static final String PASSWORD = Environment.get("CONTRACT_DATABASE_PASSWORD", "marketplace");

    private Database() {
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    static int update(String sql, Object... parameters) {
        try (Connection connection = connect(); PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    static List<Map<String, Object>> query(String sql, Object... parameters) {
        try (Connection connection = connect();
                PreparedStatement statement = prepare(connection, sql, parameters);
                ResultSet rows = statement.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i));
                }
                result.add(row);
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    static long count(String sql, Object... parameters) {
        return ((Number) query(sql, parameters).getFirst().values().iterator().next()).longValue();
    }

    /** Clears all data, for scenarios that need an empty or fully known state. */
    static void truncate() {
        update("TRUNCATE TABLE products, idempotency_records, outbox_events, sellers CASCADE");
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement;
    }
}
