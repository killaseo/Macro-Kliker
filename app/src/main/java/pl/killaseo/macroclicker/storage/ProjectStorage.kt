package pl.killaseo.macroclicker.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import pl.killaseo.macroclicker.model.Macro
import pl.killaseo.macroclicker.model.Project
import java.io.File

class ProjectStorage(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val baseDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    // ── Projekty ───────────────────────────────────────────────────

    fun saveProject(project: Project) {
        val dir = File(baseDir, project.id).also { it.mkdirs() }
        val file = File(dir, "project.json")
        file.writeText(gson.toJson(project))
    }

    fun loadProject(projectId: String): Project? {
        val file = File(baseDir, "$projectId/project.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), Project::class.java)
        } catch (e: Exception) { null }
    }

    fun loadAllProjects(): List<Project> {
        return baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadProject(it.name) }
            ?: emptyList()
    }

    fun deleteProject(projectId: String) {
        File(baseDir, projectId).deleteRecursively()
    }

    // ── Makra ──────────────────────────────────────────────────────

    fun saveMacro(projectId: String, macro: Macro) {
        val project = loadProject(projectId) ?: return
        val idx = project.macros.indexOfFirst { it.id == macro.id }
        if (idx >= 0) project.macros[idx] = macro
        else project.macros.add(macro)
        saveProject(project)
    }

    fun deleteMacro(projectId: String, macroId: String) {
        val project = loadProject(projectId) ?: return
        project.macros.removeAll { it.id == macroId }
        saveProject(project)
    }

    fun findMacroById(macroId: String): Macro? {
        return loadAllProjects()
            .flatMap { it.macros }
            .find { it.id == macroId }
    }

    // ── Eksport / Import JSON ──────────────────────────────────────

    fun exportProjectToJson(projectId: String): String? {
        val project = loadProject(projectId) ?: return null
        return gson.toJson(project)
    }

    fun importProjectFromJson(json: String): Project? {
        return try {
            val project = gson.fromJson(json, Project::class.java)
            saveProject(project)
            project
        } catch (e: Exception) { null }
    }
}
