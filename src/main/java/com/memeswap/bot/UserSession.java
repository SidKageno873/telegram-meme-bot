package com.memeswap.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;

public class UserSession {
    private List<Integer> memeIds = new ArrayList<>();
    private int currentMemeIndex = -1;
    private long userId; // ID пользователя
    private FeedType currentFeedType = FeedType.MAIN;
    private Integer authorId; // ID автора
    private List<Integer> previousFeed; // Для возврата
    private FeedType previousFeedType;
    private List<Integer> previousMemeIds;
    private int previousMemeIndex;

    private InlineKeyboardMarkup currentKeyboard;

    private List<Integer> userFeed = new ArrayList<>();

    public List<Integer> getUserFeed() {
        return userFeed;
    }

    // Перечисление типов лент
    public enum FeedType {
        MAIN, USER, AUTHOR, FAVORITES
    }

    // Возврат к предыдущей ленте
    public void returnToPreviousFeed() {
        if (previousFeedType != null) {
            this.currentFeedType = previousFeedType;
            this.memeIds = previousMemeIds;
            this.currentMemeIndex = previousMemeIndex;
        }
    }

    // Сохранение текущего состояния
    public void savePreviousState() {
        this.previousMemeIds = new ArrayList<>(this.memeIds);
        this.previousMemeIndex = this.currentMemeIndex;
        this.previousFeedType = this.currentFeedType;
    }

    // Восстановление предыдущего состояния
    public void restorePreviousState() {
        if (previousFeedType != null) {
            this.memeIds = previousMemeIds;
            this.currentMemeIndex = previousMemeIndex;
            this.currentFeedType = previousFeedType;
        }
    }

    // Установка текущего типа ленты
    public void setCurrentFeedType(FeedType feedType) {
        this.currentFeedType = feedType;
    }
    // Установка ID автора
    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }
    // Геттеры и сеттеры
    public List<Integer> getMemeIds() { return memeIds; }
    public void setMemeIds(List<Integer> memeIds) { this.memeIds = memeIds; }
    public int getCurrentMemeIndex() { return currentMemeIndex; }
    public void setCurrentMemeIndex(int index) { this.currentMemeIndex = index; }
    public long getUserId() { return userId; }
    public FeedType getCurrentFeedType() { return currentFeedType; }
    public void setCurrentKeyboard(InlineKeyboardMarkup keyboard) { this.currentKeyboard = keyboard; }

    // Очистка кэша
    public void clearCache() {
        this.memeIds = new ArrayList<>();
        this.currentMemeIndex = -1;
        this.currentFeedType = FeedType.MAIN;
        this.authorId = null;
        this.previousFeed = null;
    }
}