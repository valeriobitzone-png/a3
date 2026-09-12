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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.atomic.AtomicBoolean

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    private lateinit var windowManager: WindowManager
    private var backdropView: ImageView? = null
    private var chromeView: View? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val hidden = AtomicBoolean(false)
    private var session = OverlayFlight.present()
    private var backdropMode = BackdropMode.UNAVAILABLE
    private val captureCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var pendingState: OverlayPermissionState? = null

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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        intent?.getStringExtra(EXTRA_CHOOSE)?.let { openCard(it) }
        if (projectionGranted) {
            attachBackdrop()
            startProjection(intent)
        } else {
            attachChrome(state)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hidden.set(true)
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        backdropView?.let { windowManager.removeView(it) }
        chromeView?.let { windowManager.removeView(it) }
        backdropView = null
        chromeView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attachBackdrop() {
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
        backdropView = view
    }

    private fun attachChrome(state: OverlayPermissionState) {
        if (chromeView != null) return
        val wrap = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }
        val compose = ComposeView(wrap.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                OverlayChrome(
                    session = session,
                    blurUnavailable = state.backdrop == BackdropMode.UNAVAILABLE,
                    onHide = { hideChrome() },
                    onSettings = { openSettings() },
                    onChoose = { id -> openCard(id) }
                )
            }
        }
        wrap.addView(compose)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        windowManager.addView(wrap, params)
        chromeView = wrap
    }

    private fun hideChrome() {
        chromeView?.visibility = View.GONE
        backdropView?.visibility = View.GONE
        hidden.set(true)
    }

    private fun openSettings() {
        startActivity(
            Intent(this, OverlayPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openCard(id: String) {
        val opened = ArrayList<String>()
        OverlayFlight.choose(session, id) { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            opened += url
            hideChrome()
            true
        }
    }

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
        r.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val n = captureCount.incrementAndGet()
            try {
                // Skip the consent UI frames; take one later sample of the apps underneath.
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
                val blurred = OverlayBitmapBlur.blur(cropped, 18)
                backdropView?.post {
                    backdropView?.setImageBitmap(blurred)
                    pendingState?.let { attachChrome(it) }
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

    companion object {
        const val EXTRA_PROJECTION = "projection"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_CHOOSE = "choose"

        fun start(
            context: Context,
            projectionGranted: Boolean,
            data: Intent? = null,
            code: Int = 0,
            choose: String? = null
        ) {
            val intent = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_PROJECTION, projectionGranted)
                .putExtra(EXTRA_PROJECTION_CODE, code)
            if (data != null) intent.putExtra(EXTRA_PROJECTION_DATA, data)
            if (choose != null) intent.putExtra(EXTRA_CHOOSE, choose)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
