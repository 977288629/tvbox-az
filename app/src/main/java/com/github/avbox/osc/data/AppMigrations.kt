package com.github.avbox.osc.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val VodFollowCreateSql = "CREATE TABLE IF NOT EXISTS `vodFollow` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vodId` TEXT, `sourceKey` TEXT, `cid` TEXT, `name` TEXT, `pic` TEXT, `addedTime` INTEGER NOT NULL, `updateDays` TEXT NOT NULL, `updateHour` INTEGER NOT NULL)"

private const val DownloadTaskCreateSql = "CREATE TABLE IF NOT EXISTS `downloadTask` (" +
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    "`cid` TEXT, `sourceKey` TEXT, `vodId` TEXT, `vodName` TEXT, `vodPic` TEXT, " +
    "`episodeName` TEXT, `episodeIndex` INTEGER NOT NULL, `episodeUrl` TEXT, " +
    "`downloadType` INTEGER NOT NULL, `state` INTEGER NOT NULL, " +
    "`downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, " +
    "`filePath` TEXT, `errorMessage` TEXT, `retryCount` INTEGER NOT NULL, " +
    "`createTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL)"

private const val DownloadTaskAddPlayFlagSql = "ALTER TABLE `downloadTask` ADD COLUMN `playFlag` TEXT"

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(VodFollowCreateSql)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(DownloadTaskCreateSql)
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(DownloadTaskAddPlayFlagSql)
    }
}
