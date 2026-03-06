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

    // ====================== Daftar Channel Horizontal ======================

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
                    activity.onChannelClicked(channel)
                }
            })
        }

        override fun getItemCount(): Int = channels.size
    }

    private fun onChannelClicked(channel: Channel) {
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

    // ====================== Jam ======================

    private fun startClock() {
        timeHandler = Handler(Looper.getMainLooper())
        timeRunnable = object : Runnable {
            override fun run() {
                updateTime()
                timeHandler?.postDelayed(this, 1000)
            }
        }
        timeHandler?.post(timeRunnable!!)
    }

    private fun stopClock() {
        timeHandler?.removeCallbacks(timeRunnable!!)
    }

    private fun updateTime() {
        val now = Date()
        bindingRoot.tvPlayerTime.text = timeFormat.format(now)
    }

    // ====================== Binding Listener ======================

    private fun bindingListener() {
        bindingRoot.playerView.apply {
            setOnTouchListener(object : OnSwipeTouchListener(this@apply) {
                override fun onSwipeDown() { switchChannel(CATEGORY_UP) }
                override fun onSwipeUp() { switchChannel(CATEGORY_DOWN) }
                override fun onSwipeLeft() { switchChannel(CHANNEL_NEXT) }
                override fun onSwipeRight() { switchChannel(CHANNEL_PREVIOUS) }
                override fun onTapDoubleLeft(click: Int) { doubleTapLeft(click) }
                override fun onTapDoubleRight(click: Int) { doubleTapRight(click) }
                override fun onTapDoubleFinish(click: Int, isLeft: Boolean) {
                    doubleTapFinish(click, isLeft)
                }
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

        bindingControl.buttonExit.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
            setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) { finish() }
            })
        }

        bindingControl.buttonPrevious.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { switchChannel(CHANNEL_PREVIOUS) }
        })

        bindingControl.buttonRewind.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { player?.seekBack() }
        })

        bindingControl.buttonForward.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { player?.seekForward() }
        })

        bindingControl.buttonNext.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { switchChannel(CHANNEL_NEXT) }
        })

        bindingControl.screenMode.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { if (v != null) showMenu(v) }
        })

        bindingControl.buttonLock.apply {
            visibility = if (isTelevision) View.GONE else View.VISIBLE
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
        isMute(bindingControl.buttonVolume)
    }

    @SuppressLint("SetTextI18n")
    private fun doubleTapLeft(clicks: Int) {
        if(player?.isCurrentWindowLive == false) {
            bindingRoot.seekBack.text = "- ${timeToString((clicks * 10).toDouble())}"
            bindingRoot.seekBack.alpha = 1f
            val seekAnimation = AlphaAnimation(0f, 1f)
            seekAnimation.fillAfter = true
            bindingRoot.seekBack.startAnimation(seekAnimation)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun doubleTapRight(clicks: Int) {
        if(player?.isCurrentWindowLive == false) {
            bindingRoot.seekForward.text = "+ ${timeToString((clicks * 10).toDouble())}"
            bindingRoot.seekForward.alpha = 1f
            val seekAnimation = AlphaAnimation(0f, 1f)
            seekAnimation.fillAfter = true
            bindingRoot.seekForward.startAnimation(seekAnimation)
        }
    }

    private fun doubleTapFinish(clicks: Int, isLeft: Boolean) {
        if(player?.isCurrentWindowLive == false) {
            val click = if (isLeft) clicks * -1 else clicks
            val seekAnimation = AlphaAnimation(1f, 0f)
            seekAnimation.duration = 1800
            seekAnimation.fillAfter = true
            if (isLeft) bindingRoot.seekBack.startAnimation(seekAnimation)
            else bindingRoot.seekForward.startAnimation(seekAnimation)
            seekTime((click * 10000).toLong())
        }
    }

    private fun seekTime(time: Long) {
        val currentPos = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L
        player?.seekTo(maxOf(minOf(currentPos + time, duration), 0L))
    }

    private fun timeToString(time: Double): String {
        val second = time.toInt()
        val rsec = second % 60
        val minute = ceil((second - rsec) / 60.0).toInt()
        val rmin = minute % 60
        val hour = ceil((minute - rmin) / 60.0).toInt()
        return (if (hour > 0) forceTwoDigit(hour) + ":" else "") +
                (if (rmin >= 0 || hour >= 0) forceTwoDigit(rmin) + ":" else "") +
                forceTwoDigit(rsec)
    }

    private fun forceTwoDigit(inp: Int, length: Int = 2): String {
        val added = length - inp.toString().length
        return if (added > 0) "0".repeat(added) + inp else inp.toString()
    }

    private fun setChannelInformation(visible: Boolean) {
        if (isLocked) return
        bindingRoot.layoutInfo.visibility = if (visible && !isPipMode) View.VISIBLE else View.INVISIBLE
        bindingControl.volumeLayout.visibility = if (visible || isPipMode) View.INVISIBLE else View.VISIBLE

        if (isPipMode) return
        if (visible == bindingRoot.playerView.isControllerVisible) return
        if (visible) bindingRoot.playerView.clearFocus()
        else return

        if (handlerInfo == null) handlerInfo = Handler(Looper.getMainLooper())
        handlerInfo?.removeCallbacksAndMessages(null)
        handlerInfo?.postDelayed(object : Runnable {
            override fun run() {
                if (bindingRoot.playerView.isControllerVisible) return
                bindingRoot.layoutInfo.visibility = View.INVISIBLE
            }
        }, bindingRoot.playerView.controllerShowTimeoutMs.toLong())
    }

    private fun lockControl(setLocked: Boolean) {
        isLocked = setLocked
        val visibility = if (setLocked) View.INVISIBLE else View.VISIBLE
        bindingRoot.layoutInfo.visibility = visibility
        bindingControl.buttonExit.visibility = visibility
        bindingControl.layoutControl.visibility = visibility
        bindingControl.screenMode.visibility = visibility
        bindingControl.trackSelection.visibility = visibility
        switchLiveOrVideo()
    }

    private fun switchLiveOrVideo() { switchLiveOrVideo(false) }
    private fun switchLiveOrVideo(reset: Boolean) {
        var visibility = when {
            reset -> View.GONE
            isLocked -> View.INVISIBLE
            player?.isCurrentWindowLive == true -> View.GONE
            else -> View.VISIBLE
        }
        bindingControl.layoutSeekbar.visibility = visibility
        bindingControl.spacerControl.visibility = visibility
        if (player?.isCurrentWindowSeekable == false) visibility = View.GONE
        bindingControl.buttonRewind.visibility = visibility
        bindingControl.buttonForward.visibility = visibility
    }

    // ====================== DRM Multi-Key (ExoPlayer 2.15.1) ======================

    private fun createDrmSessionManager(
        drmLicense: DrmLicense,
        httpDataSourceFactory: DefaultHttpDataSource.Factory
    ): DrmSessionManager {
        val uuid = UUID.fromString(drmLicense.type)

        val drmCallback = if (drmLicense.key.startsWith("http")) {
            HttpMediaDrmCallback(drmLicense.key, httpDataSourceFactory)
        } else {
            val keyPairs = drmLicense.key.split(",").map { it.split(":") }
            val keys = keyPairs.map { (kid, key) ->
                val kidBytes = hexToByteArray(kid)
                val keyBytes = hexToByteArray(key)
                Pair(kidBytes, keyBytes)
            }
            val keySetData = createKeySetData(keys)
            LocalMediaDrmCallback(keySetData)
        }

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true) // PERBAIKAN: Aktifkan Multi-key Support
            .build(drmCallback)
    }

    private fun hexToByteArray(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun createKeySetData(keys: List<Pair<ByteArray, ByteArray>>): ByteArray {
        val json = buildJsonObject(object : JsonObjectAction {
            override fun execute(builder: JsonObjectBuilder) {
                builder.addJsonArray("keys", object : JsonArrayAction {
                    override fun execute(arrayBuilder: JsonArrayBuilder) {
                        keys.forEach { (kid, key) ->
                            arrayBuilder.addJsonObject(object : JsonObjectAction {
                                override fun execute(objBuilder: JsonObjectBuilder) {
                                    objBuilder.add("kty", "oct")
                                    objBuilder.add("k", base64Encode(key))
                                    objBuilder.add("kid", base64Encode(kid))
                                }
                            })
                        }
                    }
                })
            }
        })
        return json.toByteArray()
    }

    private fun base64Encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE)

    // Helper JSON sederhana (Tanpa Lambda)
    interface JsonObjectAction { fun execute(builder: JsonObjectBuilder) }
    interface JsonArrayAction { fun execute(arrayBuilder: JsonArrayBuilder) }

    private fun buildJsonObject(action: JsonObjectAction): String {
        val builder = JsonObjectBuilder()
        action.execute(builder)
        return builder.toString()
    }

    class JsonObjectBuilder {
        private val map = mutableMapOf<String, Any>()
        fun add(key: String, value: String) { map[key] = value }
        fun addJsonArray(key: String, action: JsonArrayAction) {
            val arrayBuilder = JsonArrayBuilder()
            action.execute(arrayBuilder)
            map[key] = arrayBuilder.build()
        }
        override fun toString(): String = buildJsonString(map)
    }

    class JsonArrayBuilder {
        private val list = mutableListOf<Any>()
        fun addJsonObject(action: JsonObjectAction) {
            val objBuilder = JsonObjectBuilder()
            action.execute(objBuilder)
            list.add(objBuilder.getMap())
        }
        fun build(): List<Any> = list
    }

    fun JsonObjectBuilder.getMap(): Map<String, Any> {
        val field = this.javaClass.getDeclaredField("map")
        field.isAccessible = true
        return field.get(this) as Map<String, Any>
    }

    private fun buildJsonString(obj: Any): String = when (obj) {
        is Map<*, *> -> {
            val entries = obj.entries.joinToString(",") { (k, v) ->
                "\"$k\":${buildJsonString(v!!)}"
            }
            "{$entries}"
        }
        is List<*> -> {
            val items = obj.joinToString(",") { buildJsonString(it!!) }
            "[$items]"
        }
        is String -> "\"$obj\""
        else -> obj.toString()
    }

    private fun isDeviceSupportDrm(type: String): Boolean {
        val message = String.format(getString(R.string.device_not_support_drm), type.uppercase())
        if (FrameworkMediaDrm.isCryptoSchemeSupported(type.toUUID())) return true
        AlertDialog.Builder(this).apply {
            setTitle(R.string.player_playback_error)
            setMessage(message)
            setCancelable(false)
            setPositiveButton(getString(R.string.btn_next_channel), object : DialogInterface.OnClickListener {
                override fun onClick(di: DialogInterface?, i: Int) { switchChannel(CHANNEL_NEXT) }
            })
            setNegativeButton(R.string.btn_close, object : DialogInterface.OnClickListener {
                override fun onClick(di: DialogInterface?, i: Int) { finish() }
            })
            create().show()
        }
        return false
    }

    // ====================== RenderersFactory (Hardware Preference) ======================

    private fun createRenderersFactory(): RenderersFactory {
        val factory = DefaultRenderersFactory(this)
        // PERBAIKAN: Logika pemilihan Decoder Perangkat Keras
        // Mode 1: Software Only (Extension On)
        // Mode 2: Hardware Preferred (Extension Off)
        when (preferences.decoderMode) {
            1 -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            2 -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            else -> factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        return factory
    }

    // ====================== Playback ======================

    @Suppress("DEPRECATION")
    private fun playChannel() {
        switchLiveOrVideo(true)
        bindingRoot.categoryName.text = category?.name?.trim()
        bindingRoot.channelName.text = current?.name?.trim()

        val userAgent = current?.userAgent ?: "NontonTV/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"
        val referer = current?.referer.toString()
        val streamUrl = current?.streamUrl?.decodeUrl()
        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent)
        if (current?.referer != null)
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("referer" to referer))
            
        val dataSourceFactory = DefaultDataSourceFactory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val drmLicense = Playlist.cached.drmLicenses.firstOrNull {
            current?.drmId?.equals(it.id) == true
        }

        if (drmLicense != null && drmLicense.type.toUUID() != C.UUID_NIL) {
            val drmSessionManager = createDrmSessionManager(drmLicense, httpDataSourceFactory)
            mediaSource = mediaSourceFactory.setDrmSessionManager(drmSessionManager)
                    .createMediaSource(mediaItem)
            if (!isDeviceSupportDrm(drmLicense.type)) return
        } else {
            mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        }

        trackSelector = DefaultTrackSelector(this)
        trackSelector.parameters = DefaultTrackSelector.Parameters.Builder(this).build()

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(32 * 1024, 64 * 1024, 1024, 1024)
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true).build()

        val renderersFactory = createRenderersFactory()
        val playerBuilder = SimpleExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            
        if (preferences.optimizePrebuffer)
            playerBuilder.setLoadControl(loadControl)

        player = playerBuilder.build()
        player?.addListener(PlayerListener())

        bindingRoot.playerView.player = player
        bindingRoot.playerView.resizeMode = preferences.resizeMode
        bindingRoot.playerView.requestFocus()

        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.playWhenReady = true
        player?.playbackParameters = PlaybackParameters(preferences.speedMode)
        player?.volume = preferences.volume
    }

    private fun switchChannel(mode: Int): Boolean {
        if (isLocked) return true
        switchChannel(mode, false)
        bindingRoot.playerView.hideController()
        if (isChannelListVisible) toggleChannelList(false)
        return true
    }

    private fun switchChannel(mode: Int, lastCh: Boolean) {
        val catId = Playlist.cached.categories.indexOf(category)
        val chId = category?.channels?.indexOf(current) as Int
        when (mode) {
            CATEGORY_UP -> {
                val previous = catId - 1
                if (previous > -1) {
                    category = Playlist.cached.categories[previous]
                    current = if (lastCh) category?.channels?.get(category?.channels?.size?.minus(1) ?: 0)
                    else category?.channels?.get(0)
                } else {
                    Toast.makeText(this, R.string.top_category, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            CATEGORY_DOWN -> {
                val next = catId + 1
                if (next < Playlist.cached.categories.size) {
                    category = Playlist.cached.categories[next]
                    current = category?.channels?.get(0)
                } else {
                    Toast.makeText(this, R.string.bottom_category, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            CHANNEL_PREVIOUS -> {
                val previous = chId - 1
                if (previous > -1) {
                    current = category?.channels?.get(previous)
                } else {
                    switchChannel(CATEGORY_UP, true)
                    return
                }
            }
            CHANNEL_NEXT -> {
                val next = chId + 1
                if (next < (category?.channels?.size ?: 0)) {
                    current = category?.channels?.get(next)
                } else {
                    switchChannel(CATEGORY_DOWN)
                    return
                }
            }
        }

        errorCounter = 0
        player?.playWhenReady = false
        player?.release()
        playChannel()
        updateChannelListAdapter()
    }

    private fun retryPlayback(force: Boolean) {
        if (force) {
            player?.playWhenReady = true
            player?.setMediaSource(mediaSource)
            player?.prepare()
            return
        }
        AsyncSleep().task(object : AsyncSleep.Task {
            override fun onFinish() { retryPlayback(true) }
        }).start(1)
    }

    // ====================== Player Listener ======================

    private inner class PlayerListener : Player.EventListener {
        override fun onPlaybackStateChanged(state: Int) {
            val trackHaveContent = TrackSelectionDialog.willHaveContent(trackSelector)
            bindingControl.trackSelection.visibility = if (trackHaveContent) View.VISIBLE else View.GONE
            if (state == Player.STATE_READY) {
                errorCounter = 0
                val catId = Playlist.cached.categories.indexOf(category)
                val chId = category?.channels?.indexOf(current) as Int
                preferences.watched = PlayData(catId, chId)
                switchLiveOrVideo()
            } else if (state == Player.STATE_ENDED) {
                retryPlayback(true)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) setChannelInformation(true)
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            if (player?.playWhenReady == false) return
            if (errorCounter < 5 && network.isConnected()) {
                errorCounter++
                Toast.makeText(applicationContext, error.message, Toast.LENGTH_SHORT).show()
                retryPlayback(false)
            } else {
                showMessage(String.format(getString(R.string.player_error_message),
                        error.type, error.cause?.message ?: "", error.message ?: ""), true)
            }
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
            if (trackGroups == lastSeenTrackGroupArray) return
            lastSeenTrackGroupArray = trackGroups

            val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return
            val isVideoProblem = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
            val isAudioProblem = mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS

            val problem = when {
                isVideoProblem && isAudioProblem -> "video & audio"
                isVideoProblem -> "video"
                else -> "audio"
            }
            val message = String.format(getString(R.string.error_unsupported), problem)
            if (isVideoProblem) showMessage(message, false)
            else if (isAudioProblem) Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // ====================== Error Dialog ======================

    private fun showMessage(message: String, autoretry: Boolean) {
        val countdown = AsyncSleep()
        val waitInSecond = 30
        val btnRetryText = if (autoretry) String.format(getString(R.string.btn_retry_count), waitInSecond) else getString(R.string.btn_retry)
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.player_playback_error)
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setNegativeButton(getString(R.string.btn_next_channel), object : DialogInterface.OnClickListener {
            override fun onClick(di: DialogInterface?, i: Int) {
                switchChannel(CHANNEL_NEXT)
                di?.dismiss()
            }
        })
        builder.setPositiveButton(btnRetryText, object : DialogInterface.OnClickListener {
            override fun onClick(di: DialogInterface?, i: Int) {
                retryPlayback(true)
                di?.dismiss()
            }
        })
        builder.setNeutralButton(R.string.btn_close, object : DialogInterface.OnClickListener {
            override fun onClick(di: DialogInterface?, i: Int) {
                di?.dismiss()
                finish()
            }
        })
        builder.setOnDismissListener(object : DialogInterface.OnDismissListener {
            override fun onDismiss(di: DialogInterface?) { countdown.stop() }
        })
        
        val dialog = builder.show()

        if (!autoretry) return
        countdown.task(object : AsyncSleep.Task {
            override fun onCountDown(count: Int) {
                val text = if (count <= 0) getString(R.string.btn_retry)
                else String.format(getString(R.string.btn_retry_count), count)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = text
            }
            override fun onFinish() {
                dialog.dismiss()
                retryPlayback(true)
            }
        }).start(waitInSecond)
    }

    // ====================== Menu ======================

    private fun showTrackSelector(): Boolean {
        val dialog = TrackSelectionDialog.createForTrackSelector(trackSelector, object : DialogInterface.OnDismissListener {
            override fun onDismiss(dialog: DialogInterface?) {}
        })
        dialog.show(supportFragmentManager, null)
        return true
    }

    private fun showMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.setting_mode)
        popup.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                if (item.itemId == R.id.speed_mode) {
                    showSpeedMenu(view)
                } else {
                    showScreenMenu(view)
                }
                return true
            }
        })
        popup.show()
    }

    private fun showScreenMenu(view: View) {
        val timeout = bindingRoot.playerView.controllerShowTimeoutMs
        bindingRoot.playerView.controllerShowTimeoutMs = 0
        val popupMenu = PopupMenu(this, view)
        popupMenu.inflate(R.menu.screen_resize_mode)
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                val mode = when (item.itemId) {
                    R.id.mode_fit -> 0
                    R.id.mode_fixed_width -> 1
                    R.id.mode_fixed_height -> 2
                    R.id.mode_fill -> 3
                    R.id.mode_zoom -> 4
                    else -> 5
                }
                if (bindingRoot.playerView.resizeMode != mode && mode != 5) {
                    bindingRoot.playerView.resizeMode = mode
                    preferences.resizeMode = mode
                }
                if (item.itemId == R.id.mode_back) showMenu(view) else showScreenMenu(view)
                return true
            }
        })
        
        when (preferences.resizeMode) {
            0 -> popupMenu.menu.findItem(R.id.mode_fit).isChecked = true
            1 -> popupMenu.menu.findItem(R.id.mode_fixed_width).isChecked = true
            2 -> popupMenu.menu.findItem(R.id.mode_fixed_height).isChecked = true
            3 -> popupMenu.menu.findItem(R.id.mode_fill).isChecked = true
            4 -> popupMenu.menu.findItem(R.id.mode_zoom).isChecked = true
        }
        
        popupMenu.setOnDismissListener(object : PopupMenu.OnDismissListener {
            override fun onDismiss(menu: PopupMenu?) {
                bindingRoot.playerView.controllerShowTimeoutMs = timeout
            }
        })
        popupMenu.show()
    }

    private fun showSpeedMenu(view: View) {
        val timeout = bindingRoot.playerView.controllerShowTimeoutMs
        bindingRoot.playerView.controllerShowTimeoutMs = 0
        val popupMenu = PopupMenu(this, view)
        popupMenu.inflate(R.menu.playback_speed_mode)
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                val speed = when (item.itemId) {
                    R.id.speed_0_25 -> 0.25f
                    R.id.speed_0_50 -> 0.5f
                    R.id.speed_0_75 -> 0.75f
                    R.id.speed_1_00 -> 1f
                    R.id.speed_1_25 -> 1.25f
                    R.id.speed_1_50 -> 1.5f
                    R.id.speed_1_75 -> 1.75f
                    R.id.speed_2_00 -> 2f
                    else -> 0f
                }
                if (preferences.speedMode != speed && speed != 0f) {
                    player?.playbackParameters = PlaybackParameters(speed)
                    preferences.speedMode = speed
                    showSpeedMenu(view)
                }
                if (item.itemId == R.id.speed_back) showMenu(view)
                return true
            }
        })

        val currentSpeed = preferences.speedMode
        val menu = popupMenu.menu
        if (currentSpeed == 0.25f) menu.findItem(R.id.speed_0_25).isChecked = true
        else if (currentSpeed == 0.5f) menu.findItem(R.id.speed_0_50).isChecked = true
        else if (currentSpeed == 0.75f) menu.findItem(R.id.speed_0_75).isChecked = true
        else if (currentSpeed == 1f) menu.findItem(R.id.speed_1_00).isChecked = true
        else if (currentSpeed == 1.25f) menu.findItem(R.id.speed_1_25).isChecked = true
        else if (currentSpeed == 1.5f) menu.findItem(R.id.speed_1_50).isChecked = true
        else if (currentSpeed == 1.75f) menu.findItem(R.id.speed_1_75).isChecked = true
        else if (currentSpeed == 2f) menu.findItem(R.id.speed_2_00).isChecked = true

        popupMenu.setOnDismissListener(object : PopupMenu.OnDismissListener {
            override fun onDismiss(menu: PopupMenu?) {
                bindingRoot.playerView.controllerShowTimeoutMs = timeout
            }
        })
        popupMenu.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showVolumeMenu() {
        bindingControl.volumeLayout.visibility = View.VISIBLE
        bindingControl.volumeSeek.progress = (preferences.volume * 100).toInt()
        bindingControl.volumeSeek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, i: Int, b: Boolean) {
                preferences.volume = i.toFloat() / 100
                player?.volume = preferences.volume
                isMute(bindingControl.buttonVolume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun isMute(v: ImageButton) {
        v.setImageResource(if (preferences.volume == 0f) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
        startClock()
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
        stopClock()
    }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            } else {
                enterPictureInPictureMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(pip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(pip, newConfig)
        isPipMode = pip
        setChannelInformation(!pip)
        bindingRoot.playerView.useController = !pip
        player?.playWhenReady = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!bindingRoot.playerView.isControllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            bindingRoot.playerView.showController()
            return true
        }
        if (isLocked) return true
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> return showTrackSelector()
            KeyEvent.KEYCODE_PAGE_UP -> return switchChannel(CATEGORY_UP)
            KeyEvent.KEYCODE_PAGE_DOWN -> return switchChannel(CATEGORY_DOWN)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return switchChannel(CHANNEL_PREVIOUS)
            KeyEvent.KEYCODE_MEDIA_NEXT -> return switchChannel(CHANNEL_NEXT)
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player?.isPlaying == false) player?.play() else player?.pause()
                return true
            }
        }
        if (player?.isCurrentWindowLive == false) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_REWIND -> { player?.seekBack(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player?.seekForward(); return true }
            }
        }
        if (bindingRoot.playerView.isControllerVisible) return super.onKeyUp(keyCode, event)
        if (!preferences.reverseNavigation) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return switchChannel(CATEGORY_UP)
                KeyEvent.KEYCODE_DPAD_DOWN -> return switchChannel(CATEGORY_DOWN)
                KeyEvent.KEYCODE_DPAD_LEFT -> return switchChannel(CHANNEL_PREVIOUS)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(CHANNEL_NEXT)
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return switchChannel(CHANNEL_NEXT)
                KeyEvent.KEYCODE_DPAD_DOWN -> return switchChannel(CHANNEL_PREVIOUS)
                KeyEvent.KEYCODE_DPAD_LEFT -> return switchChannel(CATEGORY_UP)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return switchChannel(CATEGORY_DOWN)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isLocked) return
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finish()
            return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_player), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() { doubleBackToExitPressedOnce = false }
        }, 2000)
    }

    override fun onDestroy() {
        player?.release()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        stopClock()
        super.onDestroy()
    }
}
