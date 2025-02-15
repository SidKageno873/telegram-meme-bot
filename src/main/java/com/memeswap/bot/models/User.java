package com.memeswap.bot.models;

public class User {
    private final int id;
    private final long telegramId;
    private final String username;
    private final String name;
    private final boolean showUsername;

    public User(int id, long telegramId, String username, String name, boolean showUsername) {
        this.id = id;
        this.telegramId = telegramId;
        this.username = username;
        this.name = name;
        this.showUsername = showUsername;
    }

    public int getId() { return id; }
    public long getTelegramId() { return telegramId; }
    public String getName() { return name; }
}

