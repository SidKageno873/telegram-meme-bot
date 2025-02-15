package com.memeswap.bot.database;

import com.memeswap.bot.models.Meme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHandler {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHandler.class);
    private static final String URL = System.getenv("DATABASE_URL");
    private static final String USER = "meme_swap_92g2_user";
    private static final String PASSWORD = "fHbjtgu7MmcqXFigQJwwFFjxsQdqAHNN";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initialize() {
        try (Connection conn = getConnection()) {
            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id SERIAL PRIMARY KEY, " +
                            "telegram_id BIGINT UNIQUE, " +
                            "username TEXT, " +
                            "name TEXT, " +
                            "show_username BOOLEAN DEFAULT TRUE)"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS memes (" +
                            "id SERIAL PRIMARY KEY, " +
                            "user_id INTEGER, " +
                            "file_id TEXT UNIQUE, " +
                            "caption TEXT, " +
                            "likes INTEGER DEFAULT 0, " +
                            "FOREIGN KEY(user_id) REFERENCES users(id))"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS likes (" +
                            "user_id INTEGER, " +
                            "meme_id INTEGER, " +
                            "UNIQUE(user_id, meme_id))"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS reports (" +
                            "user_id INTEGER, " +
                            "meme_id INTEGER, " +
                            "reason TEXT, " +
                            "UNIQUE(user_id, meme_id))"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS views (" +
                            "user_id INTEGER, " +
                            "meme_id INTEGER, " +
                            "PRIMARY KEY (user_id, meme_id), " +
                            "FOREIGN KEY (user_id) REFERENCES users(id), " +
                            "FOREIGN KEY (meme_id) REFERENCES memes(id))"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS favorites (" +
                            "user_id INTEGER, " +
                            "meme_id INTEGER, " +
                            "PRIMARY KEY (user_id, meme_id), " +
                            "FOREIGN KEY (user_id) REFERENCES users(id), " +
                            "FOREIGN KEY (meme_id) REFERENCES memes(id))"
            );

            logger.info("База данных инициализирована.");
        } catch (SQLException e) {
            logger.error("Ошибка инициализации БД: ", e);
        }
    }

    public static void addUserIfNotExists(long telegramId, String username, String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO users (telegram_id, username, name) VALUES (?, ?, ?) " +
                             "ON CONFLICT (telegram_id) DO NOTHING")) {
            stmt.setLong(1, telegramId);
            stmt.setString(2, username != null ? username : "");
            stmt.setString(3, name != null ? name : "");
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка добавления пользователя: ", e);
        }
    }

    public static int getTotalViewsForUser(int userId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM views WHERE meme_id IN " +
                             "(SELECT id FROM memes WHERE user_id = ?)")) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.error("Ошибка получения просмотров: ", e);
            return 0;
        }
    }

    public static void addToFavorites(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO favorites (user_id, meme_id) VALUES (?, ?) " +
                             "ON CONFLICT (user_id, meme_id) DO NOTHING")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка добавления в избранное: ", e);
        }
    }

    public static void removeFromFavorites(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM favorites WHERE user_id = ? AND meme_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка удаления из избранного: ", e);
        }
    }

    public static boolean validateCaption(String caption) {
        if (caption == null || caption.isEmpty()) {
            return false;
        }

        // Проверка на длину
        if (caption.length() > 250) {
            return false;
        }

        // Проверка на наличие символа #
        if (!caption.startsWith("#")) {
            return false;
        }

        // Проверка на наличие ссылок и символа @
        if (caption.contains("http://") || caption.contains("https://") || caption.contains("@")) {
            return false;
        }

        return true;
    }

    public static boolean memeExists(int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM memes WHERE id = ?")) {
            stmt.setInt(1, memeId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Ошибка проверки существования мема: ", e);
            return false;
        }
    }

    public static List<Integer> getFavoriteMemeIds(long userId) {
        List<Integer> memeIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT meme_id FROM favorites WHERE user_id = ? ORDER BY meme_id")) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                memeIds.add(rs.getInt("meme_id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения избранных мемов: ", e);
        }
        return memeIds;
    }

    public static boolean isMemeInFavorites(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM favorites WHERE user_id = ? AND meme_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Ошибка проверки избранного: ", e);
            return false;
        }
    }

    public static List<Integer> getUserMemeIds(int userId) {
        List<Integer> memeIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id FROM memes WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                memeIds.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения мемов пользователя: ", e);
        }
        return memeIds;
    }

    public static Integer getUserIdByTelegramId(long telegramId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id FROM users WHERE telegram_id = ?")) {
            stmt.setLong(1, telegramId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            logger.error("Ошибка получения ID пользователя: ", e);
            return null;
        }
    }

    public static List<Integer> getMemesByUserId(long telegramId) {
        List<Integer> memeIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT m.id FROM memes m " +
                             "JOIN users u ON m.user_id = u.id " +
                             "WHERE u.telegram_id = ? ORDER BY m.id")) {
            stmt.setLong(1, telegramId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                memeIds.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения мемов пользователя: ", e);
        }
        return memeIds;
    }

    public static void saveMeme(long telegramUserId, String fileId, String caption) {
        if (!validateCaption(caption)) {
            logger.error("Некорректная подпись мема: {}", caption);
            return;
        }

        Integer userId = getUserIdByTelegramId(telegramUserId);
        if (userId == null) {
            logger.error("Пользователь с telegramId {} не найден", telegramUserId);
            return;
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO memes (user_id, file_id, caption) VALUES (?, ?, ?)")) {
            stmt.setLong(1, userId);
            stmt.setString(2, fileId);
            stmt.setString(3, caption);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка сохранения мема: ", e);
        }
    }

    public static List<Integer> getAllMemeIds() {
        List<Integer> memeIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM memes ORDER BY RANDOM()")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                memeIds.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения списка мемов: ", e);
        }
        return memeIds;
    }

    public static Meme getMemeById(int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM memes WHERE id = ?")) {
            stmt.setInt(1, memeId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Meme(
                        rs.getInt("id"),
                        rs.getString("file_id"),
                        rs.getString("caption"),
                        rs.getLong("user_id"),
                        rs.getInt("likes")
                );
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения мема: ", e);
        }
        return null;
    }

    public static boolean hasUserLikedMeme(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM likes WHERE user_id = ? AND meme_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Ошибка проверки лайка: ", e);
            return false;
        }
    }

    public static void addLike(long userId, int memeId) {
        if (!memeExists(memeId)) {
            logger.warn("Мем {} не существует", memeId);
            return;
        }

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO likes (user_id, meme_id) VALUES (?, ?)")) {
                stmt.setLong(1, userId);
                stmt.setInt(2, memeId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE memes SET likes = likes + 1 WHERE id = ?")) {
                stmt.setInt(1, memeId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Ошибка добавления лайка: ", e);
        }
    }

    public static boolean hasUserReportedMeme(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM reports WHERE user_id = ? AND meme_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Ошибка проверки жалобы: ", e);
            return false;
        }
    }

    public static void addReport(long userId, int memeId, String reason) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO reports (user_id, meme_id, reason) VALUES (?, ?, ?)")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            stmt.setString(3, reason);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка добавления жалобы: ", e);
        }
    }

    public static void deleteMeme(int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM memes WHERE id = ?")) {
            stmt.setInt(1, memeId);
            stmt.executeUpdate();
            logger.info("Мем {} удален", memeId);
        } catch (SQLException e) {
            logger.error("Ошибка удаления мема: ", e);
        }
    }

    public static com.memeswap.bot.models.User getAuthorByMemeId(int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.* FROM users u "
                             + "JOIN memes m ON u.id = m.user_id "
                             + "WHERE m.id = ?")) {
            stmt.setInt(1, memeId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new com.memeswap.bot.models.User(
                        rs.getInt("id"),
                        rs.getLong("telegram_id"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getBoolean("show_username")
                );
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения автора мема: ", e);
        }
        return null;
    }

    public static boolean isMemeOwner(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT user_id FROM memes WHERE id = ?")) {
            stmt.setInt(1, memeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getLong("user_id") == userId;
        } catch (SQLException e) {
            logger.error("Ошибка проверки владельца: ", e);
            return false;
        }
    }

    public static List<Integer> getAllMemeIdsWeighted(long userId) {
        List<Integer> memeIds = new ArrayList<>();
        String sql = "SELECT m.id, COUNT(v.meme_id) as views " +
                "FROM memes m " +
                "LEFT JOIN views v ON m.id = v.meme_id AND v.user_id = ? " +
                "GROUP BY m.id " +
                "ORDER BY RANDOM() / (views + 1) DESC " +
                "LIMIT 100";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                memeIds.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения мемов: ", e);
        }
        return memeIds;
    }

    public static Map<String, Integer> getAuthorStats(int userId) {
        Map<String, Integer> stats = new HashMap<>();
        try (Connection conn = getConnection()) {
            // Общее количество мемов
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM memes WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                stats.put("totalMemes", rs.next() ? rs.getInt(1) : 0);
            }

            // Общее количество лайков
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT SUM(likes) FROM memes WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                stats.put("totalLikes", rs.next() ? rs.getInt(1) : 0);
            }

            // Общее количество просмотров
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM views WHERE meme_id IN " +
                            "(SELECT id FROM memes WHERE user_id = ?)")) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                stats.put("totalViews", rs.next() ? rs.getInt(1) : 0);
            }

        } catch (SQLException e) {
            logger.error("Ошибка получения статистики автора: ", e);
        }
        return stats;
    }

    public static com.memeswap.bot.models.User getUserByTelegramId(long telegramId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM users WHERE telegram_id = ?")) {
            stmt.setLong(1, telegramId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new com.memeswap.bot.models.User(
                        rs.getInt("id"),
                        rs.getLong("telegram_id"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getBoolean("show_username")
                );
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения пользователя: ", e);
        }
        return null;
    }

    public static void addView(long userId, int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO views (user_id, meme_id) VALUES (?, ?) " +
                             "ON CONFLICT (user_id, meme_id) DO NOTHING")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, memeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка добавления просмотра: ", e);
        }
    }

    public static int getMemeViews(int memeId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM views WHERE meme_id = ?")) {
            stmt.setInt(1, memeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.error("Ошибка получения просмотров мема: ", e);
            return 0;
        }
    }
}
