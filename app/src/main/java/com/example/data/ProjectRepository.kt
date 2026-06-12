package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<KaraokeProject>> = projectDao.getAllProjects()

    fun getProjectFlow(id: Int): Flow<KaraokeProject?> {
        return projectDao.getProjectByIdFlow(id)
    }

    suspend fun getProjectById(id: Int): KaraokeProject? {
        return projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: KaraokeProject): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: KaraokeProject) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: KaraokeProject) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteProjectById(id: Int) {
        projectDao.deleteProjectById(id)
    }
}
