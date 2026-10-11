package work.silly.spotless

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Spotify track-ID → YouTube video-ID. Samme sang i flere spillelister søkes bare opp én gang. */
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

/** Innstillinger med fornuftige defaults. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("spotless", Context.MODE_PRIVATE)

    /** 1–50. YouTube sin anonyme kø tar maks 50. */
    var maxQueue: Int
        get() = p.getInt("max_queue", 50).coerceIn(1, 50)
        set(v) = p.edit().putInt("max_queue", v.coerceIn(1, 50)).apply()

    /** Land for YT Music-søket (påvirker hvilke utgivelser som er tilgjengelige). */
    var region: String
        get() = p.getString("region", "NO")!!
        set(v) = p.edit().putString("region", v.uppercase().take(2)).apply()

    /** Henter YT Music-klientversjon på nytt maks én gang i uka. */
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
