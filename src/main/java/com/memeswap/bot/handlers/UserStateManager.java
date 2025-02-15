package com.memeswap.bot.handlers;

import java.util.HashMap;
import java.util.Map;

public class UserStateManager {
    private final Map<Long, UserState> userStates = new HashMap<>();

    public enum UserState {
        AWAITING_CAPTION
    }

    public void setState(long userId, UserState state) {
        userStates.put(userId, state);
    }

    public UserState getState(long userId) {
        return userStates.getOrDefault(userId, null);
    }

    public void clearState(long userId) {
        userStates.remove(userId);
    }
}