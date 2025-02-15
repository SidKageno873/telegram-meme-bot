package com.memeswap.bot.models;

public class Meme {
    private final int id;
    private final String fileId;
    private final String caption;
    private final long userId;
    private final int likes;

    public Meme(int id, String fileId, String caption, long userId, int likes) {
        this.id = id;
        this.fileId = fileId;
        this.caption = caption;
        this.userId = userId;
        this.likes = likes;
    }

    public int getId() { return id; }
    public String getFileId() { return fileId; }
    public String getCaption() { return caption; }
    public int getLikes() { return likes; }
}