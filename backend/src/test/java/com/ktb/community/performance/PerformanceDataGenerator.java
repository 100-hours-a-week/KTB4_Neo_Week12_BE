package com.ktb.community.performance;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

public class PerformanceDataGenerator {

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3307/community"
                    + "?useUnicode=true"
                    + "&characterEncoding=utf8"
                    + "&serverTimezone=UTC"
                    + "&rewriteBatchedStatements=true";

    private static final String DEFAULT_DB_USERNAME = "community";
    private static final String DEFAULT_DB_PASSWORD =
            "local-performance-password";

    private static final int DEFAULT_USER_COUNT = 10_000;
    private static final int DEFAULT_POST_COUNT = 100_000;
    private static final int DEFAULT_COMMENT_COUNT = 500_000;
    private static final int BATCH_SIZE = 1_000;

    private static final String LOGIN_PASSWORD = "Performance123!";
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 1, 0, 0);

    public static void main(String[] args) throws Exception {
        String dbUrl = env("PERF_DB_URL", DEFAULT_DB_URL);
        String dbUsername = env(
                "PERF_DB_USERNAME",
                DEFAULT_DB_USERNAME
        );
        String dbPassword = env(
                "PERF_DB_PASSWORD",
                DEFAULT_DB_PASSWORD
        );

        int userCount = envInt(
                "PERF_USER_COUNT",
                DEFAULT_USER_COUNT
        );
        int postCount = envInt(
                "PERF_POST_COUNT",
                DEFAULT_POST_COUNT
        );
        int commentCount = envInt(
                "PERF_COMMENT_COUNT",
                DEFAULT_COMMENT_COUNT
        );

        validateLocalDatabaseUrl(dbUrl);
        validateCounts(userCount, postCount, commentCount);

        System.out.printf(
                """
                Performance data generation
                URL: %s
                users: %,d
                posts: %,d
                comments: %,d
                login password: %s
                %n""",
                dbUrl,
                userCount,
                postCount,
                commentCount,
                LOGIN_PASSWORD
        );

        try (Connection connection = DriverManager.getConnection(
                dbUrl,
                dbUsername,
                dbPassword
        )) {
            connection.setAutoCommit(false);

            ensureTargetTablesAreEmpty(connection);

            String encodedPassword =
                    new BCryptPasswordEncoder().encode(LOGIN_PASSWORD);

            insertUsers(
                    connection,
                    userCount,
                    encodedPassword
            );
            insertPosts(
                    connection,
                    userCount,
                    postCount
            );
            insertComments(
                    connection,
                    userCount,
                    postCount,
                    commentCount
            );
            updatePostCommentCounts(connection);
            analyzeTables(connection);
            printFinalCounts(connection);
        }

        System.out.println("Performance data generation completed.");
    }

    private static void insertUsers(
            Connection connection,
            int userCount,
            String encodedPassword
    ) throws SQLException {
        String sql = """
                INSERT INTO users (
                    user_id,
                    email,
                    password,
                    nickname,
                    profile_image,
                    deleted,
                    deleted_at,
                    role,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            for (int id = 1; id <= userCount; id++) {
                boolean deleted = id % 20 == 0;
                LocalDateTime createdAt =
                        BASE_TIME.minusDays(id % 365L);

                statement.setLong(1, id);
                statement.setString(
                        2,
                        "perf-user-%06d@example.com".formatted(id)
                );
                statement.setString(3, encodedPassword);
                statement.setString(
                        4,
                        "perf-user-%06d".formatted(id)
                );
                statement.setNull(5, Types.VARCHAR);
                statement.setBoolean(6, deleted);
                setNullableTimestamp(
                        statement,
                        7,
                        deleted ? BASE_TIME : null
                );
                statement.setString(8, "ROLE_USER");
                statement.setTimestamp(
                        9,
                        Timestamp.valueOf(createdAt)
                );
                statement.setTimestamp(
                        10,
                        Timestamp.valueOf(createdAt)
                );

                statement.addBatch();
                executeBatchIfNeeded(
                        connection,
                        statement,
                        id,
                        userCount,
                        "users"
                );
            }
        }
    }

    private static void insertPosts(
            Connection connection,
            int userCount,
            int postCount
    ) throws SQLException {
        String sql = """
                INSERT INTO posts (
                    post_id,
                    user_id,
                    title,
                    post_body,
                    post_image,
                    likes,
                    views,
                    comments,
                    edited,
                    deleted,
                    blinded,
                    deleted_at,
                    edited_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            for (int id = 1; id <= postCount; id++) {
                long userId =
                        1L + ((long) id * 37L % userCount);
                boolean deleted = id % 20 == 0;
                LocalDateTime createdAt =
                        BASE_TIME.minusSeconds(id * 30L);

                statement.setLong(1, id);
                statement.setLong(2, userId);
                statement.setString(
                        3,
                        "Performance post %08d".formatted(id)
                );
                statement.setString(
                        4,
                        "Performance post body %08d ".formatted(id)
                                + "content ".repeat(20)
                );
                statement.setNull(5, Types.VARCHAR);
                statement.setInt(6, id % 200);
                statement.setInt(7, id % 5_000);
                statement.setInt(8, 0);
                statement.setBoolean(9, false);
                statement.setBoolean(10, deleted);
                statement.setBoolean(11, false);
                setNullableTimestamp(
                        statement,
                        12,
                        deleted ? BASE_TIME : null
                );
                statement.setNull(13, Types.TIMESTAMP);
                statement.setTimestamp(
                        14,
                        Timestamp.valueOf(createdAt)
                );
                statement.setTimestamp(
                        15,
                        Timestamp.valueOf(createdAt)
                );

                statement.addBatch();
                executeBatchIfNeeded(
                        connection,
                        statement,
                        id,
                        postCount,
                        "posts"
                );
            }
        }
    }

    private static void insertComments(
            Connection connection,
            int userCount,
            int postCount,
            int commentCount
    ) throws SQLException {
        String sql = """
                INSERT INTO comments (
                    comment_id,
                    post_id,
                    user_id,
                    parent_comment_id,
                    comment_body,
                    edited,
                    deleted,
                    deleted_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long previousPostId = 1L;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            for (int id = 1; id <= commentCount; id++) {
                boolean reply = id % 4 == 0;

                long postId;
                if (reply) {
                    postId = previousPostId;
                } else if (id <= 10_000) {
                    postId = 1L;
                } else if (id <= 11_000) {
                    postId = Math.min(2L, postCount);
                } else {
                    postId = 1L + (
                            (long) id * 53L % postCount
                    );
                }

                previousPostId = postId;

                long userId =
                        1L + ((long) id * 97L % userCount);
                boolean deleted = id % 10 == 0;
                LocalDateTime createdAt =
                        BASE_TIME.plusSeconds(id);

                statement.setLong(1, id);
                statement.setLong(2, postId);
                statement.setLong(3, userId);

                if (reply) {
                    statement.setLong(4, id - 1L);
                } else {
                    statement.setNull(4, Types.BIGINT);
                }

                statement.setString(
                        5,
                        "Performance comment %09d".formatted(id)
                );
                statement.setBoolean(6, false);
                statement.setBoolean(7, deleted);
                setNullableTimestamp(
                        statement,
                        8,
                        deleted ? createdAt : null
                );
                statement.setTimestamp(
                        9,
                        Timestamp.valueOf(createdAt)
                );
                statement.setTimestamp(
                        10,
                        Timestamp.valueOf(createdAt)
                );

                statement.addBatch();
                executeBatchIfNeeded(
                        connection,
                        statement,
                        id,
                        commentCount,
                        "comments"
                );
            }
        }
    }

    private static void updatePostCommentCounts(
            Connection connection
    ) throws SQLException {
        String sql = """
                UPDATE posts p
                LEFT JOIN (
                    SELECT post_id, COUNT(*) AS comment_count
                    FROM comments
                    WHERE deleted = FALSE
                    GROUP BY post_id
                ) c ON c.post_id = p.post_id
                SET p.comments = COALESCE(c.comment_count, 0)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.executeUpdate();
            connection.commit();
        }
    }

    private static void analyzeTables(
            Connection connection
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "ANALYZE TABLE users, posts, comments"
                     )) {
            statement.execute();
            connection.commit();
        }
    }

    private static void ensureTargetTablesAreEmpty(
            Connection connection
    ) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM users) AS user_count,
                    (SELECT COUNT(*) FROM posts) AS post_count,
                    (SELECT COUNT(*) FROM comments) AS comment_count
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();

            long users = resultSet.getLong("user_count");
            long posts = resultSet.getLong("post_count");
            long comments = resultSet.getLong("comment_count");

            if (users != 0 || posts != 0 || comments != 0) {
                throw new IllegalStateException(
                        """
                        Performance tables must be empty.
                        users=%d, posts=%d, comments=%d
                        """
                                .formatted(users, posts, comments)
                );
            }
        }
    }

    private static void printFinalCounts(
            Connection connection
    ) throws SQLException {
        String sql = """
                SELECT 'users' AS table_name, COUNT(*) AS row_count
                FROM users
                UNION ALL
                SELECT 'posts', COUNT(*)
                FROM posts
                UNION ALL
                SELECT 'comments', COUNT(*)
                FROM comments
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf(
                        "%s: %,d%n",
                        resultSet.getString("table_name"),
                        resultSet.getLong("row_count")
                );
            }
        }
    }

    private static void executeBatchIfNeeded(
            Connection connection,
            PreparedStatement statement,
            int current,
            int total,
            String name
    ) throws SQLException {
        if (current % BATCH_SIZE == 0 || current == total) {
            statement.executeBatch();
            connection.commit();

            if (current % 10_000 == 0 || current == total) {
                System.out.printf(
                        "%s: %,d / %,d%n",
                        name,
                        current,
                        total
                );
            }
        }
    }

    private static void setNullableTimestamp(
            PreparedStatement statement,
            int index,
            LocalDateTime value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(
                    index,
                    Timestamp.valueOf(value)
            );
        }
    }

    private static String env(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null || value.isBlank()
                ? defaultValue
                : value;
    }

    private static int envInt(
            String name,
            int defaultValue
    ) {
        return Integer.parseInt(env(
                name,
                Integer.toString(defaultValue)
        ));
    }

    private static void validateLocalDatabaseUrl(String dbUrl) {
        boolean local =
                dbUrl.startsWith("jdbc:mysql://localhost:3307/")
                        || dbUrl.startsWith(
                        "jdbc:mysql://127.0.0.1:3307/"
                );

        if (!local || !dbUrl.contains("/community")) {
            throw new IllegalArgumentException(
                    "Only local performance DB is allowed: "
                            + dbUrl
            );
        }
    }

    private static void validateCounts(
            int userCount,
            int postCount,
            int commentCount
    ) {
        if (userCount < 1 || postCount < 2 || commentCount < 1) {
            throw new IllegalArgumentException(
                    "Invalid performance data counts"
            );
        }
    }
}