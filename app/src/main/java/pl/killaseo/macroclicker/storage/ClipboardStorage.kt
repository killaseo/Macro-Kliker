package pl.killaseo.macroclicker.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ClipboardStorage(context: Context) : SQLiteOpenHelper(
    context, DB_NAME, null, DB_VERSION
) {

    companion object {
        const val DB_NAME    = "clipboard.db"
        const val DB_VERSION = 1

        // Rozmiar jednego chunka — 64KB to bezpieczny limit dla SQLite
        const val CHUNK_SIZE = 65536

        // Domyślny limit rozmiaru bazy — 10MB
        const val DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024L
    }

    // ── Schema ─────────────────────────────────────────────────────

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE clipboard_buffer (
                id            TEXT NOT NULL,
                project_id    TEXT NOT NULL,
                macro_id      TEXT,
                step_id       TEXT,
                variable_name TEXT,
                chunk_index   INTEGER NOT NULL DEFAULT 0,
                total_chunks  INTEGER NOT NULL DEFAULT 1,
                content       TEXT NOT NULL,
                created_at    INTEGER NOT NULL,
                PRIMARY KEY (id, chunk_index)
            )
        """)

        // Indeks do szybkiego wyszukiwania po projekcie
        db.execSQL("""
            CREATE INDEX idx_project 
            ON clipboard_buffer(project_id, created_at)
        """)

        // Indeks do wyszukiwania po zmiennej
        db.execSQL("""
            CREATE INDEX idx_variable 
            ON clipboard_buffer(project_id, variable_name)
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS clipboard_buffer")
        onCreate(db)
    }

    // ── Zapis ──────────────────────────────────────────────────────

    fun save(
        projectId:    String,
        variableName: String,
        content:      String,
        macroId:      String? = null,
        stepId:       String? = null
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val db  = writableDatabase

        // Podziel na chunki jeśli tekst jest długi
        val chunks = content.chunked(CHUNK_SIZE)

        db.beginTransaction()
        try {
            chunks.forEachIndexed { index, chunk ->
                val values = ContentValues().apply {
                    put("id",            id)
                    put("project_id",    projectId)
                    put("macro_id",      macroId)
                    put("step_id",       stepId)
                    put("variable_name", variableName)
                    put("chunk_index",   index)
                    put("total_chunks",  chunks.size)
                    put("content",       chunk)
                    put("created_at",    now)
                }
                db.insert("clipboard_buffer", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return id
    }

    // ── Odczyt ─────────────────────────────────────────────────────

    fun load(projectId: String, variableName: String): String? {
        val db = readableDatabase

        // Pobierz najnowszy wpis dla tej zmiennej
        val cursor = db.query(
            "clipboard_buffer",
            arrayOf("id", "chunk_index", "total_chunks", "content"),
            "project_id = ? AND variable_name = ?",
            arrayOf(projectId, variableName),
            null, null,
            "created_at DESC, chunk_index ASC"
        )

        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        // Zbierz wszystkie chunki i sklej
        val sb = StringBuilder()
        do {
            sb.append(cursor.getString(cursor.getColumnIndexOrThrow("content")))
        } while (cursor.moveToNext())

        cursor.close()
        return sb.toString()
    }

    fun loadById(entryId: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            "clipboard_buffer",
            arrayOf("content"),
            "id = ?",
            arrayOf(entryId),
            null, null,
            "chunk_index ASC"
        )

        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        val sb = StringBuilder()
        do { sb.append(cursor.getString(0)) } while (cursor.moveToNext())
        cursor.close()
        return sb.toString()
    }

    // ── Czyszczenie ────────────────────────────────────────────────

    /** Wyczyść wszystkie dane danego projektu */
    fun clearProject(projectId: String): Int {
        return writableDatabase.delete(
            "clipboard_buffer",
            "project_id = ?",
            arrayOf(projectId)
        )
    }

    /** Wyczyść konkretną zmienną w projekcie */
    fun clearVariable(projectId: String, variableName: String): Int {
        return writableDatabase.delete(
            "clipboard_buffer",
            "project_id = ? AND variable_name = ?",
            arrayOf(projectId, variableName)
        )
    }

    /** Usuń wpisy starsze niż X milisekund */
    fun clearOlderThan(projectId: String, olderThanMs: Long): Int {
        val cutoff = System.currentTimeMillis() - olderThanMs
        return writableDatabase.delete(
            "clipboard_buffer",
            "project_id = ? AND created_at < ?",
            arrayOf(projectId, cutoff.toString())
        )
    }

    /** Wyczyść całą bazę */
    fun clearAll(): Int {
        return writableDatabase.delete("clipboard_buffer", null, null)
    }

    // ── Statystyki rozmiaru ────────────────────────────────────────

    fun getDatabaseSizeBytes(context: Context): Long {
        return context.getDatabasePath(DB_NAME).length()
    }

    fun getProjectSizeBytes(projectId: String): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM(LENGTH(content)) FROM clipboard_buffer WHERE project_id = ?",
            arrayOf(projectId)
        )
        val size = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        return size
    }

    fun getRowCount(projectId: String): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT id) FROM clipboard_buffer WHERE project_id = ?",
            arrayOf(projectId)
        )
        val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        return count
    }

    // ── Auto-cleanup gdy przekroczony limit ───────────────────────

    /**
     * Wywołaj przed każdym save() — usuwa najstarsze wpisy
     * jeśli baza przekroczyła maxSizeBytes
     */
    fun autoCleanup(
        projectId: String,
        maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES
    ) {
        if (getProjectSizeBytes(projectId) < maxSizeBytes) return

        // Usuń 20% najstarszych wpisów tego projektu
        writableDatabase.execSQL("""
            DELETE FROM clipboard_buffer 
            WHERE project_id = ? 
            AND id IN (
                SELECT DISTINCT id FROM clipboard_buffer
                WHERE project_id = ?
                ORDER BY created_at ASC
                LIMIT (
                    SELECT COUNT(DISTINCT id) / 5 
                    FROM clipboard_buffer 
                    WHERE project_id = ?
                )
            )
        """, arrayOf(projectId, projectId, projectId))

        // VACUUM — zwróć miejsce na dysku
        writableDatabase.execSQL("VACUUM")
    }

    // ── Historia ───────────────────────────────────────────────────

    data class ClipboardEntry(
        val id: String,
        val variableName: String,
        val preview: String,     // pierwsze 100 znaków
        val totalChunks: Int,
        val createdAt: Long
    )

    fun getHistory(projectId: String, limit: Int = 50): List<ClipboardEntry> {
        val cursor = readableDatabase.rawQuery("""
            SELECT id, variable_name, 
                   SUBSTR(content, 1, 100) as preview,
                   total_chunks, created_at
            FROM clipboard_buffer
            WHERE project_id = ? AND chunk_index = 0
            ORDER BY created_at DESC
            LIMIT ?
        """, arrayOf(projectId, limit.toString()))

        val list = mutableListOf<ClipboardEntry>()
        while (cursor.moveToNext()) {
            list.add(ClipboardEntry(
                id           = cursor.getString(0),
                variableName = cursor.getString(1),
                preview      = cursor.getString(2),
                totalChunks  = cursor.getInt(3),
                createdAt    = cursor.getLong(4)
            ))
        }
        cursor.close()
        return list
    }
}
