// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class OverlayPermissionActivity : ComponentActivity() {
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val skipProjection = intent.getBooleanExtra(EXTRA_SKIP_PROJECTION, false)
        if (skipProjection && Settings.canDrawOverlays(this)) {
            launched = true
            OverlayService.start(
                this,
                projectionGranted = false,
                choose = intent.getStringExtra(OverlayService.EXTRA_CHOOSE),
                expand = intent.getBooleanExtra(OverlayService.EXTRA_EXPAND, false),
                reduced = intent.getBooleanExtra(OverlayService.EXTRA_REDUCED, false)
            )
            finish()
            return
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || launched) return
        render()
    }

    private fun render() {
        val canDraw = Settings.canDrawOverlays(this)
        val state = OverlayAndroidGate.state(this, mediaProjectionGranted = false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = if (canDraw) OverlayPolicy.ACTIVE else OverlayPolicy.OVERLAY_DENIED
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = OverlayPolicy.ANDROID_OVERLAY_PERMISSION +
                " is requested, never assumed. MediaProjection is optional; deny → " +
                OverlayPolicy.BLUR_UNAVAILABLE + ". " + OverlayCaptureLaw.CONSENT_COPY
            textSize = 14f
        })
        if (!canDraw) {
            root.addView(Button(this).apply {
                text = "Grant overlay permission"
                setOnClickListener {
                    val uri = Uri.parse("package:$packageName")
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
                }
            })
        } else {
            root.addView(Button(this).apply {
                text = "Start overlay (no blur)"
                setOnClickListener { OverlayService.start(this@OverlayPermissionActivity, false) }
            })
            root.addView(Button(this).apply {
                text = "Start overlay with screen capture"
                setOnClickListener {
                    startActivity(Intent(this@OverlayPermissionActivity, OverlayProjectionActivity::class.java))
                    finish()
                }
            })
        }
        val decision = OverlayProfileAndroid.decide(this)
        root.addView(TextView(this).apply {
            text = "profile ${decision.pillText}\n${decision.reason}"
            textSize = 14f
        })
        if (decision.blurMessage != null) {
            root.addView(TextView(this).apply {
                text = decision.blurMessage
                textSize = 14f
            })
        }
        root.addView(TextView(this).apply {
            text = "Performance profile (visible, never silent)"
            textSize = 16f
        })
        fun setProfile(profile: A3UiProfile?) {
            ProfileSession.setOverride(OverlayProfileAndroid.store(this), profile)
            render()
        }
        root.addView(Button(this).apply {
            text = "Profile AUTO"
            setOnClickListener { setProfile(null) }
        })
        for (item in A3UiProfile.entries) {
            root.addView(Button(this).apply {
                text = "Profile ${item.wire()}"
                setOnClickListener { setProfile(item) }
            })
        }
        setContentView(root)
    }

    companion object {
        const val EXTRA_SKIP_PROJECTION = "skip_projection"
    }
}
