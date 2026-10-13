package work.silly.spotless

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup: link handling, settings, cache and a test field. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var cache: SqliteCache
    private lateinit var cacheInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        cache = SqliteCache(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun heading(s: String) = root.addView(TextView(this).apply {
            text = s; textSize = 18f; setPadding(0, pad, 0, pad / 2)
        })
        fun body(s: String) = root.addView(TextView(this).apply { text = s; textSize = 14f })
        fun button(s: String, f: () -> Unit) = root.addView(Button(this).apply { text = s; setOnClickListener { f() } })

        heading("1. Let the app open Spotify links")
        body("Android 12+ doesn't let an app take over open.spotify.com automatically. Tap below, choose " +
            "“Add link” and tick open.spotify.com, spotify.link and spoti.fi.\n\n" +
            "If you have Spotify installed, turn the same thing off for Spotify first.\n\n" +
            "“Share → Spotless” always works, no setup needed.")
        button("Open “Open by default”") { openByDefaultSettings() }

        heading("2. Settings")
        body("Max tracks in the queue (1–50, YouTube limit is 50):")
        val maxQ = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.maxQueue.toString())
        }
        root.addView(maxQ)
        body("Search region (country code, affects which releases are available):")
        val region = EditText(this).apply { setText(prefs.region) }
        root.addView(region)
        button("Save") {
            maxQ.text.toString().toIntOrNull()?.let { prefs.maxQueue = it }
            region.text.toString().trim().takeIf { it.length == 2 }?.let { prefs.region = it }
            maxQ.setText(prefs.maxQueue.toString()); region.setText(prefs.region)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        heading("3. Cache")
        cacheInfo = TextView(this)
        root.addView(cacheInfo)
        button("Clear cache") { cache.clear(); refreshCache() }

        heading("Test")
        val test = EditText(this).apply { hint = "Paste a Spotify link" }
        root.addView(test)
        button("Translate") {
            startActivity(Intent(this, ResolveActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, test.text.toString()))
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshCache()
    }

    private fun refreshCache() {
        cacheInfo.text = "${cache.count()} matched tracks stored"
    }

    private fun openByDefaultSettings() {
        val pkg = Uri.parse("package:$packageName")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
    }
}
