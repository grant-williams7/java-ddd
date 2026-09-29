package com.example.marketplace.infrastructure.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Converts {@code DATABASE_URL} into JDBC settings. The variable keeps its
 * libpq meaning, so either form works:
 * <ul>
 *   <li>a keyword DSN: {@code host=localhost user=app password='s3 cret' dbname=app}</li>
 *   <li>a URL: {@code postgres://app:secret@localhost:5432/app?sslmode=disable}</li>
 * </ul>
 * Missing fields fall back to the {@code PG*} environment variables, then to
 * libpq's defaults. Error messages never repeat the input, because it may
 * contain a password.
 */
record DatabaseUrl(String jdbcUrl, String user, String password, Map<String, String> properties,
        Integer maximumPoolSize) {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "5432";

    private static final Map<String, String> ENVIRONMENT_FALLBACKS = Map.of(
            "host", "PGHOST",
            "port", "PGPORT",
            "dbname", "PGDATABASE",
            "user", "PGUSER",
            "password", "PGPASSWORD",
            "sslmode", "PGSSLMODE");

    /** libpq settings with a same-named or renamed pgjdbc property. */
    private static final Map<String, String> DRIVER_PROPERTIES = Map.of(
            "sslmode", "sslmode",
            "sslrootcert", "sslrootcert",
            "sslcert", "sslcert",
            "sslkey", "sslkey",
            "sslpassword", "sslpassword",
            "connect_timeout", "connectTimeout",
            "application_name", "ApplicationName");

    private static final Set<String> CONNECTION_KEYS = Set.of("host", "port", "dbname", "user", "password");

    private static final String POOL_MAX_CONNS = "pool_max_conns";
    private static final String POOL_PREFIX = "pool_";

    static DatabaseUrl parse(String value, Map<String, String> environment, String osUser) {
        Map<String, String> given = isUrl(value) ? parseUrl(value) : parseKeywordDsn(value);
        String database = given.remove("database");
        if (database != null) {
            given.putIfAbsent("dbname", database);
        }

        Map<String, String> settings = new LinkedHashMap<>();
        ENVIRONMENT_FALLBACKS.forEach((key, variable) -> {
            String fromEnvironment = environment.get(variable);
            if (fromEnvironment != null && !fromEnvironment.isEmpty()) {
                settings.put(key, fromEnvironment);
            }
        });
        settings.putAll(given);

        String host = settings.getOrDefault("host", DEFAULT_HOST);
        String port = settings.getOrDefault("port", DEFAULT_PORT);
        String user = settings.getOrDefault("user", osUser);
        String dbname = settings.getOrDefault("dbname", user);

        if (host.contains(",")) {
            throw invalid("multiple hosts are not supported");
        }
        if (host.startsWith("/")) {
            throw invalid("unix-domain socket hosts are not supported");
        }
        if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) {
            throw invalid("port must be a number");
        }

        Map<String, String> properties = new LinkedHashMap<>();
        StringBuilder options = new StringBuilder();
        Integer maximumPoolSize = null;
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = setting.getKey();
            String settingValue = setting.getValue();
            if (CONNECTION_KEYS.contains(key)) {
                continue;
            }
            if (DRIVER_PROPERTIES.containsKey(key)) {
                properties.put(DRIVER_PROPERTIES.get(key), settingValue);
            } else if (key.equals(POOL_MAX_CONNS)) {
                maximumPoolSize = parsePoolSize(settingValue);
            } else if (!key.startsWith(POOL_PREFIX)) {
                // Anything else is a server runtime parameter, as libpq treats it.
                if (!options.isEmpty()) {
                    options.append(' ');
                }
                options.append("-c ").append(escapeOption(key)).append('=').append(escapeOption(settingValue));
            }
        }
        if (!options.isEmpty()) {
            properties.put("options", options.toString());
        }

        String jdbcHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        String jdbcUrl = "jdbc:postgresql://" + jdbcHost + ":" + port + "/"
                + URLEncoder.encode(dbname, StandardCharsets.UTF_8).replace("+", "%20");

        return new DatabaseUrl(jdbcUrl, user, settings.get("password"), Map.copyOf(properties), maximumPoolSize);
    }

    private static boolean isUrl(String value) {
        return value.startsWith("postgres://") || value.startsWith("postgresql://");
    }

    /**
     * Parses libpq's keyword/value format: pairs separated by whitespace,
     * optional whitespace around '=', values optionally single-quoted, and
     * backslash escapes for quotes and backslashes.
     */
    private static Map<String, String> parseKeywordDsn(String value) {
        Map<String, String> settings = new LinkedHashMap<>();
        int i = 0;
        int length = value.length();
        while (true) {
            i = skipWhitespace(value, i);
            if (i >= length) {
                return settings;
            }

            int keyStart = i;
            while (i < length && value.charAt(i) != '=' && !Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            String key = value.substring(keyStart, i);

            i = skipWhitespace(value, i);
            if (i >= length || value.charAt(i) != '=' || key.isEmpty()) {
                throw invalid("expected keyword=value pairs");
            }
            i = skipWhitespace(value, i + 1);

            StringBuilder parsed = new StringBuilder();
            if (i < length && value.charAt(i) == '\'') {
                i++;
                boolean closed = false;
                while (i < length) {
                    char c = value.charAt(i++);
                    if (c == '\\' && i < length) {
                        parsed.append(value.charAt(i++));
                    } else if (c == '\'') {
                        closed = true;
                        break;
                    } else {
                        parsed.append(c);
                    }
                }
                if (!closed) {
                    throw invalid("unterminated quoted value");
                }
            } else {
                while (i < length && !Character.isWhitespace(value.charAt(i))) {
                    char c = value.charAt(i++);
                    if (c == '\\' && i < length) {
                        parsed.append(value.charAt(i++));
                    } else {
                        parsed.append(c);
                    }
                }
            }
            settings.put(key, parsed.toString());
        }
    }

    private static Map<String, String> parseUrl(String value) {
        String rest = value.substring(value.indexOf("://") + 3);
        Map<String, String> settings = new LinkedHashMap<>();

        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) {
            for (String pair : rest.substring(queryStart + 1).split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int equals = pair.indexOf('=');
                String key = decode(equals < 0 ? pair : pair.substring(0, equals), true);
                String pairValue = equals < 0 ? "" : decode(pair.substring(equals + 1), true);
                settings.put(key, pairValue);
            }
            rest = rest.substring(0, queryStart);
        }

        int pathStart = rest.indexOf('/');
        String authority = pathStart < 0 ? rest : rest.substring(0, pathStart);
        if (pathStart >= 0 && pathStart + 1 < rest.length()) {
            settings.put("dbname", decode(rest.substring(pathStart + 1), false));
        }

        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            String userInfo = authority.substring(0, at);
            authority = authority.substring(at + 1);
            int colon = userInfo.indexOf(':');
            String user = decode(colon < 0 ? userInfo : userInfo.substring(0, colon), false);
            if (!user.isEmpty()) {
                settings.put("user", user);
            }
            if (colon >= 0) {
                settings.put("password", decode(userInfo.substring(colon + 1), false));
            }
        }

        String host = authority;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                throw invalid("unterminated IPv6 host");
            }
            host = authority.substring(1, close);
            String afterHost = authority.substring(close + 1);
            if (afterHost.startsWith(":") && afterHost.length() > 1) {
                settings.put("port", afterHost.substring(1));
            }
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                host = authority.substring(0, colon);
                if (colon + 1 < authority.length()) {
                    settings.put("port", authority.substring(colon + 1));
                }
            }
        }
        if (!host.isEmpty()) {
            settings.put("host", decode(host, false));
        }

        return settings;
    }

    /**
     * Percent-decodes URL parts. Query values treat '+' as a space; user info,
     * host and path don't, matching how URLs are parsed elsewhere.
     */
    private static String decode(String encoded, boolean plusIsSpace) {
        StringBuilder decoded = new StringBuilder();
        byte[] pending = new byte[encoded.length()];
        int pendingLength = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) {
                    throw invalid("malformed percent-encoding");
                }
                try {
                    pending[pendingLength++] = (byte) Integer.parseInt(encoded.substring(i + 1, i + 3), 16);
                } catch (NumberFormatException e) {
                    throw invalid("malformed percent-encoding");
                }
                i += 2;
                continue;
            }
            if (pendingLength > 0) {
                decoded.append(new String(pending, 0, pendingLength, StandardCharsets.UTF_8));
                pendingLength = 0;
            }
            decoded.append(c == '+' && plusIsSpace ? ' ' : c);
        }
        if (pendingLength > 0) {
            decoded.append(new String(pending, 0, pendingLength, StandardCharsets.UTF_8));
        }
        return decoded.toString();
    }

    private static int skipWhitespace(String value, int from) {
        int i = from;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
            i++;
        }
        return i;
    }

    private static Integer parsePoolSize(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw invalid("pool_max_conns must be a number");
        }
    }

    private static String escapeOption(String value) {
        return value.replace("\\", "\\\\").replace(" ", "\\ ");
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("invalid DATABASE_URL: " + reason);
    }

    @Override
    public String toString() {
        return "DatabaseUrl[jdbcUrl=" + jdbcUrl + ", user=" + user + ", properties=" + properties + "]";
    }
}
