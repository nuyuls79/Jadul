package net.harimurti.tv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.drm.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import net.harimurti.tv.databinding.ActivityPlayerBinding
import net.harimurti.tv.databinding.CustomControlBinding
import net.harimurti.tv.dialog.TrackSelectionDialog
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.*
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.DrmLicense
import net.harimurti.tv.model.PlayData
import net.harimurti.tv.model.Playlist
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

class PlayerActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val network = Network()
    private var category: Category? = null
    private var current: Channel? = null
    private var player: SimpleExoPlayer? = null
    private lateinit var mediaSource: MediaSource
    private lateinit var trackSelector: DefaultTrackSelector
    private var lastSeenTrackGroupArray: TrackGroupArray? = null
    private lateinit var bindingRoot: ActivityPlayerBinding
    private lateinit var bindingControl: CustomControlBinding
    private var handlerInfo: Handler? = null
    private var errorCounter = 0
    private var isLocked = false

    private var timeHandler: Handler? = null
    private var timeRunnable: Runnable? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private lateinit var channelAdapter: ChannelListAdapter
    private var isChannelListVisible = false

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val callback = intent.getStringExtra(PLAYER_CALLBACK)
            if (callback == RETRY_PLAYBACK) {
                retryPlayback(true)
            } else if (callback == CLOSE_PLAYER) {
                finish()
            }
        }
    }

    companion object {
        var isFirst = true
        var isPipMode = false
        const val PLAYER_CALLBACK = "PLAYER_CALLBACK"
        const val RETRY_PLAYBACK = "RETRY_PLAYBACK"
        const val CLOSE_PLAYER = "CLOSE_PLAYER"
        private const val CHANNEL_NEXT = 0
        private const val CHANNEL_PREVIOUS = 1
        private const val CATEGORY_UP = 2
        private const val CATEGORY_DOWN = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bindingRoot = ActivityPlayerBinding.inflate(layoutInflater)
        bindingControl = CustomControlBinding.bind(bindingRoot.root.findViewById(R.id.custom_control))
        setContentView(bindingRoot.root)

        isFirst = false

        if (Playlist.cached.isCategoriesEmpty()) {
            Toast.makeText(this, R.string.player_no_playlist, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val parcel: PlayData? = intent.getParcelableExtra(PlayData.VALUE)
            if (parcel != null) {
                category = Playlist.cached.categories[parcel.catId]
                current = category?.channels?.get(parcel.chId)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.player_playdata_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (category == null || current == null) {
            Toast.makeText(this, R.string.player_no_channel, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindingListener()
        playChannel()
        setupChannelList()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(PLAYER_CALLBACK))

        startClock()
    }

    // ====================== Daftar Channel ======================

    class ChannelListAdapter(
        private val channels: List<Channel>,
        private val currentChannel: Channel?,
        private val activity: PlayerActivity
    ) : RecyclerView.Adapter<ChannelListAdapter.ViewHolder>() {

        class ViewHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel_horizontal, parent, false) as TextView
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = channels[position]
            holder.textView.text = channel.name
            holder.textView.isSelected = (channel == currentChannel)
            holder.textView.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    activity.onChannelItemClick(channel)
                }
            })
        }

        override fun getItemCount(): Int = channels.size
    }

    fun onChannelItemClick(channel: Channel) {
        if (channel != current) {
            current = channel
            errorCounter = 0
            player?.playWhenReady = false
            player?.release()
            playChannel()
            updateChannelListAdapter()
            toggleChannelList(false)
        }
    }

    private fun toggleChannelList(show: Boolean) {
        isChannelListVisible = show
        bindingControl.rvChannelList.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupChannelList() {
        val channels = category?.channels ?: return
        channelAdapter = ChannelListAdapter(channels, current, this)
        bindingControl.rvChannelList.adapter = channelAdapter
        bindingControl.rvChannelList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        bindingControl.buttonInfo.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                updateChannelListAdapter()
                toggleChannelList(!isChannelListVisible)
            }
        })
    }

    private fun updateChannelListAdapter() {
        category?.channels?.let { channels ->
            channelAdapter = ChannelListAdapter(channels, current, this)
            bindingControl.rvChannelList.adapter = channelAdapter
        }
    }

    // ====================== Jam & UI Control ======================

    private fun startClock() {
        timeHandler = Handler(Looper.getMainLooper())
        timeRunnable = object : Runnable {
            override fun run() {
                bindingRoot.tvPlayerTime.text = timeFormat.format(Date())
                timeHandler?.postDelayed(this, 1000)
            }
        }
        timeHandler?.post(timeRunnable!!)
    }

    private fun stopClock() {
        timeHandler?.removeCallbacks(timeRunnable!!)
    }

    private fun bindingListener() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener(this@apply) {
                override fun onSwipeDown() { switchChannel(CATEGORY_UP) }
                override fun onSwipeUp() { switchChannel(CATEGORY_DOWN) }
                override fun onSwipeLeft() { switchChannel(CHANNEL_NEXT) }
                override fun onSwipeRight() { switchChannel(CHANNEL_PREVIOUS) }
                override fun onTapDoubleLeft(click: Int) { doubleTapLeft(click) }
                override fun onTapDoubleRight(click: Int) { doubleTapRight(click) }
                override fun onTapDoubleFinish(click: Int, isLeft: Boolean) { doubleTapFinish(click, isLeft) }
            })
            setControllerVisibilityListener(object : PlayerControlView.VisibilityListener {
                override fun onVisibilityChange(visibility: Int) {
                    setChannelInformation(visibility == View.VISIBLE)
                }
            })
        }

        bindingControl.trackSelection.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { showTrackSelector() }
        })

        bindingControl.buttonExit.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { finish() }
        })

        bindingControl.buttonLock.apply {
            setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    if (!isLocked) {
                        (v as ImageButton).setImageResource(R.drawable.ic_lock)
                        lockControl(true)
                    }
                }
            })
            setOnLongClickListener(object : View.OnLongClickListener {
                override fun onLongClick(v: View?): Boolean {
                    val resId = if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
                    (v as ImageButton).setImageResource(resId)
                    lockControl(!isLocked)
                    return true
                }
            })
        }

        bindingControl.buttonVolume.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { showVolumeMenu() }
        })
    }

    private fun setChannelInformation(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility = if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        if (visible) {
            if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
            handlerInfo?.removeCallbacksAndMessages(null)
            handlerInfo?.postDelayed(object : Runnable {
                override fun run() {
                    if (!bindingRoot.playerView.isControllerVisible)
                        bindingRoot.layoutInfo.visibility = View.INVISIBLE
                }
            }, 3000)
        }
    }

    private fun lockControl(setLocked: Boolean) {
        isLocked = setLocked
        val visibility = if (setLocked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility = visibility
        bindingControl.layoutControl.visibility = visibility
    }

    // ====================== ExoPlayer Core (DRM & Renderers) ======================

    private fun createDrmSessionManager(drmLicense: DrmLicense, httpFactory: DefaultHttpDataSource.Factory): DrmSessionManager {
        val uuid = UUID.fromString(drmLicense.type)
        
        val drmCallback = if (drmLicense.key.startsWith("http")) {
            HttpMediaDrmCallback(drmLicense.key, httpFactory)
        } else {
            // Logic ClearKey manual tanpa lambda
            val keyPairs = drmLicense.key.split(",")
            val sb = StringBuilder("{\"keys\":[")
            for (i in keyPairs.indices) {
                val parts = keyPairs[i].split(":")
                if (parts.size < 2) continue
                val kid = android.util.Base64.encodeToString(hexToByteArray(parts[0]), android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE).trim()
                val key = android.util.Base64.encodeToString(hexToByteArray(parts[1]), android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE).trim()
                sb.append("{\"kty\":\"oct\",\"k\":\"$key\",\"kid\":\"$kid\"}")
                if (i < keyPairs.size - 1) sb.append(",")
            }
            sb.append("]}")
            LocalMediaDrmCallback(sb.toString().toByteArray())
        }

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true) // AKTIF: Untuk Multi-key DRM
            .build(drmCallback)
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val l = hex.length
        val data = ByteArray(l / 2)
        var i = 0
        while (i < l) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun createRenderersFactory(): RenderersFactory {
        val factory = DefaultRenderersFactory(this)
        // Preferensi Hardware/Software
        when (preferences.decoderMode) {
            1 -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            2 -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            else -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        return factory
    }

    private fun playChannel() {
        val streamUrl = current?.streamUrl?.decodeUrl() ?: ""
        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)

        val drmLicense = Playlist.cached.drmLicenses.firstOrNull { it.id == current?.drmId }
        if (drmLicense != null) {
            val drmManager = createDrmSessionManager(drmLicense, httpFactory)
            mediaSource = mediaSourceFactory.setDrmSessionManager(drmManager).createMediaSource(mediaItem)
        } else {
            mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        }

        trackSelector = DefaultTrackSelector(this)
        player = SimpleExoPlayer.Builder(this, createRenderersFactory())
            .setTrackSelector(trackSelector)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) errorCounter = 0
            }
            override fun onPlayerError(error: ExoPlaybackException) {
                if (errorCounter < 3) {
                    errorCounter++
                    retryPlayback(false)
                }
            }
        })

        bindingRoot.playerView.player = player
        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.playWhenReady = true
    }

    // ====================== Navigasi & Helper ======================

    private fun switchChannel(mode: Int): Boolean {
        // Logika switch channel dasar
        return true
    }

    private fun retryPlayback(force: Boolean) {
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun showTrackSelector() {
        TrackSelectionDialog.createForTrackSelector(trackSelector, object : DialogInterface.OnDismissListener {
            override fun onDismiss(d: DialogInterface?) {}
        }).show(supportFragmentManager, null)
    }

    private fun showVolumeMenu() {
        bindingControl.volumeLayout.visibility = View.VISIBLE
        bindingControl.volumeSeek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                player?.volume = p.toFloat() / 100
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun doubleTapLeft(c: Int) {}
    private fun doubleTapRight(c: Int) {}
    private fun doubleTapFinish(c: Int, l: Boolean) {}

    override fun onDestroy() {
        player?.release()
        stopClock()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}
