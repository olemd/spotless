package work.silly.spotless

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/** Spotify track ID → YouTube video ID. The same song across playlists is only looked up once. */
class SqliteCache(ctx: Context) : SQLiteOpenHelper(ctx, "matches.db", null, 1), MatchCache {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE m (spotify_id TEXT PRIMARY KEY, video_id TEXT NOT NULL, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    override fun get(spotifyId: String): String? =
        readableDatabase.rawQuery("SELECT video_id FROM m WHERE spotify_id = ?", arrayOf(spotifyId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    override fun put(spotifyId: String, videoId: String) {
        writableDatabase.insertWithOnConflict("m", null, ContentValues().apply {
            put("spotify_id", spotifyId)
            put("video_id", videoId)
            put("ts", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM m", null).use { it.moveToFirst(); it.getLong(0) }
    fun clear() = writableDatabase.execSQL("DELETE FROM m")
}

/** Settings with sensible defaults. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("spotless", Context.MODE_PRIVATE)

    /** 1–50. YouTube's anonymous queue takes at most 50. */
    var maxQueue: Int
        get() = p.getInt("max_queue", 50).coerceIn(1, 50)
        set(v) = p.edit().putInt("max_queue", v.coerceIn(1, 50)).apply()

    /** Country for the YT Music search (affects which releases are available). Defaults to the device locale. */
    var region: String
        get() = p.getString("region", null) ?: defaultRegion()
        set(v) = p.edit().putString("region", v.uppercase().take(2)).apply()

    private fun defaultRegion() = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    /** Re-fetches the YT Music client version at most once a week. */
    fun ytmVersion(http: Http): String? {
        val age = System.currentTimeMillis() - p.getLong("ytm_version_ts", 0)
        if (age > 7L * 24 * 3600 * 1000) {
            YtmClient.fetchCurrentVersion(http)?.let {
                p.edit().putString("ytm_version", it).putLong("ytm_version_ts", System.currentTimeMillis()).apply()
            }
        }
        return p.getString("ytm_version", null)
    }
}

object Ytm {
    const val PACKAGE = "com.google.android.apps.youtube.music"

    fun resolver(ctx: Context): Resolver {
        val http = Http()
        val prefs = Prefs(ctx)
        val version by lazy { prefs.ytmVersion(http) }
        return Resolver(
            http = http,
            ytm = YtmClient(http, { version }, prefs.region),
            cache = SqliteCache(ctx),
            maxQueue = prefs.maxQueue,
        )
    }
}
