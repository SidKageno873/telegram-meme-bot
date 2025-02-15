package com.memeswap.bot;

import com.memeswap.bot.database.DatabaseHandler;

import java.util.Scanner;

public class ConsoleCommandHandler implements Runnable {
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Введите команду (delete <memeId>): ");
            String command = scanner.nextLine().trim();
            if (command.startsWith("delete ")) {
                try {
                    int memeId = Integer.parseInt(command.substring(7));
                    DatabaseHandler.deleteMeme(memeId);
                    System.out.println("Мем " + memeId + " удален!");

                    // Очищаем кэш у всех пользователей
                    MemeSwagBot.getInstance().clearAllSessionsCache();
                } catch (NumberFormatException e) {
                    System.out.println("Неверный формат команды!");
                }
            }
        }
    }
}