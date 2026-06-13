package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM karaoke_projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<KaraokeProject>>

    @Query("SELECT * FROM karaoke_projects WHERE id = :id")
    suspend fun getProjectById(id: Int): KaraokeProject?

    @Query("SELECT * FROM karaoke_projects WHERE id = :id")
    fun getProjectByIdFlow(id: Int): Flow<KaraokeProject?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: KaraokeProject): Long

    @Update
    suspend fun updateProject(project: KaraokeProject)

    @Delete
    suspend fun deleteProject(project: KaraokeProject)

    @Query("DELETE FROM karaoke_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)
}
