package com.nidoham.ytpremium.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VideoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(VideoHistory history);

    @Query("SELECT videoId FROM video_history")
    List<String> getAllVideoIds();

    @Query("DELETE FROM video_history WHERE timestamp < :olderThan")
    void deleteOlderThan(long olderThan); // Optional: cleanup old history
}