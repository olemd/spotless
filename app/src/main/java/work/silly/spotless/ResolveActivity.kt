package work.silly.spotless

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Tar imot Spotify-lenker (VIEW og "Del"), oversetter dem og sender videre til YT Music.
 * Vises som en liten dialog med fremdrift, og lukker seg selv.
 */
class ResolveActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var actions: LinearLayout
    @Volatile private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        buildUi()

        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (text.isNullOrBlank()) return fail("Ingen lenke mottatt", null)

        val resolver = Ytm.resolver(this)
        Thread {
            val outcome = runCatching {
                resolver.resolve(text) { msg -> runOnUiThread { status.text = msg } }
            }.getOrElse { Outcome.Fail(it.message ?: it.javaClass.simpleName) }
            runOnUiThread {
                if (cancelled || isFinishing) return@runOnUiThread
                when (outcome) {
                    is Outcome.Open -> open(outcome)
                    is Outcome.Fail -> fail(outcome.message, text.takeIf { it.startsWith("http") })
                }
            }
        }.start()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun open(o: Outcome.Open) {
        Toast.makeText(applicationContext, o.note, Toast.LENGTH_SHORT).show()
        val uri = Uri.parse(o.url)
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(Intent(view).setPackage(Ytm.PACKAGE))
        } catch (_: ActivityNotFoundException) {
            // YT Music ikke installert (eller håndterer ikke denne URL-en): la systemet velge.
            startActivity(view)
        }
        finish()
    }

    private fun fail(msg: String, originalUrl: String?) {
        spinner.visibility = android.view.View.GONE
        status.text = msg
        actions.removeAllViews()
        if (originalUrl != null) actions.addView(Button(this).apply {
            text = "Åpne i nettleser"
            setOnClickListener { openElsewhere(originalUrl) }
        })
        actions.addView(Button(this).apply {
            text = "Lukk"
            setOnClickListener { finish() }
        })
    }

    /** Åpner originalen uten å havne i denne appen igjen. */
    private fun openElsewhere(url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val chooser = Intent.createChooser(view, "Åpne med").putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, ResolveActivity::class.java)),
        )
        startActivity(chooser)
        finish()
    }

    private fun buildUi() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        spinner = ProgressBar(this)
        status = TextView(this).apply {
            text = "Starter …"
            textSize = 16f
            setPadding(pad, 0, 0, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(spinner)
            addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(Button(this@ResolveActivity).apply {
                text = "Avbryt"
                setOnClickListener { finish() }
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            minimumWidth = (300 * resources.displayMetrics.density).toInt()
            addView(row)
            addView(actions)
        })
    }
}
