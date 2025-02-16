package com.memeswap.bot;

import com.memeswap.bot.database.DatabaseHandler;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Инициализация базы данных
            DatabaseHandler.initialize();

            // Регистрация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MemeSwagBot());

            // Запуск обработчика команд консоли
            //new Thread(new ConsoleCommandHandler()).start();

            System.out.println("Бот успешно запущен!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}