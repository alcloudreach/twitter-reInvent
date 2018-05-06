package com.cloudreach.hackathon.message.handlers;

import java.util.Date;

public class TweetDto {
    private long id;
    private String text;
    private boolean isRetweet;
    private String lang;
    private Date createdAt;

    public TweetDto() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isRetweet() {
        return isRetweet;
    }

    public void setRetweet(boolean retweet) {
        isRetweet = retweet;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TweetDto{" +
                "id=" + id +
                ", text='" + text + '\'' +
                ", isRetweet=" + isRetweet +
                ", lang='" + lang + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
