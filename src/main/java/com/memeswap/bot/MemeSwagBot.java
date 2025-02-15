package com.memeswap.bot;

import com.memeswap.bot.database.DatabaseHandler;
import com.memeswap.bot.handlers.*;
import com.memeswap.bot.models.Meme;
import com.memeswap.bot.utils.KeyboardFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.util.*;

public class MemeSwagBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(MemeSwagBot.class);
    private final Map<Long, UserSession> userSessions = new HashMap<>();
    private final UserStateManager userStateManager = new UserStateManager();


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        long userId = message.getFrom().getId();
        long chatId = message.getChatId();
        UserSession session = userSessions.computeIfAbsent(chatId, k -> new UserSession());

        try {
            if (message.hasText()) {
                String text = message.getText();
                switch (text) {
                    case "/start":
                        sendWelcomeMessage(chatId);
                        break;

                    case "🖼️ Загрузить мем":
                        userStateManager.setState(userId, UserStateManager.UserState.AWAITING_CAPTION);
                        sendMessage(chatId, "📎 Отправьте изображение с подписью в формате #Название");
                        break;

                    case "👤 Профиль":
                        sendUserProfile(chatId, userId);
                        break;

                    case "⭐ Избранное":
                        startFavoritesFeed(chatId, userId);
                        break;

                    case "📜 Лента мемов":
                        startMemeFeed(chatId);
                        break;

                    case "📂 Мои мемы":
                        startUserMemeFeed(chatId, userId);
                        break;

                    case "⬅️ Назад":
                        showPreviousMeme(chatId);
                        break;

                    case "➡️ Вперед":
                        showNextMeme(chatId);
                        break;

                    case "❤️ Лайк":
                        handleLike(userId, session.getMemeIds().get(session.getCurrentMemeIndex()));
                        break;

                    case "🚩 Пожаловаться":
                        handleReport(userId, session.getMemeIds().get(session.getCurrentMemeIndex()));
                        break;

                    case "🏠 Главное меню":
                        sendMainMenu(chatId);
                        break;

                    case "↩️ Вернуться":
                        if (session.getCurrentFeedType() == UserSession.FeedType.AUTHOR) {
                            session.returnToPreviousFeed();
                            showCurrentMeme(chatId);
                        }
                        break;
                }
            } else if (message.hasPhoto()) {
                handleMemeUpload(message, chatId, userId);
            }
        } catch (Exception e) {
            logger.error("Ошибка обработки сообщения: ", e);
            sendMessage(chatId, "❌ Произошла ошибка, попробуйте позже");
        }
    }

    private void handleLike(long userId, int memeId) {
        if (DatabaseHandler.hasUserLikedMeme(userId, memeId)) {
            sendPopupNotification(userId, "Вы уже ставили лайк этому мему");
        } else {
            DatabaseHandler.addLike(userId, memeId);
            sendPopupNotification(userId, "❤️ Лайк добавлен!");
        }
    }

    private void handleReport(long userId, int memeId) {
        if (DatabaseHandler.hasUserReportedMeme(userId, memeId)) {
            sendPopupNotification(userId, "Вы уже жаловались на этот мем)");
        } else {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(userId));
            message.setText("Выберите причину жалобы:");
            message.setReplyMarkup(createReportReasonsKeyboard(memeId));
            executeSilently(message);
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Вы вернулись в главное меню. Выберите действие");
        message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
        executeSilently(message);
    }

    private void startUserMemeFeed(long chatId, long userId) {
        UserSession session = userSessions.get(chatId);
        session.savePreviousState();

        List<Integer> userMemeIds = DatabaseHandler.getMemesByUserId(userId);
        if (userMemeIds.isEmpty()) {
            sendMessage(chatId, "😔 У вас пока нет мемов");
            session.restorePreviousState();
            return;
        }

        session.setCurrentFeedType(UserSession.FeedType.USER);
        session.setMemeIds(userMemeIds);
        session.setCurrentMemeIndex(0);

        showCurrentMeme(chatId);
    }

    private void handleMemeUpload(Message message, long chatId, long userId) {
        if (userStateManager.getState(userId) == UserStateManager.UserState.AWAITING_CAPTION) {
            String caption = message.getCaption();
            if (!ValidationUtils.isValidCaption(caption)) {

                sendMessage(chatId, "❌ Неверный формат подписи! Название к мему не должно:\n" +
                        "\n1. Содержать 1000 символов" +
                        "\n2. Содержать символы: @, //" +
                        "\n3. Содержать любые ссылки" +
                        "\n4. Должно начинаться на #" +
                        "\n\nПример: #FunnyMeme");

                return;
            }
            // Добавляем/обновляем пользователя
            DatabaseHandler.addUserIfNotExists(
                    userId,
                    message.getFrom().getUserName(),
                    message.getFrom().getFirstName()
            );

            // Сохраняем мем

            String fileId = message.getPhoto().get(0).getFileId();
            DatabaseHandler.saveMeme(userId, fileId, caption);

            sendMessage(chatId, "✅ Мем успешно загружен!");
            userStateManager.clearState(userId);
        }
    }

    public class ValidationUtils {
        public static boolean isValidCaption(String caption) {
            return caption != null &&
                    caption.startsWith("#") &&
                    caption.length() <= 1000 &&
                    !caption.contains("@") &&
                    !caption.contains("//") &&
                    !caption.matches(".*http[s]?://.*");
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🌟 Добро пожаловать в MemeSwap! 🌟\n\n" +
                "Здесь вы можете выкладывать, смотреть и оценивать мемы/новости\n" +
                "Выберите действие из меню ниже");
        message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
        executeSilently(message);
    }

    private void sendUserProfile(long chatId, long userId) {
        SendMessage profile = UserProfileHandler.handleProfileCommand(userId);
        profile.setChatId(String.valueOf(chatId));
        executeSilently(profile);
    }

    private void startMemeFeed(long chatId) {
        UserSession session = userSessions.get(chatId);
        session.savePreviousState();

        List<Integer> memeIds = DatabaseHandler.getAllMemeIdsWeighted(session.getUserId());
        if (memeIds.isEmpty()) {
            sendMessage(chatId, "😔 Пока нет мемов");
            return;
        }

        session.setCurrentFeedType(UserSession.FeedType.MAIN);
        session.setMemeIds(memeIds);
        session.setCurrentMemeIndex(0);

        showCurrentMeme(chatId);
    }

    private void showNextMeme(long chatId) {
        UserSession session = userSessions.get(chatId);
        if (session.getMemeIds().isEmpty()) {
            sendMessage(chatId, "😔 Пока нет мемов:/ Будь первым!");
            return;
        }

        int nextIndex = session.getCurrentMemeIndex() + 1;
        if (nextIndex >= session.getMemeIds().size()) {
            if (session.getCurrentFeedType() != UserSession.FeedType.MAIN) {
                sendPopupNotification(chatId, "Вы достигли конца списка");
                return;
            }
            nextIndex = 0;
        }

        Meme meme = DatabaseHandler.getMemeById(session.getMemeIds().get(nextIndex));
        if (meme == null) {
            session.setMemeIds(DatabaseHandler.getAllMemeIds());
            showNextMeme(chatId);
            return;
        }

        DatabaseHandler.addView(session.getUserId(), meme.getId());

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(meme.getFileId()));
        photo.setCaption(createMemeCaption(meme));
        photo.setReplyMarkup(createMemeKeyboard(meme.getId(), session));

        executeSilently(photo);
        session.setCurrentMemeIndex(nextIndex);

        // Обновляем меню с учетом типа ленты
        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(String.valueOf(chatId));
        menuMessage.setText("Используйте кнопки для управления:");
        menuMessage.setReplyMarkup(KeyboardFactory.getMemeMenuKeyboard(session.getCurrentFeedType()));
        executeSilently(menuMessage);
    }

    private void showPreviousMeme(long chatId) {
        UserSession session = userSessions.get(chatId);
        if (session.getMemeIds().isEmpty()) {
            sendMessage(chatId, "😔 Пока нет мемов:/ Будь первым!");
            return;
        }

        int prevIndex = session.getCurrentMemeIndex() - 1;
        if (prevIndex < 0) {
            if (session.getCurrentFeedType() != UserSession.FeedType.MAIN) {
                sendPopupNotification(chatId, "Вы в начале списка");
                return;
            }
            prevIndex = session.getMemeIds().size() - 1;
        }

        Meme meme = DatabaseHandler.getMemeById(session.getMemeIds().get(prevIndex));
        if (meme == null) {
            session.setMemeIds(DatabaseHandler.getAllMemeIds());
            showPreviousMeme(chatId);
            return;
        }

        DatabaseHandler.addView(session.getUserId(), meme.getId());

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(meme.getFileId()));
        photo.setCaption(createMemeCaption(meme));
        photo.setReplyMarkup(createMemeKeyboard(meme.getId(), session));

        executeSilently(photo);
        session.setCurrentMemeIndex(prevIndex);

        // Обновляем меню с учетом типа ленты
        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(String.valueOf(chatId));
        menuMessage.setText("Используйте кнопки для управления:");
        menuMessage.setReplyMarkup(KeyboardFactory.getMemeMenuKeyboard(session.getCurrentFeedType()));
        executeSilently(menuMessage);
    }

    private InlineKeyboardMarkup createMemeKeyboard(int memeId, UserSession session) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопка избранного только для MAIN и AUTHOR
        if (session.getCurrentFeedType() == UserSession.FeedType.MAIN
                || session.getCurrentFeedType() == UserSession.FeedType.AUTHOR) {

            boolean isFavorite = DatabaseHandler.isMemeInFavorites(session.getUserId(), memeId);
            String favoriteText = isFavorite ? "❌ Удалить из избранного" : "⭐ Добавить в избранное";
            String callbackData = isFavorite ? "remove_favorite_" : "add_favorite_";

            keyboard.add(Collections.singletonList(
                    createInlineButton(favoriteText, callbackData + memeId)
            ));
        }

        if (session.getCurrentFeedType() == UserSession.FeedType.FAVORITES) {
            keyboard.add(Collections.singletonList(
                    createInlineButton("❌ Удалить из избранного", "remove_favorite_" + memeId)
            ));
        }

        // Кнопка профиля автора (только для основной ленты)
        if (session.getCurrentFeedType() == UserSession.FeedType.MAIN) {
            keyboard.add(Collections.singletonList(
                    createInlineButton("👤 Профиль автора", "author_profile_" + memeId) // Изменено название
            ));
        }

        // Кнопка удаления (только для своей ленты)
        if (session.getCurrentFeedType() == UserSession.FeedType.USER) {
            keyboard.add(Collections.singletonList(
                    createInlineButton("🗑️ Удалить", "delete_meme_" + memeId)
            ));
        }

        return new InlineKeyboardMarkup(keyboard);
    }

    private String createMemeCaption(Meme meme) {
        return String.format(
                "%s\n\nID: %d\n❤️ Лайков: %d\n👀 Просмотров: %d",
                meme.getCaption(),
                meme.getId(),
                meme.getLikes(),
                DatabaseHandler.getMemeViews(meme.getId())
        );
    }

    private void showAuthorProfileWithActions(long chatId, long authorTelegramId) {
        // Получаем данные автора
        com.memeswap.bot.models.User author = DatabaseHandler.getUserByTelegramId(authorTelegramId);
        if (author == null) {
            logger.error("Автор с Telegram ID {} не найден", authorTelegramId);
            sendMessage(chatId, "❌ Информация об авторе недоступна");
            return;
        }

        // Получаем статистику по внутреннему ID автора
        Map<String, Integer> stats = DatabaseHandler.getAuthorStats(author.getId());

        String profileText = String.format(
                "👤 Профиль автора:\n" +
                        "📛 Имя: %s\n" +
                        "📊 Всего мемов: %d\n" +
                        "❤️ Всего лайков: %d\n" +
                        "👀 Всего просмотров: %d",
                author.getName(),
                stats.getOrDefault("totalMemes", 0),
                stats.getOrDefault("totalLikes", 0),
                stats.getOrDefault("totalViews", 0)
        );

        // Создаем сообщение с кнопкой
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(profileText);

        // Добавляем кнопку для просмотра мемов автора
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(
                Collections.singletonList(
                        createInlineButton("📜 Просмотреть мемы автора",
                                "view_author_memes_" + authorTelegramId)
                )
        ));

        message.setReplyMarkup(markup);
        executeSilently(message);
    }

    private void startAuthorMemeFeed(long chatId, long authorTelegramId) {
        UserSession session = userSessions.get(chatId);
        session.savePreviousState();

        Integer authorId = DatabaseHandler.getUserIdByTelegramId(authorTelegramId);
        if (authorId == null) {
            sendMessage(chatId, "❌ Автор не найден");
            return;
        }

        List<Integer> authorMemeIds = DatabaseHandler.getUserMemeIds(authorId);
        if (authorMemeIds.isEmpty()) {
            sendMessage(chatId, "😔 У автора пока нет мемов");
            session.restorePreviousState();
            return;
        }

        session.setCurrentFeedType(UserSession.FeedType.AUTHOR);
        session.setMemeIds(authorMemeIds);
        session.setCurrentMemeIndex(0);
        session.setAuthorId(authorId);

        showCurrentMeme(chatId);
    }

    private void handleReport(long userId, int memeId, String reason) {
        if (DatabaseHandler.hasUserReportedMeme(userId, memeId)) {
            sendPopupNotification(userId, "Вы уже жаловались на этот мем");
        } else {
            DatabaseHandler.addReport(userId, memeId, reason);
            System.out.printf("[REPORT] UserID: %d | MemeID: %d | Reason: %s\n", userId, memeId, reason);
            sendPopupNotification(userId, "🚩 Жалоба зарегистрирована!");
        }
    }

    private void showCurrentMeme(long chatId) {
        UserSession session = userSessions.get(chatId);
        if (session.getCurrentMemeIndex() < 0 ||
                session.getCurrentMemeIndex() >= session.getMemeIds().size()) return;

        int memeId = session.getMemeIds().get(session.getCurrentMemeIndex());
        Meme meme = DatabaseHandler.getMemeById(memeId);

        if (meme == null || !DatabaseHandler.memeExists(memeId)) {
            session.getMemeIds().remove(Integer.valueOf(memeId));
            showNextMeme(chatId);
            return;
        }

        if (session.getCurrentMemeIndex() >= 0 &&
                session.getCurrentMemeIndex() < session.getMemeIds().size()) {
            if (meme == null) {
                // Если мем удален, обновляем список и показываем следующий
                session.getMemeIds().remove(Integer.valueOf(memeId));
                showNextMeme(chatId);
                return;
            }
            // Обновляем просмотры
            DatabaseHandler.addView(session.getUserId(), memeId);

            SendPhoto photo = new SendPhoto();
            photo.setChatId(String.valueOf(chatId));
            photo.setPhoto(new InputFile(meme.getFileId()));
            photo.setCaption(createMemeCaption(meme));
            photo.setReplyMarkup(createMemeKeyboard(memeId, session));

            executeSilently(photo);

            // Обновляем меню
            SendMessage menu = new SendMessage();
            menu.setChatId(String.valueOf(chatId));
            menu.setText("Используйте кнопки для управления:");
            menu.setReplyMarkup(KeyboardFactory.getMemeMenuKeyboard(session.getCurrentFeedType()));
            executeSilently(menu);
        }
    }

    private void showCurrentAuthorMeme(long chatId) {
        UserSession session = userSessions.get(chatId);
        int memeId = session.getMemeIds().get(session.getCurrentMemeIndex());

        Meme meme = DatabaseHandler.getMemeById(memeId);
        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(meme.getFileId()));
        photo.setCaption(createMemeCaption(meme));

        // Специальная клавиатура для авторской ленты
        photo.setReplyMarkup(createAuthorMemeKeyboard());
        executeSilently(photo);
    }

    private void startFavoritesFeed(long chatId, long userId) {
        UserSession session = userSessions.get(chatId);
        session.savePreviousState();

        List<Integer> favoriteMemeIds = DatabaseHandler.getFavoriteMemeIds(userId)
                .stream()
                .filter(memeId -> DatabaseHandler.getMemeById(memeId) != null) // Фильтр удаленных мемов
                .toList();

        if (favoriteMemeIds.isEmpty()) {
            sendMessage(chatId, "😔 У вас пока нет избранных мемов");
            session.restorePreviousState();
            return;
        }

        session.setCurrentFeedType(UserSession.FeedType.FAVORITES);
        session.setMemeIds(new ArrayList<>(favoriteMemeIds)); // Обновляем список
        session.setCurrentMemeIndex(0);

        showCurrentMeme(chatId);
    }

    private InlineKeyboardMarkup createAuthorMemeKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопки навигации
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(createInlineButton("⬅️", "author_prev"));
        navRow.add(createInlineButton("➡️", "author_next"));
        keyboard.add(navRow);

        // Кнопка возврата
        keyboard.add(Collections.singletonList(
                createInlineButton("↩️ Вернуться к ленте", "return_to_feed")
        ));

        return new InlineKeyboardMarkup(keyboard);
    }

    private void handleAuthorNavigation(long chatId, String action) {
        UserSession session = userSessions.get(chatId);
        int currentIndex = session.getCurrentMemeIndex();

        switch (action) {
            case "author_prev":
                if (currentIndex > 0) {
                    session.setCurrentMemeIndex(currentIndex - 1);
                    showCurrentAuthorMeme(chatId);
                }
                break;
            case "author_next":
                if (currentIndex < session.getMemeIds().size() - 1) {
                    session.setCurrentMemeIndex(currentIndex + 1);
                    showCurrentAuthorMeme(chatId);
                }
                break;
        }
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        long userId = callbackQuery.getFrom().getId();
        UserSession session = userSessions.get(chatId);

        if (data == null || data.isEmpty()) {
            logger.warn("Пустой callback-запрос от пользователя {}", userId);
            return;
        }

        try {
            // Обработка лайков
            if (data.startsWith("like_")) {
                int memeId = Integer.parseInt(data.split("_")[1]);
                handleLike(userId, memeId);
            } else if (data.startsWith("delete_meme_")) {
                int memeId = Integer.parseInt(data.split("_")[2]);
                Integer internalUserId = DatabaseHandler.getUserIdByTelegramId(userId);

                if (internalUserId != null && DatabaseHandler.isMemeOwner(internalUserId, memeId)) {
                    DatabaseHandler.deleteMeme(memeId);
                    sendPopupNotification(chatId, "✅ Мем удален!");

                    // Обновляем список мемов в сессии
                    session.getUserFeed().remove(Integer.valueOf(memeId));

                    if (session.getUserFeed().isEmpty()) {
                        sendMainMenu(chatId);
                    } else {
                        showNextMeme(chatId); // Показываем следующий мем
                    }
                } else {
                    sendPopupNotification(chatId, "❌ Нет прав для удаления");
                }
            } else if (data.startsWith("report_")) {
                String[] parts = data.split("_");
                if (parts.length >= 3) {
                    int memeId = Integer.parseInt(parts[1]);
                    String reason = String.join("_", Arrays.copyOfRange(parts, 2, parts.length));
                    handleReport(userId, memeId, reason);
                }

                // Просмотр профиля автора
            } else if (data.startsWith("add_favorite_")) {
                int memeId = Integer.parseInt(data.split("_")[2]);

                if (!DatabaseHandler.memeExists(memeId)) {
                    sendPopupNotification(chatId, "❌ Мем не найден");
                    return;
                }

                if (DatabaseHandler.isMemeInFavorites(userId, memeId)) {
                    sendPopupNotification(chatId, "⭐ Этот мем уже в избранном!");
                } else {
                    DatabaseHandler.addToFavorites(userId, memeId);
                    sendPopupNotification(chatId, "⭐ Мем добавлен в избранное!");
                    editMessageWithNewKeyboard(chatId, callbackQuery.getMessage().getMessageId(), memeId, session);
                }
            }  else if (data.startsWith("remove_favorite_")) {
                int memeId = Integer.parseInt(data.split("_")[2]);
                DatabaseHandler.removeFromFavorites(userId, memeId);
                sendPopupNotification(chatId, "❌ Мем удален из избранного!");
                editMessageWithNewKeyboard(chatId, callbackQuery.getMessage().getMessageId(), memeId, session);
            }

            else if (data.startsWith("author_profile_")) {
                try {
                    int memeId = Integer.parseInt(data.substring("author_profile_".length()));
                    com.memeswap.bot.models.User author = DatabaseHandler.getAuthorByMemeId(memeId);

                    if (author != null) {
                        showAuthorProfileWithActions(chatId, author.getTelegramId());
                    } else {
                        logger.error("Автор для мема {} не найден", memeId);
                        sendPopupNotification(chatId, "❌ Информация об авторе недоступна");
                    }
                } catch (NumberFormatException e) {
                    logger.error("Неверный формат memeId: {}", data);
                }

                // Просмотр мемов автора
            } else if (data.startsWith("view_author_memes_")) {
                try {
                    // Исправляем получение authorTelegramId
                    long authorTelegramId = Long.parseLong(data.split("_")[3]);
                    Integer authorId = DatabaseHandler.getUserIdByTelegramId(authorTelegramId);

                    if (authorId != null) {
                        // Передаем корректный telegram_id автора
                        startAuthorMemeFeed(chatId, authorTelegramId);
                    } else {
                        sendPopupNotification(chatId, "❌ Автор не найден");
                    }
                } catch (Exception e) {
                    logger.error("Ошибка обработки запроса автора: {}", e.getMessage());
                }

                // Навигация по мемам автора
            } else if (data.startsWith("author_")) {
                String[] parts = data.split("_");
                if (parts.length == 2) {
                    handleAuthorNavigation(chatId, parts[1]);
                }

                // Возврат к основной ленте
            } else if (data.equals("return_to_feed")) {
                if (session != null) {
                    session.returnToPreviousFeed();
                    showCurrentMeme(chatId);
                }

                // Неизвестный callback
            } else {
                logger.warn("Неизвестный callback: {}", data);
                sendPopupNotification(chatId, "❌ Неизвестная команда");
            }

            // Подтверждаем обработку callback
            execute(new AnswerCallbackQuery(callbackQuery.getId()));

        } catch (Exception e) {
            logger.error("Ошибка обработки callback: {}", callbackQuery.getData(), e);
            sendPopupNotification(callbackQuery.getMessage().getChatId(), "❌ Ошибка обработки запроса");
        }
    }

    private void editMessageWithNewKeyboard(long chatId, int messageId, int memeId, UserSession session) {
        try {
            Meme meme = DatabaseHandler.getMemeById(memeId);
            if (meme == null) {
                logger.warn("Мем {} не найден, пропускаем обновление", memeId);
                return;
            }

            InlineKeyboardMarkup newKeyboard = createMemeKeyboard(memeId, session);

            EditMessageCaption edit = new EditMessageCaption();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setCaption(createMemeCaption(meme));
            edit.setReplyMarkup(newKeyboard);

            execute(edit);
            session.setCurrentKeyboard(newKeyboard);
            logger.debug("Клавиатура обновлена для мема {}", memeId);
        } catch (TelegramApiException e) {
            if (!e.getMessage().contains("message is not modified")) {
                logger.error("Ошибка обновления сообщения: ", e);
            }
        }
    }

    private void sendPopupNotification(long chatId, String text) {
        try {
            execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        executeSilently(message);
    }

    private <T extends Serializable> void executeSilently(BotApiMethod<T> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createReportReasonsKeyboard(int memeId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Новый формат callback данных
        keyboard.add(Collections.singletonList(
                createInlineButton("🚩 Неприемлемый контент", "report_" + memeId + "_1")
        ));
        keyboard.add(Collections.singletonList(
                createInlineButton("🚩 Нецензурная лексика", "report_" + memeId + "_2")
        ));
        keyboard.add(Collections.singletonList(
                createInlineButton("🚩 Спам", "report_" + memeId + "_3")
        ));
        keyboard.add(Collections.singletonList(
                createInlineButton("🚩 Другое", "report_" + memeId + "_4")
        ));

        return new InlineKeyboardMarkup(keyboard);
    }

    private void executeSilently(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void clearAllSessionsCache() {
        userSessions.values().forEach(UserSession::clearCache);
    }

    // Синглтон-паттерн для доступа к инстансу
    private static MemeSwagBot instance;
    public static MemeSwagBot getInstance() {
        return instance;
    }

    @Override
    public void onRegister() {
        instance = this;
    }

    @Override
    public String getBotUsername() {
        return "MemeSwagBot";
    }

    @Override
    public String getBotToken() {
        return "7937990194:AAHedzejkuIflo9yfP8G7l7KBcOVDqJkzIw"; // Замените на ваш токен
    }
}