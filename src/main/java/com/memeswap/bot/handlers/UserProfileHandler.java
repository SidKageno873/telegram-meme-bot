package com.memeswap.bot.handlers;

import com.memeswap.bot.database.DatabaseHandler;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class UserProfileHandler {
    public static SendMessage handleProfileCommand(long telegramId) {
        SendMessage response = new SendMessage();

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.id, u.name, u.username FROM users u WHERE u.telegram_id = ?")) {

            stmt.setLong(1, telegramId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("id");
                String name = rs.getString("name");
                String username = rs.getString("username");

                Map<String, Integer> stats = DatabaseHandler.getAuthorStats(userId);
                int totalMemes = stats.getOrDefault("totalMemes", 0);
                int totalLikes = stats.getOrDefault("totalLikes", 0);
                int totalViews = DatabaseHandler.getTotalViewsForUser(userId);

                String profileInfo = String.format(
                        "👤 Профиль:\n" +
                                "📛 Имя: %s\n" +
                                "🔗 Юзернейм: @%s\n" +
                                "📊 Всего мемов: %d\n" +
                                "❤️ Всего лайков: %d\n" +
                                "👀 Всего просмотров: %d",
                        name,
                        username != null ? username : "не указан",
                        totalMemes,
                        totalLikes,
                        totalViews
                );

                response.setText(profileInfo);
            }
        } catch (SQLException e) {
            response.setText("❌ Ошибка загрузки профиля");
        }

        return response;
    }
}