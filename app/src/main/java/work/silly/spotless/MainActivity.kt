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

/** Oppsett: lenkehåndtering, innstillinger, cache og et testfelt. */
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

        heading("1. La appen åpne Spotify-lenker")
        body("Android 12+ lar ikke en app ta over open.spotify.com automatisk. Trykk under, velg " +
            "«Legg til lenker» og huk av open.spotify.com, spotify.link og spoti.fi.\n\n" +
            "Har du Spotify installert, må du skru av det samme for Spotify først.\n\n" +
            "«Del → Spotless» fungerer uansett, uten oppsett.")
        button("Åpne «Åpne som standard»") { openByDefaultSettings() }

        heading("2. Innstillinger")
        body("Maks antall spor i køen (1–50, YouTube-grense 50):")
        val maxQ = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.maxQueue.toString())
        }
        root.addView(maxQ)
        body("Region for søk (landkode, påvirker tilgjengelige utgivelser):")
        val region = EditText(this).apply { setText(prefs.region) }
        root.addView(region)
        button("Lagre") {
            maxQ.text.toString().toIntOrNull()?.let { prefs.maxQueue = it }
            region.text.toString().trim().takeIf { it.length == 2 }?.let { prefs.region = it }
            maxQ.setText(prefs.maxQueue.toString()); region.setText(prefs.region)
            Toast.makeText(this, "Lagret", Toast.LENGTH_SHORT).show()
        }

        heading("3. Cache")
        cacheInfo = TextView(this)
        root.addView(cacheInfo)
        button("Tøm cache") { cache.clear(); refreshCache() }

        heading("Test")
        val test = EditText(this).apply { hint = "Lim inn Spotify-lenke" }
        root.addView(test)
        button("Oversett") {
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
        cacheInfo.text = "${cache.count()} matchede spor lagret"
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
