package com.repopilot.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class RepoRecord(val id: Long, val name: String, val url: String, val path: String, val branch: String, val lastScan: Long)
data class HistoryRecord(val id: Long, val repoId: Long, val kind: String, val message: String, val createdAt: Long)

class RepoDb(context: Context) : SQLiteOpenHelper(context, "repopilot.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE repositories(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,url TEXT NOT NULL UNIQUE,path TEXT NOT NULL,branch TEXT NOT NULL DEFAULT 'main',last_scan INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,repo_id INTEGER NOT NULL,kind TEXT NOT NULL,message TEXT NOT NULL,created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun upsertRepo(name: String, url: String, path: String): Long {
        val existing = readableDatabase.rawQuery("SELECT id FROM repositories WHERE url=?", arrayOf(url)).use { if (it.moveToFirst()) it.getLong(0) else null }
        val cv = ContentValues().apply { put("name", name); put("url", url); put("path", path) }
        return if (existing != null) { writableDatabase.update("repositories", cv, "id=?", arrayOf(existing.toString())); existing }
        else writableDatabase.insertOrThrow("repositories", null, cv)
    }

    fun repos(): List<RepoRecord> = readableDatabase.rawQuery("SELECT id,name,url,path,branch,last_scan FROM repositories ORDER BY id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(RepoRecord(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5))) }
    }

    fun setBranch(id: Long, branch: String) { writableDatabase.execSQL("UPDATE repositories SET branch=? WHERE id=?", arrayOf(branch, id)) }
    fun touchScan(id: Long) { writableDatabase.execSQL("UPDATE repositories SET last_scan=? WHERE id=?", arrayOf(System.currentTimeMillis(), id)) }
    fun addHistory(repoId: Long, kind: String, message: String) {
        val cv = ContentValues().apply { put("repo_id", repoId); put("kind", kind); put("message", message.take(4000)); put("created_at", System.currentTimeMillis()) }
        writableDatabase.insert("history", null, cv)
    }
    fun history(repoId: Long): List<HistoryRecord> = readableDatabase.rawQuery("SELECT id,repo_id,kind,message,created_at FROM history WHERE repo_id=? ORDER BY id DESC LIMIT 100", arrayOf(repoId.toString())).use { c ->
        buildList { while (c.moveToNext()) add(HistoryRecord(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getLong(4))) }
    }
}
