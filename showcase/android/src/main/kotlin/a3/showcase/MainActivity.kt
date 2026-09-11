package a3.showcase

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.decorView.setBackgroundColor(0xFFFFFFFF.toInt())
        val level = ShowcaseLevel.parse(intent.getStringExtra(EXTRA_LEVEL))
        val reduced = intent.getBooleanExtra(EXTRA_REDUCED, false)
        val talkback = intent.getBooleanExtra(EXTRA_TALKBACK, false)
        val tour = intent.getBooleanExtra(EXTRA_RECORD, false)
        val journal = ShowcaseJournal()
        val sink = AndroidShowcaseSink(this, journal)
        setContent {
            ShowcaseApp(
                formFactor = "phone",
                initialLevel = level,
                initialReduced = reduced,
                initialTalkback = talkback,
                tour = tour,
                journal = journal,
                sink = sink,
                onFlush = { j -> sink.flush(this, j) }
            )
        }
    }

    companion object {
        const val EXTRA_LEVEL = "a3_level"
        const val EXTRA_REDUCED = "a3_reduced_motion"
        const val EXTRA_TALKBACK = "a3_talkback"
        const val EXTRA_RECORD = "a3_record"
    }
}
