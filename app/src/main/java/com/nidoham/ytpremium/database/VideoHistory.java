package com.nidoham.ytpremium.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "video_history", indices = {@Index(value = "videoId", unique = true)})
public class VideoHistory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String videoId;      // YouTube video ID (e.g., dQw4w9WgXcQ)
    public long timestamp;      // When it was played

    public VideoHistory(String videoId) {
        this.videoId = videoId;
        this.timestamp = System.currentTimeMillis();
    }
}