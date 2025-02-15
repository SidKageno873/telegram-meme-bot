package com.memeswap.bot.utils;

import com.memeswap.bot.UserSession;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

public class KeyboardFactory {
    public static ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🖼️ Загрузить мем");
        row1.add("📜 Лента мемов");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("👤 Профиль");
        row2.add("📂 Мои мемы");
        row2.add("⭐ Избранное");

        markup.setKeyboard(List.of(row1, row2));
        return markup;
    }

    public static ReplyKeyboardMarkup getMemeMenuKeyboard(UserSession.FeedType feedType) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("⬅️ Назад");
        row1.add("➡️ Вперед");

        KeyboardRow row2 = new KeyboardRow();
        switch (feedType) {
            case MAIN -> {
                row2.add("❤️ Лайк");
                row2.add("🚩 Пожаловаться");
            }
            case USER, FAVORITES -> {}
            case AUTHOR -> {} // Пустая реализация для AUTHOR
        }

        KeyboardRow row3 = new KeyboardRow();
        row3.add("🏠 Главное меню");

        markup.setKeyboard(List.of(row1, row2, row3));
        return markup;
    }
}