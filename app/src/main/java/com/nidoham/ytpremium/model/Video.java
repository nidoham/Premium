package com.nidoham.ytpremium.model;

public class Video {
    private String videoUrl;
    private String title;
    private String description;

    public Video(String videoUrl, String title, String description) {
        this.videoUrl = videoUrl;
        this.title = title;
        this.description = description;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}