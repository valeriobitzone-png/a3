// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    private lateinit var windowManager: WindowManager
    private var backdropView: ImageView? = null
    private var pillView: View? = null
    private var sheetView: View? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val hidden = AtomicBoolean(false)
    private var session = OverlayFlight.present()
    private var backdropMode = BackdropMode.UNAVAILABLE
    private var blurred: android.graphics.Bitmap? = null
    private val captureCount = AtomicInteger(0)
    private var pendingState: OverlayPermissionState? = null
    private var life = OverlayLifecycle.start(0)
    private val markState = mutableStateOf(OverlayActionMark.UNKNOWN)
    private val phaseState = mutableStateOf(OverlayPhase.COLLAPSED)
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutMs = OverlayLifecycle.DEFAULT_TIMEOUT_MS
    private var reduced = false
    private var dismissOutside = true
    private var surfaceKind = OverlaySurfaceKind.ROUTINE
    private val timeoutRun = Runnable {
        applyLife(OverlayLifecycle.tick(life, now(), timeoutMs))
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectionGranted = intent?.getBooleanExtra(EXTRA_PROJECTION, false) == true
        val state = OverlayAndroidGate.state(this, projectionGranted)
        notifyActive(projectionGranted)
        if (state.availability != OverlayAvailability.ENABLED) {
            notifyMessage(state.message)
            stopSelf()
            return START_NOT_STICKY
        }
        backdropMode = state.backdrop
        pendingState = state
        reduced = intent?.getBooleanExtra(EXTRA_REDUCED, false) == true || reducedMotionSystem()
        timeoutMs = intent?.getLongExtra(EXTRA_TIMEOUT_MS, timeoutMs) ?: timeoutMs
        if (intent?.hasExtra(EXTRA_DISMISS_OUTSIDE) == true) {
            dismissOutside = intent.getBooleanExtra(EXTRA_DISMISS_OUTSIDE, true)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (intent?.getBooleanExtra(EXTRA_SENSITIVE, false) == true) {
            surfaceKind = OverlaySurfaceKind.SENSITIVE
        }
        if (pillView == null) {
            life = OverlayLifecycle.start(now())
            attachPill()
        }
        if (projectionGranted &&
            projection == null &&
            OverlaySensitiveLaw.mayShowExpanded(surfaceKind)
        ) {
            startProjection(intent)
        }
        if (surfaceKind == OverlaySurfaceKind.SENSITIVE) {
            applyLife(OverlayLifecycle.hideSensitive(life, now()))
        } else if (intent?.getBooleanExtra(EXTRA_EXPAND, false) == true) {
            applyLife(OverlayLifecycle.expand(life, now()))
        }
        if (intent?.getBooleanExtra(EXTRA_COLLAPSE, false) == true) {
            applyLife(OverlayLifecycle.collapse(life, now(), OverlayCollapseReason.DISMISS))
        }
        if (intent?.getBooleanExtra(EXTRA_UNDER_FOCUS, false) == true) {
            applyLife(OverlayLifecycle.underFocus(life, now()))
        }
        if (intent?.hasExtra(EXTRA_PROFILE) == true) {
            ProfileSession.setOverride(
                OverlayProfileAndroid.store(this),
                A3UiProfile.parse(intent.getStringExtra(EXTRA_PROFILE))
            )
        }
        val profile = OverlayProfileAndroid.decide(this)
        if (profile.matrix.motion == MotionMode.SIMPLIFIED) {
            reduced = true
        }
        if (intent?.getBooleanExtra(EXTRA_TICK, false) == true) {
            applyLife(OverlayLifecycle.tick(life, now(), timeoutMs))
        }
        intent?.getStringExtra(EXTRA_CHOOSE)?.let { openCard(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        hidden.set(true)
        handler.removeCallbacks(timeoutRun)
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        blurred?.recycle()
        blurred = null
        removeView(backdropView)
        removeView(sheetView)
        removeView(pillView)
        backdropView = null
        sheetView = null
        pillView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun reducedMotionSystem(): Boolean {
        return try {
            android.provider.Settings.Global.getFloat(
                contentResolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    private fun applyLife(next: OverlayLifecycleState) {
        val gated =
            if (!OverlaySensitiveLaw.mayShowExpanded(surfaceKind) && next.phase == OverlayPhase.EXPANDED) {
                OverlayLifecycle.hideSensitive(next, now())
            } else {
                next
            }
        val prev = life
        life = gated
        markState.value = gated.mark
        phaseState.value = gated.phase
        if (prev.phase != gated.phase) {
            if (gated.phase == OverlayPhase.EXPANDED) showExpanded() else hideExpanded()
        }
        scheduleTimeout()
    }

    private fun scheduleTimeout() {
        handler.removeCallbacks(timeoutRun)
        if (life.phase != OverlayPhase.EXPANDED) return
        val delay = timeoutMs - (now() - life.lastInteractionAt)
        if (delay <= 0L) {
            applyLife(OverlayLifecycle.tick(life, now(), timeoutMs))
        } else {
            handler.postDelayed(timeoutRun, delay)
        }
    }

    private fun attachPill() {
        if (pillView != null) return
        val wrap = OverlayHostFrame()
        wrap.bindTrees()
        val compose = ComposeView(wrap.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            bindTrees()
            setContent {
                val decision = OverlayProfileAndroid.decide(this@OverlayService)
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.End
                ) {
                    ProfileDebugChip(decision)
                    OverlayPill(
                        mark = markState.value,
                        onTap = {
                            if (life.phase == OverlayPhase.COLLAPSED &&
                                OverlaySensitiveLaw.mayShowExpanded(surfaceKind)
                            ) {
                                applyLife(OverlayLifecycle.expand(life, now()))
                            }
                        },
                        onLongPress = { openSettings() }
                    )
                }
            }
        }
        wrap.addView(compose)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayAndroidWindows.pillFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 16
        params.y = 16
        windowManager.addView(wrap, params)
        pillView = wrap
    }

    private fun showExpanded() {
        val matrix = OverlayProfileAndroid.decide(this).matrix
        if (backdropMode == BackdropMode.REAL_BLUR && matrix.blurEnabled) attachBackdrop()
        attachSheet()
        raisePill()
    }

    private fun hideExpanded() {
        val sheet = sheetView
        val back = backdropView
        sheetView = null
        backdropView = null
        val ms = OverlayLifecycle.transitionMs(reduced)
        if (ms == 0L) {
            removeView(sheet)
            removeView(back)
            blurred?.recycle()
            blurred = null
            return
        }
        if (sheet != null) {
            val lp = sheet.layoutParams as WindowManager.LayoutParams
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(sheet, lp) }
            sheet.animate().alpha(0f).setDuration(ms).withEndAction {
                removeView(sheet)
            }.start()
        }
        back?.animate()?.alpha(0f)?.setDuration(ms)?.withEndAction {
            removeView(back)
            blurred?.recycle()
            blurred = null
        }?.start()
        if (back == null && sheet == null) return
        if (sheet == null && back != null) {
            handler.postDelayed({ removeView(back) }, ms)
        }
    }

    private fun attachBackdrop() {
        if (backdropView != null) return
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageBitmap(blurred)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayAndroidWindows.backdropFlags(),
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
        backdropView = view
        val radius = OverlayProfileAndroid.decide(this).matrix.blurRadiusPx
        OverlayBackdropGpu.apply(view, radius)
    }

    private fun attachSheet() {
        if (sheetView != null) return
        val wrap = OverlaySheetFrame()
        wrap.bindTrees()
        val compose = ComposeView(wrap.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            bindTrees()
            setContent {
                val decision = OverlayProfileAndroid.decide(this@OverlayService)
                OverlaySheet(
                    session = session,
                    blurUnavailable = backdropMode == BackdropMode.UNAVAILABLE || !decision.matrix.blurEnabled,
                    reducedMotion = reduced,
                    dismissOutside = dismissOutside,
                    profile = decision,
                    onChoose = { id -> openCard(id) },
                    onDismiss = {
                        applyLife(OverlayLifecycle.collapse(life, now(), OverlayCollapseReason.DISMISS))
                    }
                )
            }
        }
        wrap.addView(compose)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayAndroidWindows.containerFlags(OverlayPhase.EXPANDED),
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(wrap, params)
        sheetView = wrap
    }

    private fun raisePill() {
        val view = pillView ?: return
        if (!view.isAttachedToWindow) return
        val lp = view.layoutParams as WindowManager.LayoutParams
        windowManager.removeView(view)
        windowManager.addView(view, lp)
    }

    private fun openSettings() {
        startActivity(
            Intent(this, OverlayPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openCard(id: String) {
        applyLife(OverlayLifecycle.dispatch(life, now()))
        var worked = false
        try {
            OverlayFlight.choose(session, id) { url ->
                val chrome = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    setPackage("com.android.chrome")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(chrome)
                } catch (_: Exception) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                worked = true
                true
            }
        } catch (_: Exception) {
            worked = false
        }
        applyLife(OverlayLifecycle.terminal(life, now(), worked))
    }

    /** One-shot bitmap for visible blur only. Never agent input, never persisted, never off-device. */
    private fun startProjection(intent: Intent?) {
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        } ?: return
        val code = intent?.getIntExtra(EXTRA_PROJECTION_CODE, 0) ?: 0
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data) ?: return
        projection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    virtualDisplay?.release()
                    virtualDisplay = null
                }
            },
            Handler(Looper.getMainLooper())
        )
        val dm = resources.displayMetrics
        val r = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, android.graphics.PixelFormat.RGBA_8888, 2)
        reader = r
        virtualDisplay = projection?.createVirtualDisplay(
            "a3-overlay",
            dm.widthPixels,
            dm.heightPixels,
            dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface,
            null,
            null
        )
        r.setOnImageAvailableListener({ imgReader ->
            val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val n = captureCount.incrementAndGet()
            try {
                if (n != 12) return@setOnImageAvailableListener
                val plane = image.planes[0]
                val buf = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmap = android.graphics.Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buf)
                val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                val next = OverlayBitmapBlur.copy(cropped)
                cropped.recycle()
                val radius = OverlayProfileAndroid.decide(this).matrix.blurRadiusPx
                val previous = blurred
                blurred = next
                previous?.recycle()
                handler.post {
                    if (life.phase == OverlayPhase.EXPANDED) {
                        if (backdropView == null) attachBackdrop()
                        backdropView?.setImageBitmap(next)
                        backdropView?.let { OverlayBackdropGpu.apply(it, radius) }
                    } else {
                        next.recycle()
                        if (blurred === next) blurred = null
                    }
                    virtualDisplay?.release()
                    virtualDisplay = null
                }
            } finally {
                image.close()
            }
        }, null)
    }

    private fun notifyActive(projection: Boolean = false) {
        val channel = "a3-overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channel, "A3 overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, OverlayPermissionActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle(OverlayPolicy.ACTIVE)
            .setContentText("epistemic overlay")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (projection) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

    private fun notifyMessage(message: String) {
        notifyActive()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = "a3-overlay"
        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("A3 overlay")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        nm.notify(2, n)
    }

    private fun host(): FrameLayout = OverlayHostFrame()

    private fun View.bindTrees() {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
    }

    private fun removeView(view: View?) {
        if (view == null) return
        runCatching { windowManager.removeView(view) }
    }

    /** filterTouchesWhenObscured + FLAG_WINDOW_IS_OBSCURED: hostile windows cannot tapjack A3 actions. */
    open inner class OverlayHostFrame : FrameLayout(this@OverlayService) {
        init {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            bindTrees()
            filterTouchesWhenObscured = true
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (!OverlayTapJacking.allowsA3Action(event.flags)) return true
            return super.dispatchTouchEvent(event)
        }
    }

    inner class OverlaySheetFrame : OverlayHostFrame() {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!OverlayTapJacking.allowsA3Action(event.flags)) return true
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                applyLife(OverlayLifecycle.underFocus(life, now()))
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    companion object {
        const val EXTRA_PROJECTION = "projection"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_CHOOSE = "choose"
        const val EXTRA_EXPAND = "expand"
        const val EXTRA_COLLAPSE = "collapse"
        const val EXTRA_UNDER_FOCUS = "under_focus"
        const val EXTRA_TICK = "tick"
        const val EXTRA_REDUCED = "reduced"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_DISMISS_OUTSIDE = "dismiss_outside"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_SENSITIVE = "sensitive"

        fun start(
            context: Context,
            projectionGranted: Boolean,
            data: Intent? = null,
            code: Int = 0,
            choose: String? = null,
            expand: Boolean = false,
            reduced: Boolean = false,
            timeoutMs: Long = OverlayLifecycle.DEFAULT_TIMEOUT_MS,
            profile: String? = null,
            sensitive: Boolean = false
        ) {
            val intent = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_PROJECTION, projectionGranted)
                .putExtra(EXTRA_PROJECTION_CODE, code)
                .putExtra(EXTRA_EXPAND, expand)
                .putExtra(EXTRA_REDUCED, reduced)
                .putExtra(EXTRA_TIMEOUT_MS, timeoutMs)
                .putExtra(EXTRA_SENSITIVE, sensitive)
            if (data != null) intent.putExtra(EXTRA_PROJECTION_DATA, data)
            if (choose != null) intent.putExtra(EXTRA_CHOOSE, choose)
            if (profile != null) intent.putExtra(EXTRA_PROFILE, profile)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
