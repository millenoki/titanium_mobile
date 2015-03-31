package ti.modules.titanium.audio.streamer;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaCodec;
import android.media.MediaCodec.CryptoException;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RemoteControlClient;
import android.media.RemoteController.MetadataEditor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiApplication.AppStateListener;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiEnhancedService;
import org.appcelerator.titanium.proxy.TiEnhancedServiceProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiDrawableReference;

import ti.modules.titanium.filesystem.FileProxy;
import ti.modules.titanium.audio.AudioFocusHelper;
import ti.modules.titanium.audio.FocusableAudioWidget;
import ti.modules.titanium.audio.StreamerProxy;
import ti.modules.titanium.audio.AudioModule;
import ti.modules.titanium.audio.MediaButtonHelper;
import ti.modules.titanium.audio.RemoteControlClientCompat;
import ti.modules.titanium.audio.RemoteControlHelper;
import ti.modules.titanium.audio.SoundProxy;
import ti.modules.titanium.audio.RemoteControlClientCompat.MetadataEditorCompat;
import ti.modules.titanium.audio.streamer.StreamerExoPlayer.InfoListener;
import ti.modules.titanium.audio.streamer.StreamerExoPlayer.InternalErrorListener;
import ti.modules.titanium.audio.streamer.StreamerExoPlayer.RendererBuilder;
import ti.modules.titanium.audio.streamer.StreamerExoPlayer.RendererBuilderCallback;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack.InitializationException;
import com.google.android.exoplayer.audio.AudioTrack.WriteException;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.Id3Parser;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.source.DefaultSampleSource;
import com.google.android.exoplayer.source.FrameworkSampleExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.squareup.picasso.Cache;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.squareup.picasso.Picasso.LoadedFrom;

/**
 * A backbround {@link Service} used to keep music playing between activities
 * and when the user moves Apollo into the background.
 */
@SuppressWarnings("deprecation")
public class AudioStreamerExoService extends TiEnhancedService implements
        Target, Callback, AppStateListener, FocusableAudioWidget {
    private static final String TAG = "AudioStreamerExoService";
    /**
     * Indicates that the music has paused or resumed
     */
    public static final String PLAYSTATE_CHANGED = "ti.modules.titanium.audio.streamer.playstatechanged";

    /**
     * Indicates the meta data has changed in some way, like a track change
     */
    public static final String META_CHANGED = "ti.modules.titanium.audio.streamer.metachanged";

    /**
     * Indicates the queue has been updated
     */
    public static final String QUEUE_CHANGED = "ti.modules.titanium.audio.streamer.queuechanged";

    /**
     * Indicates the repeat mode chaned
     */
    public static final String REPEATMODE_CHANGED = "ti.modules.titanium.audio.streamer.repeatmodechanged";

    /**
     * Indicates the shuffle mode chaned
     */
    public static final String SHUFFLEMODE_CHANGED = "ti.modules.titanium.audio.streamer.shufflemodechanged";

    /**
     * Called to indicate a general service commmand. Used in
     * {@link TiMediaButtonIntentReceiver}
     */
    public static final String SERVICECMD = "ti.modules.titanium.audio.streamer.musicservicecommand";

    /**
     * Called to go toggle between pausing and playing the music
     */
    public static final String TOGGLEPAUSE_ACTION = "ti.modules.titanium.audio.streamer.togglepause";

    /**
     * Called to go to pause the playback
     */
    public static final String PAUSE_ACTION = "ti.modules.titanium.audio.streamer.pause";

    /**
     * Called to go to stop the playback
     */
    public static final String STOP_ACTION = "ti.modules.titanium.audio.streamer.stop";

    /**
     * Called to go to the previous track
     */
    public static final String PREVIOUS_ACTION = "ti.modules.titanium.audio.streamer.previous";

    /**
     * Called to go to the next track
     */
    public static final String NEXT_ACTION = "ti.modules.titanium.audio.streamer.next";

    /**
     * Called to change the repeat mode
     */
    public static final String REPEAT_ACTION = "ti.modules.titanium.audio.streamer.repeat";

    /**
     * Called to change the shuffle mode
     */
    public static final String SHUFFLE_ACTION = "ti.modules.titanium.audio.streamer.shuffle";

    /**
     * Called to kill the notification while Apollo is in the foreground
     */
    public static final String KILL_FOREGROUND = "ti.modules.titanium.audio.streamer.killforeground";

    /**
     * Used to easily notify a list that it should refresh. i.e. A playlist
     * changes
     */
    public static final String REFRESH = "ti.modules.titanium.audio.streamer.refresh";

    /**
     * Called to build the notification while Apollo is in the background
     */
    public static final String START_BACKGROUND = "ti.modules.titanium.audio.streamer.startbackground";

    /**
     * Called to update the remote control client
     */
    public static final String UPDATE_LOCKSCREEN = "ti.modules.titanium.audio.streamer.updatelockscreen";

    public static final String CMDNAME = "command";

    public static final String CMDTOGGLEPAUSE = "togglepause";

    public static final String CMDSTOP = "stop";

    public static final String CMDPAUSE = "pause";

    public static final String CMDPLAY = "play";

    public static final String CMDPREVIOUS = "previous";

    public static final String CMDNEXT = "next";

    public static final String CMDNOTIF = "buttonId";

    /**
     * Indicates when the track ends
     */
    private static final int TRACK_ENDED = 1;

    /**
     * Indicates that the current track was changed the next track
     */
    private static final int TRACK_WENT_TO_NEXT = 2;

    /**
     * Indicates when the release the wake lock
     */
    private static final int RELEASE_WAKELOCK = 3;

    /**
     * Indicates the player died
     */
    private static final int SERVER_DIED = 4;

    /**
     * Indicates some sort of focus change, maybe a phone call
     */
    private static final int FOCUSCHANGE = 5;

    /**
     * Indicates to fade the volume down
     */
    private static final int FADEDOWN = 6;

    /**
     * Indicates to fade the volume back up
     */
    private static final int FADEUP = 7;

    /**
     * Idle time before stopping the foreground notfication (1 minute)
     */
    private static final int IDLE_DELAY = 60000;

    /**
     * The max size allowed for the track history
     */
    private static final int MAX_HISTORY_SIZE = 100;

    private static final int MSG_FIRST_ID = 100;
    private static final int MSG_PICASSO_REQUEST = MSG_FIRST_ID + 1;
    private static final int MSG_UPDATE_BITMAP_METADATA = MSG_FIRST_ID + 2;

    /**
     * Keeps a mapping of the track history
     */
//    private static final LinkedList<Integer> mHistory = new LinkedList<Integer>();

    /**
     * Used to shuffle the tracks
     */
//    private static final Shuffler mShuffler = new Shuffler();

    /**
     * 4x1 widget
     */
    private AudioStreamerAppWidgetInterface mAppWidgetSmall;

    /**
     * 4x2 widget
     */
    private AudioStreamerAppWidgetInterface mAppWidgetLarge;

    /**
     * 4x2 alternate widget
     */
    private AudioStreamerAppWidgetInterface mAppWidgetLargeAlternate;

    /**
     * The media player
     */
    private MultiPlayer mPlayer;

    // /**
    // * The path of the current file to play
    // */
    // private String mFileToPlay;

    /**
     * Keeps the service running when the screen is off
     */
    private WakeLock mWakeLock;

    private Object mCursor;

    /**
     * Monitors the audio state
     */
    private AudioManager mAudioManager;
    private WifiManager mWifiManager;
    private WifiLock mWifiLock;

    /**
     * Used to know when the service is active
     */
    private boolean mServiceInUse = false;

    /**
     * Used to know if something should be playing or not
     */
    private boolean mIsSupposedToBePlaying = false;

    /**
     * Used to track what type of audio focus loss caused the playback to pause
     */
    private boolean mPausedByTransientLossOfFocus = false;

    public boolean mBuildNotification = false;
    
    public boolean mForcedTrackToEnd = false;

    public static final float DUCK_VOLUME = 0.1f;
    // our AudioFocusHelper object, if it's available (it's available on SDK
    // level >= 8)
    // If not available, this will be null. Always check for null before using!
    AudioFocusHelper mAudioFocusHelper = null;

    // do we have audio focus?
    enum AudioFocus {
        NoFocusNoDuck, // we don't have audio focus, and can't duck
        NoFocusCanDuck, // we don't have focus, but can play at a low volume
                        // ("ducking")
        Focused // we have full audio focus
    }

    AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

    /**
     * Lock screen controls ICS+
     */
    private RemoteControlClientCompat mRemoteControlClientCompat;

    /**
     * Enables the remote control client
     */
    private boolean mEnableLockscreenControls;

    private ComponentName mMediaButtonReceiverComponent;

//    private int mPlayListLen = 0;

    private int mPlayPos = -1;

    private int mNextPlayPos = -1;

    private int mOpenFailedCounter = 0;

//    private int mMediaMountedCount = 0;

    private int mShuffleMode = AudioModule.SHUFFLE_NONE;

    private int mRepeatMode = AudioModule.REPEAT_NONE;

    private int mServiceStartId = -1;

    private List<Object> mQueue = null;
    private List<Object> mOriginalQueue = null;

    private MusicPlayerHandler mPlayerHandler;

    private DelayedHandler mDelayedStopHandler;

    private boolean mPausePending = false;
    private boolean mStopPending = false;
    private boolean mPlayPending = false;

    public static final String STATE_WAITING_FOR_QUEUE_DESC = "waiting for queue";

    public static final String EVENT_COMPLETE = "complete";
    public static final String EVENT_BUFFERING = "buffering";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_STATE = "state";
    public static final String EVENT_CHANGE = "change";
    public static final String EVENT_PROGRESS = "progress";
    public static final String EVENT_END = "end";
    public static final int STATE_BUFFERING = 0; // current playback is in the
    // buffering from the network
    // state
    public static final int STATE_INITIALIZED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_STARTING = 4;
    public static final int STATE_STOPPED = 5;
    public static final int STATE_STOPPING = 6;
    public static final int STATE_WAITING_FOR_DATA = 7;
    public static final int STATE_WAITING_FOR_QUEUE = 8;
    public static final String STATE_BUFFERING_DESC = "buffering";
    public static final String STATE_INITIALIZED_DESC = "initialized";
    public static final String STATE_PAUSED_DESC = "paused";
    public static final String STATE_PLAYING_DESC = "playing";
    public static final String STATE_STARTING_DESC = "starting";
    public static final String STATE_STOPPED_DESC = "stopped";
    public static final String STATE_STOPPING_DESC = "stopping";
    public static final String STATE_WAITING_FOR_DATA_DESC = "waiting for data";

    public static final String EVENT_COMPLETE_JSON = "{ type : '"
            + EVENT_COMPLETE + "' }";

    private float mVolume = 1.0f;
    private boolean inBackground = false;

    private List<Intent> mPendingCommandIntents = new ArrayList<Intent>();
    private boolean mIgnoreStateChange = false;
    /**
     * Used to build the notification
     */
    private NotificationHelper mNotificationHelper;
    protected Handler uiHandler;
    MetadataEditorCompat currentMetadataEditor = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(final Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return super.onBind(intent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onUnbind(final Intent intent) {
        mServiceInUse = false;

        if (mIsSupposedToBePlaying || mPausedByTransientLossOfFocus) {
            // Something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop
            // the service now.
            return true;

            // If there is a playlist but playback is paused, then wait a while
            // before stopping the service, so that pause/resume isn't slow.
            // Also delay stopping the service if we're transitioning between
            // tracks.
        } else if ((mQueue != null && mQueue.size() > 0) || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            final Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }
        return super.onUnbind(intent);
    }
    
    public void onError(final int what, final String msg) {
//        mPlayPending = false;
//        mPausePending = false;
//        stop();
        if (proxyHasListeners(EVENT_ERROR, false)) {
            KrollDict data = new KrollDict();
            data.putCodeAndMessage(what, msg);
            data.put("track", mCursor);
            data.put(TiC.PROPERTY_INDEX, mPlayPos);
            proxy.fireEvent(EVENT_ERROR, data, false, false);
        }
    }

    private long mCurrentBufferingPosition = -1;

    public void onBufferingUpdate(long position, int percent) {
        if (position <= 0 || mCurrentBufferingPosition == position)
            return;
        mCurrentBufferingPosition = position;
        if (proxyHasListeners(EVENT_BUFFERING, false)) {
            KrollDict event = new KrollDict();
            event.put(TiC.PROPERTY_POSITION, position);
            event.put(TiC.PROPERTY_PROGRESS, percent);
            event.put(TiC.PROPERTY_DURATION, duration());
            proxy.fireEvent(EVENT_BUFFERING, event, false, false);
        }
    }

    private boolean proxyHasListeners(final String type,
            final boolean checkParent) {
        return proxy != null && proxy.hasListeners(type, checkParent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRebind(final Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    protected void start() {
        super.start();
        if (mPendingCommandIntents.size() > 0) {
            List<Intent> toHandle = new ArrayList<Intent>(mPendingCommandIntents);
            mPendingCommandIntents.clear();
            for (int i = 0; i < toHandle.size(); i++) {
                handleIntent(toHandle.get(i));
            }
        }
    }

    @Override
    protected void bindToProxy(final TiEnhancedServiceProxy proxy) {
        super.bindToProxy(proxy);
        if (this.proxy != null) {
            setShuffleMode(TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_SHUFFLE_MODE), mShuffleMode));
            setRepeatMode(TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_REPEAT_MODE), mRepeatMode));
            // Initialze the notification helper
            mNotificationHelper = new NotificationHelper(this,
                    ((StreamerProxy) proxy).getNotificationIcon(),
                    ((StreamerProxy) proxy).getNotificationViewId(),
                    ((StreamerProxy) proxy)
                            .getNotificationExtandedViewId());
           
            // Use the remote control APIs (if available and the user allows it)
            // to
            // set the playback state
            mEnableLockscreenControls = ((StreamerProxy) proxy)
                    .getEnableLockscreenControls();
            setUpRemoteControlClient();

            setVolume(TiConvert.toFloat(proxy.getProperty(TiC.PROPERTY_VOLUME),
                    1.0f));
            if (mStarted) {
                //we were already started
                if (mState == STATE_PLAYING) {
                    buildNotification();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();
        TiApplication.addAppStateListener(this);

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work will not disrupt the UI.
        final HandlerThread thread = new HandlerThread("MusicPlayerHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Initialize the handlers
        mPlayerHandler = new MusicPlayerHandler(this, thread.getLooper());
        mDelayedStopHandler = new DelayedHandler(this);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,
                "ti_audiostreamer_wifi_lock");

        // create the Audio Focus Helper, if the Audio Focus feature is
        // available (SDK 8 or above)
        if (android.os.Build.VERSION.SDK_INT < 8)
            mAudioFocus = AudioFocus.Focused; // no focus feature, so we always
                                              // "have" audio focus

        // Initialze the audio manager and register any headset controls for
        // playback
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        String packageName = TiApplication.getInstance().getPackageName();
        mMediaButtonReceiverComponent = new ComponentName(packageName,
                packageName + ".TiMediaButtonEventReceiver");
        MediaButtonHelper.registerMediaButtonEventReceiverCompat(mAudioManager,
                mMediaButtonReceiverComponent);

        uiHandler = new Handler(Looper.getMainLooper(), this);

        // Initialze the media player
        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mPlayerHandler);

        // Initialze the intent filter and each action
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);
        filter.addAction(KILL_FOREGROUND);
        filter.addAction(START_BACKGROUND);
        filter.addAction(UPDATE_LOCKSCREEN);
        // Attach the broadcast listener
        registerReceiver(mIntentReceiver, filter);

        // Initialize the wake lock
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getClass().getName());
        mWakeLock.setReferenceCounted(false);

        // Listen for the idle state
        final Message message = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(message, IDLE_DELAY);

    }

    /**
     * Initializes the remote control client
     */
    private void setUpRemoteControlClient() {
        if (mEnableLockscreenControls) {
            if (mRemoteControlClientCompat == null) {
                final Intent mediaButtonIntent = new Intent(
                        Intent.ACTION_MEDIA_BUTTON);
                mediaButtonIntent.setComponent(mMediaButtonReceiverComponent);
                mRemoteControlClientCompat = new RemoteControlClientCompat(
                        PendingIntent.getBroadcast(getApplicationContext(), 0,
                                mediaButtonIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT));
                RemoteControlHelper.registerRemoteControlClient(mAudioManager,
                        mRemoteControlClientCompat);
            }
            // Flags for the media transport control that this client supports.
            final int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                    | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                    | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                    | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                    | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                    | RemoteControlClient.FLAG_KEY_MEDIA_STOP;
            mRemoteControlClientCompat.setTransportControlFlags(flags);
        }
    }

    private void closeCursor() {
        if (mCursor != null) {
            mCursor = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        TiApplication.removeAppStateListener(this);
        super.onDestroy();

        // Release the player
        mPlayer.release();
        mPlayer = null;

        unregisterReceiver(mIntentReceiver);

        // Remove the audio focus listener and lock screen controls
        giveUpAudioFocus();

        RemoteControlHelper.unregisterRemoteControlClient(mAudioManager,
                mRemoteControlClientCompat);

        // Remove any callbacks from the handlers
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mPlayerHandler.removeCallbacksAndMessages(null);

        closeCursor();
        // Release the wake lock
        mWakeLock.release();

    }

    private void handleIntent(final Intent intent) {
        if (!mStarted) {
            mPendingCommandIntents.add(intent);
            return;
        }
        if (intent != null) {
            final String action = intent.getAction();
            final String command = intent.getStringExtra("command");
            if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(command)
                    || PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(command)
                    || TOGGLEPAUSE_ACTION.equals(action)) {
                if (mIsSupposedToBePlaying) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (CMDPLAY.equals(command)) {
                play();
            } else if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
                killNotification();
                mBuildNotification = false;
            } else if (REPEAT_ACTION.equals(action)) {
                cycleRepeat();
            } else if (SHUFFLE_ACTION.equals(action)) {
                cycleShuffle();
            } else if (KILL_FOREGROUND.equals(action)) {
                mBuildNotification = false;
                killNotification();
            } else if (START_BACKGROUND.equals(action)) {
                mBuildNotification = true;
                buildNotification();
            } else if (UPDATE_LOCKSCREEN.equals(action)) {
                mEnableLockscreenControls = intent.getBooleanExtra(
                        UPDATE_LOCKSCREEN, true);
                if (mEnableLockscreenControls) {
                    setUpRemoteControlClient();
                    // Update the controls according to the current playback
                    notifyChange(PLAYSTATE_CHANGED);
                    notifyChange(META_CHANGED);
                } else {
                    // Remove then unregister the conrols
                    mRemoteControlClientCompat
                            .setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
                    RemoteControlHelper.unregisterRemoteControlClient(
                            mAudioManager, mRemoteControlClientCompat);
                }
            }
        }

    }
    
    @Override
    public void onMediaKey(KeyEvent key) {
        switch (key.getKeyCode()) {
        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            if (isPlaying()) {
                pause();
            } else {
                play();
            }
            break;
        case KeyEvent.KEYCODE_MEDIA_PLAY:
            play();
            break;
        case KeyEvent.KEYCODE_HEADSETHOOK:
        case KeyEvent.KEYCODE_MEDIA_PAUSE:
            pause();
            break;
        case KeyEvent.KEYCODE_MEDIA_STOP:
            stop();
            break;
        case KeyEvent.KEYCODE_MEDIA_NEXT:
            gotoNext();
            break;
        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            prev();
            break;
        default:
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags,
            final int startId) {
        mServiceStartId = startId;

        int result = super.onStartCommand(intent, flags, startId);
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        handleIntent(intent);
        // Make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        final Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return result;
    }

    /**
     * Builds the notification
     */
    public void buildNotification() {
        if (mBuildNotification || inBackground) {
            try {
                mNotificationHelper.buildNotificationIfNeeded();
//                updateMetadata();
            } catch (final IllegalStateException parcelBitmap) {
                parcelBitmap.printStackTrace();
            }
        }
    }

    /**
     * Removes the foreground notification
     */
    public void killNotification() {
        stopForeground(true);
    }

    /**
     * Changes the notification buttons to a paused state and beging the
     * countdown to calling {@code #stopForeground(true)}
     */
    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        final Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);

        mDelayedStopHandler.postDelayed(new Runnable() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                killNotification();
            }
        }, IDLE_DELAY);
    }

    /**
     * Stops playback
     * 
     * @param remove_status_icon
     *            True to go to the idle state, false otherwise
     */
    private void stop(final boolean remove_status_icon) {
        if (isStopped()) {
            return;
        }
        setState(STATE_STOPPING);
        if (mPlayPending) {
            mStopPending = true;
            return;
        }
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        } else {
            setState(STATE_STOPPED);
        }
        closeCursor();
        if (remove_status_icon) {
            stopForeground(false);
        } else {
            gotoIdleState();
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
            giveUpAudioFocus();
        }
    }

    /**
     * Removes the range of tracks specified from the play list. If a file
     * within the range is the file currently being played, playback will move
     * to the next file after the range.
     * 
     * @param first
     *            The first file to be removed
     * @param last
     *            The last file to be removed
     * @return the number of tracks deleted
     */
    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
            int currentLength = getPlaylistSize();
            if (last < first) {
                return 0;
            } else if (first < 0) {
                first = 0;
            } else if (last >= currentLength) {
                last = currentLength - 1;
            }

            int newPos = mPlayPos;
            
            for (int i = first; i <= last; i++) {
                Object removed = mOriginalQueue.remove(i);
                if (mQueue.remove(removed)) {
                    newPos++;
                }
            }
            currentLength = getPlaylistSize();
            if (newPos != mPlayPos) {
                if (currentLength == 0) {
                    stop(true);
                    mPlayPos = -1;
                } else {
                    if (newPos >= currentLength) {
                        mPlayPos = 0;
                    } else {
                        mPlayPos = newPos;
                    }
                    final boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
//                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }

    /**
     * Adds a list to the playlist
     * 
     * @param list
     *            The list to add
     * @param position
     *            The position to place the tracks
     */
    public void removeFromPlayList(final List<Object> list) {
        for (int i = 0; i < list.size(); i++) {
            removeTrack(list.get(i));
        }
    }
    
    
    private int getPlaylistSize() {
        if (mOriginalQueue != null) {
            return mOriginalQueue.size();
        }
        return 0;
    }
    
    private int getQueueSize() {
        if (mQueue != null) {
            return mQueue.size();
        }
        return 0;
    }
    /**
     * Adds a list to the playlist
     * 
     * @param list
     *            The list to add
     * @param position
     *            The position to place the tracks
     */
    public void addToPlayList(final List<Object> list, int position) {
        int currentLength = getPlaylistSize();
        if (position < 0) {
            position = 0;
        }
        if (position > currentLength) {
            position = currentLength;
        }
        
        boolean needsSorting = false;
        if (mOriginalQueue == null) {
            mOriginalQueue = new ArrayList<Object>();
            needsSorting = true;
        }

        mOriginalQueue.addAll(position, list);
        if (needsSorting) {
            createCurrentQueue();
        } else {
            mQueue.addAll(position, list);
        }
        currentLength = getPlaylistSize();
        if (currentLength == 0) {
            closeCursor();
//            notifyChange(META_CHANGED);
        } else {
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     */
    private void openCurrentAndNext() {
        openCurrentAndMaybeNext(true);
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     * 
     * @param openNext
     *            True to prepare the next track for playback, false otherwise.
     */
    private void openCurrentAndMaybeNext(final boolean openNext) {
        synchronized (this) {
            closeCursor();
            int currentLength = getQueueSize();
            

            if (currentLength == 0) {
                return;
            }
            stop(false);

            mCursor = mQueue.get(mPlayPos);
            while (true) {
                if (mCursor != null && openFile(mCursor)) {
                    break;
                }
                closeCursor();
                if (mOpenFailedCounter++ < 10 && currentLength > 1) {
                    final int pos = getNextPosition(false);
                    if (pos < 0) {
                        gotoIdleState();
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
                    }
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
                    mCursor = mQueue.get(mPlayPos);
                } else {
                    mOpenFailedCounter = 0;
                    gotoIdleState();
                    if (mIsSupposedToBePlaying) {
                        mIsSupposedToBePlaying = false;
                        notifyChange(PLAYSTATE_CHANGED);
                    }
                    return;
                }
            }
            if (openNext) {
                setNextTrack();
            }
        }
    }
    
    private void createCurrentQueue() {
        if (mOriginalQueue == null) {
            mQueue = null;
        }
        else {
            mQueue = new ArrayList<Object>(mOriginalQueue);
            if (mShuffleMode != AudioModule.SHUFFLE_NONE) {
                Collections.shuffle(mQueue);
            }
            notifyChange(QUEUE_CHANGED);
        }
        
    }

    /**
     * @param force
     *            True to force the player onto the track next, false otherwise.
     * @return The next position to play.
     */
    private int getNextPosition(final boolean force) {
        
        if (mRepeatMode == AudioModule.REPEAT_ONE && !force) {
            if (mPlayPos < 0) {
                return 0;
            }
            return mPlayPos;
        } 
        int currentLength = getQueueSize();
        if (mPlayPos >= currentLength - 1) {
            if (mRepeatMode == AudioModule.REPEAT_ALL) {
                if (mShuffleMode != AudioModule.SHUFFLE_NONE) {
                    createCurrentQueue();
                }
                return 0;
            }
            return -1;
        }
        return mPlayPos + 1;
    }

    /**
     * Sets the track track to be played
     */
    private void setNextTrack() {
        mNextPlayPos = getNextPosition(false);
        if (mPlayPos != mNextPlayPos && mNextPlayPos >= 0 && mQueue != null) {
            mPlayer.openFile(mQueue.get(mNextPlayPos), true);
        }
    }

    /**/
//    private boolean wasRecentlyUsed(final int idx, int lookbacksize) {
//        if (lookbacksize == 0) {
//            return false;
//        }
//        final int histsize = mHistory.size();
//        if (histsize < lookbacksize) {
//            lookbacksize = histsize;
//        }
//        final int maxidx = histsize - 1;
//        for (int i = 0; i < lookbacksize; i++) {
//            final long entry = mHistory.get(maxidx - i);
//            if (entry == idx) {
//                return true;
//            }
//        }
//        return false;
//    }

    /**
     * Notify the change-receivers that something has changed.
     */
    private void notifyChange(final String what) {
        // final Intent intent = new Intent(what);
        // sendStickyBroadcast(intent);

        // Update the lockscreen controls
        updateRemoteControlClient(what);

        if (what.equals(META_CHANGED)) {
            if (proxyHasListeners(EVENT_CHANGE, false)) {
                KrollDict data = new KrollDict();
                data.put("track", mCursor);
                data.put(TiC.PROPERTY_DURATION, duration());
                data.put(TiC.PROPERTY_INDEX, mPlayPos);
                proxy.fireEvent(EVENT_CHANGE, data, false, false);
            }
        } else if (what.equals(QUEUE_CHANGED)) {
        }

        // Update the app-widgets
        if (mAppWidgetSmall != null) {
            mAppWidgetSmall.notifyChange(this, what);
        }
        if (mAppWidgetLarge != null) {
            mAppWidgetLarge.notifyChange(this, what);
        }
        if (mAppWidgetLargeAlternate != null) {
            mAppWidgetLargeAlternate.notifyChange(this, what);
        }
    }

    /**
     * Updates the lockscreen controls, if enabled.
     * 
     * @param what
     *            The broadcast
     */
    private void updateRemoteControlClient(final String what) {
        if (what.equals(PLAYSTATE_CHANGED)) {
            if (mEnableLockscreenControls && mRemoteControlClientCompat != null) {
                // If the playstate change notify the lock screen
                // controls
                mRemoteControlClientCompat
                        .setPlaybackState(mIsSupposedToBePlaying ? RemoteControlClient.PLAYSTATE_PLAYING
                                : RemoteControlClient.PLAYSTATE_PAUSED);
            }
            // if (mBuildNotification) {
            mNotificationHelper.goToIdleState(mIsSupposedToBePlaying);
            // }
        } else if (what.equals(META_CHANGED)) {
            Log.d(TAG,"updateRemoteControlClient " + what);
            updateMetadata();
        }

    }

    /**
     * Opens a file and prepares it for playback
     * 
     * @param path
     *            The path of the file to open
     */
    public boolean openFile(final Object object) {
        synchronized (this) {
            if (object == null) {
                return false;
            }
            if (mPlayer.openFile(object, false)) {
                mOpenFailedCounter = 0;
                return true;
            }
            stop(true);
            return false;
        }
    }

    /**
     * Returns the audio session ID
     * 
     * @return The current media player audio session ID
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /**
     * Sets the audio session ID.
     * 
     * @param sessionId
     *            : the audio session ID.
     */
    public void setAudioSessionId(final int sessionId) {
        synchronized (this) {
            mPlayer.setAudioSessionId(sessionId);
        }
    }

    /**
     * Returns the shuffle mode
     * 
     * @return The current shuffle mode (all, party, none)
     */
    public int getShuffleMode() {
        return mShuffleMode;
    }

    /**
     * Returns the repeat mode
     * 
     * @return The current repeat mode (all, one, none)
     */
    public int getRepeatMode() {
        return mRepeatMode;
    }

    public int removeTrack(final Object object) {
        int numremoved = 0;
        synchronized (this) {
            int index = mOriginalQueue.indexOf(object);
            while (index != -1) {
                numremoved += removeTracksInternal(index, index);
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    /**
     * Removes the range of tracks specified from the play list. If a file
     * within the range is the file currently being played, playback will move
     * to the next file after the range.
     * 
     * @param first
     *            The first file to be removed
     * @param last
     *            The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(final int first, final int last) {
        final int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    /**
     * Returns the position in the queue
     * 
     * @return the current position in the queue
     */
    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    /**
     * Returns the current
     * 
     * @return The current track
     */
    public Object getCurrent() {
        if (mCursor != null) {
            return mCursor;
        } else if (mOriginalQueue != null){
            return mOriginalQueue.get(mPlayPos);
        }
        return null;
    }

    /**
     * Seeks the current track to a specific time
     * 
     * @param position
     *            The time to seek to
     * @return The time to play the track at
     */
    public long seek(long position) {
        if (mPlayer.isInitialized()) {
            if (position < 0) {
                position = 0;
            } else if (position > mPlayer.duration()) {
                position = mPlayer.duration();
            }
            return mPlayer.seek(position);
        }
        return -1;
    }

    /**
     * Returns the current position in time of the currenttrack
     * 
     * @return The current playback position in miliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }
    
    /**
     * Returns the current state 
     * 
     * @return The current player state
     */
    public int state() {
        return mState;
    }
    
    /**
     * Returns the current state description
     * 
     * @return The current player state description
     */
    public String stateDescription() {
        return mStateDescription;
    }


    /**
     * Returns the full duration of the current track
     * 
     * @return The duration of the current track in miliseconds
     */
    public long duration() {
        if (mPlayer.isPrepared()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the queue (shuffled or playlist)
     * 
     * @return The queue as a Object[]
     */
    public Object[] getQueue() {
        synchronized (this) {
            if (mQueue != null) {
                return mQueue.toArray();
            }
            return null;
        }
    }
    
    /**
     * Returns the playlist
     * 
     * @return The playlist as a Object[]
     */
    public Object[] getPlaylist() {
        synchronized (this) {
            if (mOriginalQueue != null) {
                return mOriginalQueue.toArray();
            }
            return null;
        }
    }

    /**
     * @return True if music is playing, false otherwise
     */
    public boolean isPlaying() {
        if (mPlayer != null) {
            return mPlayer.isPlaying();
        }
        return mState == STATE_PLAYING;
    }

    /**
     * @return True if music is paused, false otherwise
     */
    public boolean isPaused() {
        if (mPlayer != null) {
            return mPlayer.isPaused();
        }
        return mState == STATE_PAUSED;
    }

    /**
     * @return True if music is stopped, false otherwise
     */
    public boolean isStopped() {
        return mState == STATE_STOPPED;
    }

    /**
     * Opens a list for playback
     * 
     * @param list
     *            The list of tracks to open
     c
     */
    public void open(final List<Object> list, final int position) {
        if (list == null) {
            return;
        }
        synchronized (this) {
//            final int listlength = list.size();
            mOriginalQueue = null;
            addToPlayList(list, -1);
            notifyChange(QUEUE_CHANGED);
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = 0;
            }
//            mHistory.clear();
            openCurrentAndNext();
        }
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    public void reset() {
        setPlaylist(null);
    }

    /**
     * Resumes or starts playback.
     */
    public void play() {
        if (mPausePending || mStopPending) {
            stop(!mPausePending || mStopPending);
            return;
        }
        mPausePending = false;
        mStopPending = false;
        tryToGetAudioFocus();

        if (mPlayer.isInitialized()) {

            if (mPlayer.isPrepared()) {
                mPlayPending = false;
                mPlayer.start();
                mIsSupposedToBePlaying = mPlayer.isPlaying();
                notifyChange(PLAYSTATE_CHANGED);

                mPlayerHandler.removeMessages(FADEDOWN);
                mPlayerHandler.sendEmptyMessage(FADEUP);

            }
        } else if (mOriginalQueue == null) { // first play
            if (proxy != null) {
                open(((StreamerProxy) proxy).getInternalPlaylist(), 0);
            }
        } else {
            openCurrentAndNext();
            play();
//            notifyChange(META_CHANGED);
        }
    }

    /**
     * Temporarily pauses playback.
     */
    @Override
    public void pause() {
        synchronized (this) {
            mPlayerHandler.removeMessages(FADEUP);

            if (mIsSupposedToBePlaying) {
                mPlayer.pause();
                gotoIdleState();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            } else if (mPlayPending) {
                mPausePending = true;
            }
        }
    }

    /**
     * Changes from the current track to the next track
     * @param force : used to still get next track even in repeat_one.
     */
    public void gotoNext(final boolean force) {
        synchronized (this) {
            if (mShuffleMode == AudioModule.SHUFFLE_RANDOM) {
                createCurrentQueue();
                mPlayPos = 0;
            }
            else {
                final int pos = getNextPosition(force);
                if (pos == mPlayPos) {
                    return;
                }
                if (pos < 0) {
                    gotoIdleState();
                    if (mIsSupposedToBePlaying) {
                        proxy.fireEvent(EVENT_END, null, false, true);
                        stop();
                        mPlayPos = -1;
                    }
                    return;
                }
                mPlayPos = pos;
            }
            mForcedTrackToEnd = true;
            stop(false);
            openCurrentAndNext();
            if (mPlayPending) {
                play();
            }
//            notifyChange(META_CHANGED);
        }
    }
    
    /**
     * Changes from the current track to the next track
     */
    public void gotoNext() {
        gotoNext(false);
    }

    /**
     * Changes from the current track to the previous played track
     */
    public void prev() {
        synchronized (this) {
            if (mPlayPos > 0) {
                mPlayPos--;
            } else {
                if (mShuffleMode == AudioModule.SHUFFLE_RANDOM) { 
                    createCurrentQueue();
                }
                mPlayPos = mQueue.size() - 1;
            }
            stop(false);
            openCurrent();
            play();
//            notifyChange(META_CHANGED);
        }
    }

    /**
     * We don't want to open the current and next track when the user is using
     * the {@code #prev()} method because they won't be able to travel back to
     * the previously listened track if they're shuffling.
     */
    private void openCurrent() {
        openCurrentAndMaybeNext(false);
    }


    /**
     * Sets the repeat mode
     * 
     * @param repeatmode
     *            The repeat mode to use
     */
    public void setRepeatMode(final int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            notifyChange(REPEATMODE_CHANGED);
        }
    }

    /**
     * Sets the shuffle mode
     * 
     * @param shufflemode
     *            The shuffle mode to use
     */
    public void setShuffleMode(final int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode) {
                return;
            }
            mShuffleMode = shufflemode;
            
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }

    public void setPlaylist(final List<Object> list) {
        final boolean playing = mState == STATE_PLAYING;
        final boolean emptyPlaylist = list != null;
        mIgnoreStateChange = emptyPlaylist;
        stop();
        mIgnoreStateChange = false;
        mOriginalQueue = null;
        mQueue = null;
        mPlayPos = 0;
        if (list != null) {
            addToPlayList(list, Integer.MAX_VALUE);
        }
        if (!emptyPlaylist && playing) {
            play();
        }
    }

    /**
     * Cycles through the different repeat modes
     */
    private void cycleRepeat() {
        if (mRepeatMode == AudioModule.REPEAT_NONE) {
            setRepeatMode(AudioModule.REPEAT_ALL);
        } else if (mRepeatMode == AudioModule.REPEAT_ALL) {
            setRepeatMode(AudioModule.REPEAT_ONE);
            if (mShuffleMode != AudioModule.SHUFFLE_NONE) {
                setShuffleMode(AudioModule.SHUFFLE_NONE);
            }
        } else {
            setRepeatMode(AudioModule.REPEAT_NONE);
        }
    }

    /**
     * Cycles through the different shuffle modes
     */
    private void cycleShuffle() {
        if (mShuffleMode == AudioModule.SHUFFLE_NONE) {
            setShuffleMode(AudioModule.SHUFFLE_SONGS);
            if (mRepeatMode == AudioModule.REPEAT_ONE) {
                setRepeatMode(AudioModule.REPEAT_ALL);
            }
        } else if (mShuffleMode == AudioModule.SHUFFLE_SONGS
                || mShuffleMode == AudioModule.SHUFFLE_RANDOM) {
            setShuffleMode(AudioModule.SHUFFLE_NONE);
        }
    }

    /**
     * Called when one of the lists should refresh or requery.
     */
    public void refresh() {
        notifyChange(REFRESH);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            final String command = intent.getStringExtra(CMDNAME);
            if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
                gotoNext();
            } else if (CMDPREVIOUS.equals(command)
                    || PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(command)
                    || TOGGLEPAUSE_ACTION.equals(action)) {
                if (mIsSupposedToBePlaying) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (CMDPLAY.equals(command)) {
                play();
            } else if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
                killNotification();
                setState(STATE_STOPPED);
                mBuildNotification = false;
            } else if (REPEAT_ACTION.equals(action)) {
                cycleRepeat();
            } else if (SHUFFLE_ACTION.equals(action)) {
                cycleShuffle();
            } else if (KILL_FOREGROUND.equals(action)) {
                mBuildNotification = false;
                killNotification();
            } else if (START_BACKGROUND.equals(action)) {
                mBuildNotification = true;
                buildNotification();
            } else if (UPDATE_LOCKSCREEN.equals(action)) {
                mEnableLockscreenControls = intent.getBooleanExtra(
                        UPDATE_LOCKSCREEN, true);
                if (mEnableLockscreenControls) {
                    setUpRemoteControlClient();
                    // Update the controls according to the current playback
                    notifyChange(PLAYSTATE_CHANGED);
                    notifyChange(META_CHANGED);
                } else {
                    // Remove then unregister the conrols
                    mRemoteControlClientCompat
                            .setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
                    RemoteControlHelper.unregisterRemoteControlClient(
                            mAudioManager, mRemoteControlClientCompat);
                }
            } else if (mAppWidgetSmall != null
                    && mAppWidgetSmall.getWidgetUpdateId().equals(command)) {
                final int[] small = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetSmall.performUpdate(AudioStreamerExoService.this,
                        small);
            } else if (mAppWidgetLarge != null
                    && mAppWidgetLarge.getWidgetUpdateId().equals(command)) {
                final int[] large = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLarge.performUpdate(AudioStreamerExoService.this,
                        large);
            } else if (mAppWidgetLargeAlternate != null
                    && mAppWidgetLargeAlternate.getWidgetUpdateId().equals(
                            command)) {
                final int[] largeAlt = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLargeAlternate.performUpdate(
                        AudioStreamerExoService.this, largeAlt);
            }
        }
    };

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAudioFocusChange(final int focusChange) {
            mPlayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0)
                    .sendToTarget();
        }
    };
    private int mState;
    private String mStateDescription;

    private static final class DelayedHandler extends Handler {

        private final WeakReference<AudioStreamerExoService> mService;

        /**
         * Constructor of <code>DelayedHandler</code>
         * 
         * @param service
         *            The service to use.
         */
        public DelayedHandler(final AudioStreamerExoService service) {
            mService = new WeakReference<AudioStreamerExoService>(service);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            if (mService.get().isPlaying()
                    || mService.get().mPausedByTransientLossOfFocus
                    || mService.get().mServiceInUse
                    || mService.get().mPlayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            mService.get().stopSelf(mService.get().mServiceStartId);
        }
    }

    private static final class MusicPlayerHandler extends Handler {

        private final WeakReference<AudioStreamerExoService> mService;

        private float mVolume = 1.0f;
        private float mCurrentVolume = 0.0f;
        private boolean mVolumeFading = false;

        /**
         * Constructor of <code>MusicPlayerHandler</code>
         * 
         * @param service
         *            The service to use.
         * @param looper
         *            The thread to run on.
         */
        public MusicPlayerHandler(final AudioStreamerExoService service,
                final Looper looper) {
            super(looper);
            mService = new WeakReference<AudioStreamerExoService>(service);
        }

        public void setVolume(final float volume) {
            mVolume = volume;
            if (!mVolumeFading) {
                mCurrentVolume = mVolume;
                mService.get().mPlayer.setVolume(mCurrentVolume);
            }
        }

        public boolean isVolumeFading() {
            return mVolumeFading;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
            case FADEDOWN:
                mCurrentVolume -= .05f;
                if (mCurrentVolume > .2f) {
                    mVolumeFading = true;
                    sendEmptyMessageDelayed(FADEDOWN, 10);
                } else {
                    mVolumeFading = false;
                    mCurrentVolume = .2f;
                }
                mService.get().mPlayer.setVolume(mCurrentVolume);
                break;
            case FADEUP:
                mCurrentVolume += .01f;
                if (mCurrentVolume < mVolume) {
                    mVolumeFading = true;
                    sendEmptyMessageDelayed(FADEUP, 10);
                } else {
                    mVolumeFading = false;
                    mCurrentVolume = mVolume;
                }
                mService.get().mPlayer.setVolume(mCurrentVolume);
                break;
            case SERVER_DIED:
                if (mService.get().mIsSupposedToBePlaying) {
                    mService.get().gotoNext();
                } else {
                    mService.get().openCurrentAndNext();
                }
                break;
            case TRACK_WENT_TO_NEXT:
                mService.get().mPlayPos = mService.get().mNextPlayPos;
                mService.get().closeCursor();
                if (mService.get().mPlayPos != -1) {
                    mService.get().mCursor = mService.get().mQueue.get(mService
                            .get().mPlayPos);
                    mService.get().buildNotification();
//                    mService.get().notifyChange(META_CHANGED);
                    mService.get().setNextTrack();
                    break;
                }
                //dont break if -1 (stop)
            case TRACK_ENDED:
                if (mService.get().mRepeatMode == AudioModule.REPEAT_ONE) {
                    if (mService.get().mCursor != null) {
                        mService.get().seek(0);
                        mService.get().play();
                    }
                    else {
                        mService.get().gotoNext(true);
                    }
                } else {
                    mService.get().gotoNext();
                }
                break;
            case RELEASE_WAKELOCK:
                mService.get().mWakeLock.release();
                break;
            case FOCUSCHANGE:
                switch (msg.arg1) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mService.get().isPlaying()) {
                        mService.get().mPausedByTransientLossOfFocus = false;
                    }
                    mService.get().pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    removeMessages(FADEUP);
                    sendEmptyMessage(FADEDOWN);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mService.get().isPlaying()) {
                        mService.get().mPausedByTransientLossOfFocus = true;
                    }
                    mService.get().pause();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!mService.get().isPlaying()
                            && mService.get().mPausedByTransientLossOfFocus) {
                        mService.get().mPausedByTransientLossOfFocus = false;
                        mCurrentVolume = 0f;
                        mService.get().mPlayer.setVolume(mCurrentVolume);
                        mService.get().play();
                    } else {
                        removeMessages(FADEDOWN);
                        sendEmptyMessage(FADEUP);
                    }
                    break;
                default:
                }
                break;
            default:
                break;
            }
        }
    }

    private static final class Shuffler {

        private final LinkedList<Integer> mHistoryOfNumbers = new LinkedList<Integer>();

        private final TreeSet<Integer> mPreviousNumbers = new TreeSet<Integer>();

        private final Random mRandom = new Random();

        private int mPrevious;

        /**
         * Constructor of <code>Shuffler</code>
         */
        public Shuffler() {
            super();
        }

        /**
         * @param interval
         *            The length the queue
         * @return The position of the next track to play
         */
        public int nextInt(final int interval) {
            int next;
            do {
                next = mRandom.nextInt(interval);
            } while (next == mPrevious && interval > 1
                    && !mPreviousNumbers.contains(Integer.valueOf(next)));
            mPrevious = next;
            mHistoryOfNumbers.add(mPrevious);
            mPreviousNumbers.add(mPrevious);
            cleanUpHistory();
            return next;
        }

        /**
         * Removes old tracks and cleans up the history preparing for new tracks
         * to be added to the mapping
         */
        private void cleanUpHistory() {
            if (!mHistoryOfNumbers.isEmpty()
                    && mHistoryOfNumbers.size() >= MAX_HISTORY_SIZE) {
                for (int i = 0; i < Math.max(1, MAX_HISTORY_SIZE / 2); i++) {
                    mPreviousNumbers.remove(mHistoryOfNumbers.removeFirst());
                }
            }
        }
    };

    private class ExoRendererBuilder implements RendererBuilder,
            ManifestCallback<HlsPlaylist> {

        private final String userAgent = TiApplication.getInstance()
                .getUserAgent();
        private final String url;
        private boolean isHLS = false;

        private StreamerExoPlayer player;
        private RendererBuilderCallback callback;

        public ExoRendererBuilder(String url) {
            this.url = url;
             isHLS = !url.endsWith("mp3");
        }

        @Override
        public void buildRenderers(StreamerExoPlayer player,
                RendererBuilderCallback callback) {
            this.player = player;
            this.callback = callback;

            if (isHLS) {
                HlsPlaylistParser parser = new HlsPlaylistParser();
                ManifestFetcher<HlsPlaylist> playlistFetcher =
                        new ManifestFetcher<HlsPlaylist>(url, new DefaultHttpDataSource(userAgent, null), parser);
                playlistFetcher.singleLoad(player.getMainHandler().getLooper(),
                        this);
            } else {
                // Build the video and audio renderers.
                
                DefaultSampleSource sampleSource =
                        new DefaultSampleSource(new FrameworkSampleExtractor(AudioStreamerExoService.this, Uri.parse(url), null), 2);
                    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                        null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, null, player.getMainHandler(),
                        player, 50);
                    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                        null, true, player.getMainHandler(), player);

                    MetadataTrackRenderer<Map<String, Object>> id3Renderer = new MetadataTrackRenderer<Map<String, Object>>(
                            sampleSource, new Id3Parser(),
                            player.getId3MetadataRenderer(), player
                                    .getMainHandler().getLooper());
                    
                    // Invoke the callback.
                    TrackRenderer[] renderers = new TrackRenderer[StreamerExoPlayer.RENDERER_COUNT];
                    renderers[StreamerExoPlayer.TYPE_VIDEO] = videoRenderer;
                    renderers[StreamerExoPlayer.TYPE_AUDIO] = audioRenderer;
                    renderers[StreamerExoPlayer.TYPE_TIMED_METADATA] = id3Renderer;
//                    renderers[StreamerExoPlayer.TYPE_DEBUG] = debugRenderer;
                    callback.onRenderers(null, null, renderers);
            }

        }
        
        @Override
        public void onSingleManifest(HlsPlaylist manifest) {
            
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

            DataSource dataSource = new UriDataSource(userAgent, bandwidthMeter);
            HlsChunkSource chunkSource = new HlsChunkSource(dataSource, url, manifest, bandwidthMeter, null,
                HlsChunkSource.ADAPTIVE_MODE_SPLICE);
            HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true, 3);
            MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, player.getMainHandler(), player, 50);
            MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

            MetadataTrackRenderer<Map<String, Object>> id3Renderer =
                new MetadataTrackRenderer<Map<String, Object>>(sampleSource, new Id3Parser(),
                    player.getId3MetadataRenderer(), player.getMainHandler().getLooper());

//            Eia608TrackRenderer closedCaptionRenderer = new Eia608TrackRenderer(sampleSource, player,
//                player.getMainHandler().getLooper());

            TrackRenderer[] renderers = new TrackRenderer[StreamerExoPlayer.RENDERER_COUNT];
            renderers[StreamerExoPlayer.TYPE_VIDEO] = videoRenderer;
            renderers[StreamerExoPlayer.TYPE_AUDIO] = audioRenderer;
            renderers[StreamerExoPlayer.TYPE_TIMED_METADATA] = id3Renderer;
//            renderers[DemoPlayer.TYPE_TEXT] = closedCaptionRenderer;
            callback.onRenderers(null, null, renderers);
            player.setVolume(mVolume);
        }

        @Override
        public void onSingleManifestError(IOException e) {
            callback.onRenderersError(e);
        }

    }

    private static final class MultiPlayer implements
            StreamerExoPlayer.Id3MetadataListener, StreamerExoPlayer.Listener,
            InfoListener, InternalErrorListener {

        public class PlayingItem {
            public String path = null;
            public AssetFileDescriptor assetFileDescriptor = null;
            public FileDescriptor fileDescriptor = null;
            public FileInputStream inputStream = null;

            PlayingItem(String path) {
                this.path = path;
            }

            PlayingItem(String path, AssetFileDescriptor assetFileDescriptor) {
                this.path = path;
                this.assetFileDescriptor = assetFileDescriptor;
            }

            PlayingItem(String path, FileInputStream inputStream) {
                this.path = path;
                this.inputStream = inputStream;
            }

            PlayingItem(String path, FileDescriptor fileDescriptor) {
                this.path = path;
                this.fileDescriptor = fileDescriptor;
            }
        }

        private final WeakReference<AudioStreamerExoService> mService;

        private StreamerExoPlayer mCurrentMediaPlayer = null;
        private StreamerExoPlayer mNextMediaPlayer;
        private float mVolume = 1.0f;
        
        private int state = -1;

        private Handler mHandler;

        private boolean mIsInitialized = false;

        private boolean mIsPreparing = false;
        private boolean mIsPaused = false;
        private boolean mNextIsPreparing = false;

        private PlayingItem mPlayingFile = null;
        private PlayingItem mNextPlayingFile = null;
        protected Timer mProgressTimer;

        /**
         * Constructor of <code>MultiPlayer</code>
         */
        public MultiPlayer(final AudioStreamerExoService service) {
            mService = new WeakReference<AudioStreamerExoService>(service);
        }

        private void releasePlayer(StreamerExoPlayer player) {
            if (player != null) {
                player.release();
            }
        }

        private boolean setDataSource(final String path) {
            if (mCurrentMediaPlayer != null) {
                releasePlayer(mCurrentMediaPlayer);
                mCurrentMediaPlayer = null;
            }
            if (mNextMediaPlayer != null) {
                releasePlayer(mNextMediaPlayer);
                mNextMediaPlayer = null;
            }
            if (path == null) {
                return false;
            }
            mCurrentMediaPlayer = new StreamerExoPlayer(
                    mService.get().new ExoRendererBuilder(path));
            mCurrentMediaPlayer.setPlayWhenReady(true);
            mCurrentMediaPlayer.addListener(this);
            mCurrentMediaPlayer.setMetadataListener(this);
            mCurrentMediaPlayer.setInfoListener(this);
            mCurrentMediaPlayer.setInternalErrorListener(this);
            mCurrentMediaPlayer.prepare();
            return true;
        }

        private boolean setNextDataSource(final String path) {
            if (mNextMediaPlayer != null) {
                releasePlayer(mNextMediaPlayer);
                mNextMediaPlayer = null;
            }
            
            if (path == null) {
                return false;
            }
            mNextMediaPlayer = new StreamerExoPlayer(
                    mService.get().new ExoRendererBuilder(path));
            mNextMediaPlayer.setPlayWhenReady(false);
            mNextMediaPlayer.addListener(this);
            mNextMediaPlayer.setInfoListener(this);
            mNextMediaPlayer.setInternalErrorListener(this);
            mNextMediaPlayer.prepare();

            return true;
        }

        public boolean openFile(final Object object, final boolean preparingNext) {
            synchronized (this) {
                if (object == null) {
                    return false;
                }

                try {
                    String url = null;
                    if (object instanceof FileProxy) {
                        url = ((FileProxy) object).getNativePath();
                    } else if (object instanceof SoundProxy) {
                        url = ((SoundProxy) object).getUrl();
                    } else if (object instanceof HashMap) {
                        url = TiConvert.toString(((HashMap) object)
                                .get(TiC.PROPERTY_URL));
                    } else {
                        url = TiConvert.toString(object);
                    }
                    if (preparingNext) {
                        mNextIsPreparing = false;
                    } else {
                        mIsPreparing = false;
                    }
                    if (preparingNext) {
                        mNextIsPreparing = true;
                        mNextPlayingFile = new PlayingItem(url);
                        setNextDataSource(mNextPlayingFile.path);
                    } else {
                        mIsPreparing = true;
                        mPlayingFile = new PlayingItem(url);
                        mIsInitialized = setDataSource(mPlayingFile.path);
                    }

                    // }
                    // }

                } catch (Throwable t) {
                    Log.w(TAG, "Issue while initializing : ", t);
                    return false;
                }
                if (!preparingNext) {
                    if (mNextIsPreparing) {
                        mService.get().setState(STATE_STARTING);
                    } else if (mIsInitialized) {
                        mService.get().setState(STATE_INITIALIZED);
                    }
                    if (mIsInitialized && !mIsPreparing) {
                        mService.get().onStartPlaying(mPlayingFile);
                    }
                }

                return mIsInitialized;
            }
        }

        /**
         * Sets the handler
         * 
         * @param handler
         *            The handler to use
         */
        public void setHandler(final Handler handler) {
            mHandler = handler;
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        public boolean isInitialized() {
            return mIsInitialized;
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        public boolean isPrepared() {
            return mIsInitialized && !mIsPreparing;
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        public boolean isPlaying() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.isPlaying();
            }
            return false;
        }

        public boolean isValid() {
            return isPrepared();
        }

        /**
         * @return True if the player is ready to go, false otherwise
         */
        public boolean isPaused() {
            return mIsPaused;
        }

        /**
         * Starts or resumes playback.
         */
        public void start() {
            if (mCurrentMediaPlayer == null)
                return;
            mCurrentMediaPlayer.start();
            mIsPaused = !mCurrentMediaPlayer.isPlaying();
            mService.get().setState(
                    !mIsPaused ? STATE_PLAYING : mService.get().mState);
            startProgressTimer();
        }

        /**
         * Resets the MediaPlayer to its uninitialized state.
         */
        public void stop() {
            if (mCurrentMediaPlayer != null) {
                mCurrentMediaPlayer.reset();
            }
            
            mPlayingFile = null;
            mIsInitialized = false;
            mIsPaused = false;
            mService.get().setState(STATE_STOPPED);
            stopProgressTimer();
        }

        /**
         * Releases resources associated with this MediaPlayer object.
         */
        public void release() {
            stop();
            releasePlayer(mCurrentMediaPlayer);
        }

        /**
         * Pauses playback. Call start() to resume.
         */
        public void pause() {
            if (mCurrentMediaPlayer == null) {
                return;
            }
            mCurrentMediaPlayer.pause();
            mIsPaused = true;
            mService.get().setState(STATE_PAUSED);
            stopProgressTimer();
        }

        /**
         * Gets the duration of the file.
         * 
         * @return The duration in milliseconds
         */
        public long duration() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getDuration();
            }
            return 0;
        }

        /**
         * Gets the current playback position.
         * 
         * @return The current position in milliseconds
         */
        public long position() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getCurrentPosition();
            }
            return 0;
        }

        /**
         * Sets the current playback position.
         * 
         * @param whereto
         *            The offset in milliseconds from the start to seek to
         * @return The offset in milliseconds from the start to seek to
         */
        public long seek(final long whereto) {
            if (mCurrentMediaPlayer != null) {
                mCurrentMediaPlayer.seekTo((int) whereto);
            }
            return whereto;
        }

        /**
         * Sets the volume on this player.
         * 
         * @param vol
         *            Left and right volume scalar
         */
        public void setVolume(final float vol) {
            mVolume = vol;
            if (mCurrentMediaPlayer != null) {
                mCurrentMediaPlayer.setVolume(vol);
            }
        }

        /**
         * Sets the audio session ID.
         * 
         * @param sessionId
         *            The audio session ID
         */
        public void setAudioSessionId(final int sessionId) {
            mCurrentMediaPlayer.setAudioSessionId(sessionId);
        }

        /**
         * Returns the audio session ID.
         * 
         * @return The current audio session ID.
         */
        public int getAudioSessionId() {
            return mCurrentMediaPlayer.getAudioSessionId();
        }

        public PlayingItem getPlayingFile() {
            return mPlayingFile;
        }

        /**
         * Sets the audio stream type.
         * 
         * @param streamtype
         *            the audio stream type
         */
        // public void setAudioStreamType(final int streamtype) {
        // mCurrentMediaPlayer.setAudioStreamType(streamtype);
        // }

        private void startProgressTimer() {
            if (mProgressTimer == null) {
                mProgressTimer = new Timer(true);
            } else {
                mProgressTimer.cancel();
                mProgressTimer = new Timer(true);
            }
            if (isPlaying()) {
                mService.get().onProgress();
            }
            mProgressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (isPlaying()) {
                            mService.get().onProgress();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Issue while progressTimer run: ", t);
                    }
                }
            }, 1000, 1000);
        }

        private void stopProgressTimer() {
            if (mProgressTimer != null) {
                mProgressTimer.cancel();
                mProgressTimer = null;
            }
        }

        @Override
        public void onLoadCompleted(StreamerExoPlayer player, int sourceId,
                long bytesLoaded) {
            if (player == mCurrentMediaPlayer) {
                mIsPreparing = false;
                mService.get().setState(STATE_INITIALIZED);
                mService.get().notifyChange(PLAYSTATE_CHANGED);
                if (mIsInitialized) {
                    mService.get().onStartPlaying(mPlayingFile);
                }
            } else if (player == mNextMediaPlayer) {
                mNextIsPreparing = false;
            }

        }

        @Override
        public void onRendererInitializationError(StreamerExoPlayer player,
                Exception e) {
//            onError(player, e);
        }

        @Override
        public void onAudioTrackInitializationError(StreamerExoPlayer player,
                InitializationException e) {
//            onError(player, e);
        }

        @Override
        public void onAudioTrackWriteError(StreamerExoPlayer player,
                WriteException e) {
//          onError(player, e);
        }

        @Override
        public void onDecoderInitializationError(StreamerExoPlayer player,
                DecoderInitializationException e) {
//            onError(player, e);
        }

        @Override
        public void onCryptoError(StreamerExoPlayer player, CryptoException e) {
//            onError(player, e);
        }

        @Override
        public void onUpstreamError(StreamerExoPlayer player, int sourceId,
                IOException e) {
//            onError(player, e);
        }

        @Override
        public void onConsumptionError(StreamerExoPlayer player, int sourceId,
                IOException e) {
//            onError(player, e);
        }

        @Override
        public void onDrmSessionManagerError(StreamerExoPlayer player,
                Exception e) {
//            onError(player, e);
        }

        @Override
        public void onVideoFormatEnabled(StreamerExoPlayer player,
                String formatId, int trigger, int mediaTimeMs) {
        }

        @Override
        public void onAudioFormatEnabled(StreamerExoPlayer player,
                String formatId, int trigger, int mediaTimeMs) {
        }

        @Override
        public void onDroppedFrames(StreamerExoPlayer player, int count,
                long elapsed) {
        }

        @Override
        public void onBandwidthSample(StreamerExoPlayer player, int elapsedMs,
                long bytes, long bitrateEstimate) {
        }

        @Override
        public void onLoadStarted(StreamerExoPlayer player, int sourceId,
                String formatId, int trigger, boolean isInitialization,
                int mediaStartTimeMs, int mediaEndTimeMs, long length) {

        }
        
        

        @Override
        public void onStateChanged(StreamerExoPlayer player,
                boolean playWhenReady, int playbackState) {
            if (playbackState == StreamerExoPlayer.STATE_BUFFERING) {
                mService.get().onBufferingUpdate(player.getBufferedPosition(), player.getBufferPercentage());
            }
            else if (player == mCurrentMediaPlayer && state != playbackState) {
                state = playbackState;
                if (playbackState == StreamerExoPlayer.STATE_ENDED) {
                    if (!mService.get().mForcedTrackToEnd) {
                        if (mNextMediaPlayer != null) {
                            releasePlayer(mCurrentMediaPlayer);
                            mCurrentMediaPlayer = mNextMediaPlayer;
                            if (mCurrentMediaPlayer != null) {
                                mCurrentMediaPlayer.setMetadataListener(this);
                                mCurrentMediaPlayer.setPlayWhenReady(true);
                            }
                            mPlayingFile = mNextPlayingFile;
                            mIsPreparing = mNextIsPreparing;
                            mNextMediaPlayer = null;
                            mNextPlayingFile = null;
                            mNextIsPreparing = false;
                            mHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
                        } else {
                            mService.get().mWakeLock.acquire(30000);
                            mHandler.sendEmptyMessage(TRACK_ENDED);
                            mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
                        }
                    }
                } else if (playbackState == StreamerExoPlayer.STATE_READY) {
                    mIsPreparing = false;
                    if (mIsInitialized) {
                        if (player.isPlaying()) {
                            mService.get().onStartPlaying(mPlayingFile);
                            // playback started, simulate state
                            start();
                        }
                        else {
                            mService.get().setState(STATE_INITIALIZED);
                        }
                    }
                }
            } else {
                if (playbackState == StreamerExoPlayer.STATE_READY) {
                    if (player == mNextMediaPlayer) {
                        mNextIsPreparing = false;
                    }
                } else if (playbackState == StreamerExoPlayer.STATE_ENDED) {
                    if (player == mNextMediaPlayer) {
                        mNextMediaPlayer = null;
                    }
                    if (player == mCurrentMediaPlayer) {
                        mCurrentMediaPlayer = null;
                    }
                    releasePlayer(player);
                }
            }
        }

        @Override
        public void onError(StreamerExoPlayer player, Exception e) {
            Log.e(TAG, "onError " + e.getLocalizedMessage());
            state = ExoPlayer.STATE_IDLE;
            int code = -1;
            boolean needsStop = false;
            String msg = "Unknown media error.";
            int what = 0;
//            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
//                msg = "Media server died";
//                needsStop = true;
//            }
            if (e instanceof FileNotFoundException) {
                what  = MediaPlayer.MEDIA_ERROR_IO;
                code = 404;
                msg = "File can't be accessed: " + mPlayingFile.path;
                needsStop = true;
            } else if (e instanceof InvalidResponseCodeException)  {
                what  = MediaPlayer.MEDIA_ERROR_IO;
                code = ((InvalidResponseCodeException) e).responseCode;
                msg = e.getLocalizedMessage();
                needsStop = true;
            }
            mService.get().onError(code, msg);
            if (needsStop) {
                mIsInitialized = false;
                mPlayingFile = null;
                mService.get().closeCursor();
            }
            switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
//                releasePlayer(mCurrentMediaPlayer);
//                mCurrentMediaPlayer = null;
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(SERVER_DIED), 2000);
                break;
            default:
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(TRACK_ENDED), 200);
                break;
            }
        }

        @Override
        public void onVideoSizeChanged(StreamerExoPlayer player, int width,
                int height, float pixelWidthHeightRatio) {
        }

        @Override
        public void onId3Metadata(StreamerExoPlayer player,
                Map<String, Object> metadata) {
            mService.get().updateMetadata(metadata);
        }

        public int getCurrentPosition() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getCurrentPosition();
            }
            return 0;
        }

        public int getDuration() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getDuration();
            }
            return 0;
        }

        public int getBufferPercentage() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getBufferPercentage();
            }
            return 0;
        }
        public long getBufferPosition() {
            if (mCurrentMediaPlayer != null) {
                return mCurrentMediaPlayer.getBufferedPosition();
            }
            return 0;
        }

    }

    private class LoadLocalCoverArtTask extends
            AsyncTask<TiDrawableReference, Void, Void> {
        private Cache cache;
        private TiDrawableReference imageref;

        LoadLocalCoverArtTask(Cache cache) {
            this.cache = cache;
        }

        @Override
        protected Void doInBackground(TiDrawableReference... params) {
            imageref = params[0];
            String cacheKey = imageref.getCacheKey();
            Cache cache = TiApplication.getImageMemoryCache();
            Bitmap bitmap = (cacheKey != null) ? cache.get(cacheKey) : null;
            if (bitmap == null) {
                Drawable drawable = imageref.getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                    cache.set(imageref.getUrl(), bitmap);
                }
            }

            updateBitmapMetadata(bitmap);
            return null;
        }
    }

    static final HashMap<String, Integer> METADATAS = new HashMap<String, Integer>() {
        {
            put("album", MediaMetadataRetriever.METADATA_KEY_ALBUM);
            put("artist", MediaMetadataRetriever.METADATA_KEY_ARTIST);
            put("albumartist", MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            put("title", MediaMetadataRetriever.METADATA_KEY_TITLE);
            put("author", MediaMetadataRetriever.METADATA_KEY_AUTHOR);
            put("compilation", MediaMetadataRetriever.METADATA_KEY_COMPILATION);
            put("composer", MediaMetadataRetriever.METADATA_KEY_COMPOSER);
            put("date", MediaMetadataRetriever.METADATA_KEY_DATE);
            put("genre", MediaMetadataRetriever.METADATA_KEY_GENRE);
        }
    };

    static final HashMap<String, Integer> METADATAS_LONG = new HashMap<String, Integer>() {
        {
            put("tracknumber",
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            put("duration", MediaMetadataRetriever.METADATA_KEY_DURATION);
            // put("bitrate", MediaMetadataRetriever.METADATA_KEY_BITRATE);
            put("year", MediaMetadataRetriever.METADATA_KEY_YEAR);
        }
    };

    private void runPicassoRequest(final TiDrawableReference imageref) {
        Picasso picasso = TiApplication.getPicassoInstance();

        if (proxy != null && proxy.hasProperty(TiC.PROPERTY_HTTP_OPTIONS)) {
            // Prepare OkHttp
            final Context context = TiApplication.getAppContext();
            picasso = new Picasso.Builder(context).downloader(
                    new OkHttpDownloader(context) {
                        @Override
                        protected HttpURLConnection openConnection(Uri uri)
                                throws IOException {
                            HttpURLConnection connection = super
                                    .openConnection(uri);
                            TiApplication
                                    .prepareURLConnection(
                                            connection,
                                            (HashMap) proxy
                                                    .getProperty(TiC.PROPERTY_HTTP_OPTIONS));
                            return connection;
                        }
                    }).build();
        }
        // picasso will cancel running request if reusing
        picasso.cancelRequest(this);
        picasso.load(imageref.getUrl()).into(this);
    }

    protected void onProgress() {
        if (proxyHasListeners(EVENT_PROGRESS, false)) {
            int position = mPlayer.getCurrentPosition();
            int duration = mPlayer.getDuration();
//            int buffer = mPlayer.getBufferPercentage();
            KrollDict event = new KrollDict();
            event.put("progress", position);
//            event.put("buffer", buffer);
            event.put(TiC.PROPERTY_DURATION, duration);
            proxy.fireEvent(EVENT_PROGRESS, event, false, false);
        }
    }

    private void handleUpdateBitmapMetadata(final Bitmap bitmap) {
        mNotificationHelper.updateAlbumArt(bitmap);
        // this needs to be called in a background thread because of the copy
        if (mRemoteControlClientCompat == null || bitmap == null)
            return;
        Bitmap newBitmap = bitmap.copy(bitmap.getConfig(),
                bitmap.isMutable() ? true : false);
        if (currentMetadataEditor != null) {
            currentMetadataEditor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK,
                    newBitmap);
            return;
        }

        MetadataEditorCompat metadataEditor = mRemoteControlClientCompat
                .editMetadata(false);
        metadataEditor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, newBitmap);
        metadataEditor.apply();

    }

    private boolean updatingMetadata = false;

    private void updateMetadata(final Map<String, Object> metadata) {
        if (mRemoteControlClientCompat == null || updatingMetadata)
            return;
        updatingMetadata = true;
        // for now we DONT call with "true". There is a recycle on the bitmap
        // which is bad news for when the bitmap is use somewhere else.
        // instead we update all datas :s
        currentMetadataEditor = mRemoteControlClientCompat.editMetadata(true);
        currentMetadataEditor
                .putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, null);
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (METADATAS.containsKey(key)) {
                    currentMetadataEditor.putString(METADATAS.get(key),
                            TiConvert.toString(value));
                } else if (METADATAS_LONG.containsKey(key)) {
                    currentMetadataEditor.putLong(METADATAS_LONG.get(key),
                            TiConvert.toInt(value));
                }
            }
            if (!metadata.containsKey("duration") && mPlayer != null
                    && mPlayer.isPlaying()) {
                currentMetadataEditor.putLong(
                        MediaMetadataRetriever.METADATA_KEY_DURATION,
                        mPlayer.duration());
            }
            TiDrawableReference imageref = TiDrawableReference.fromObject(
                    proxy, metadata.get("artwork"));
            if (imageref.isNetworkUrl()) {
                // only picasso requests needs to be run in main thread
                if (TiApplication.isUIThread()) {
                    runPicassoRequest(imageref);
                } else {
                    uiHandler.obtainMessage(MSG_PICASSO_REQUEST, imageref)
                            .sendToTarget();
                }

            } else if (!imageref.isTypeNull()) {
                (new LoadLocalCoverArtTask(TiApplication.getImageMemoryCache()))
                        .execute(imageref);
            }

        }

        currentMetadataEditor.apply();
        currentMetadataEditor = null;
        updatingMetadata = false;
    }

    public void updateMetadata() {
        HashMap<String, Object> metadata = null;
        if (proxy != null && proxy.hasProperty(TiC.PROPERTY_METADATA)) {
            metadata = (HashMap<String, Object>) proxy
                    .getProperty(TiC.PROPERTY_METADATA);
        } else if (mCursor instanceof HashMap) {
            metadata = (HashMap<String, Object>) mCursor;
        }

        updateMetadata(metadata);
        mNotificationHelper.updateMetadata(metadata);
    }

    private void updateBitmapMetadata(final Bitmap bitmap) {
        (new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                handleUpdateBitmapMetadata(bitmap);
                return null;
            }
        }).execute();
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
        updateBitmapMetadata(bitmap);
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        updateBitmapMetadata(null);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_PICASSO_REQUEST: {
            runPicassoRequest((TiDrawableReference) msg.obj);
            // handleUpdateMetadata((HashMap<String, Object>) msg.obj);
            return true;
        }
        case MSG_UPDATE_BITMAP_METADATA: {
            handleUpdateBitmapMetadata((Bitmap) msg.obj);
            return true;
        }
        }
        return false;
    }

    private int mRemoteControlClientState = 0;

    private void setState(int state) {
        if (state == mState)
            return;
        mState = state;

        int remoteState = mRemoteControlClientState;
        switch (state) {
        case STATE_BUFFERING:
            mStateDescription = STATE_BUFFERING_DESC;
            break;
        case STATE_INITIALIZED:
            mStateDescription = STATE_INITIALIZED_DESC;
            break;
        case STATE_PAUSED:
            remoteState = RemoteControlClient.PLAYSTATE_PAUSED;
            mStateDescription = STATE_PAUSED_DESC;
            break;
        case STATE_PLAYING:
            remoteState = RemoteControlClient.PLAYSTATE_PLAYING;
            mStateDescription = STATE_PLAYING_DESC;
            break;
        case STATE_STARTING:
            mStateDescription = STATE_STARTING_DESC;
            break;
        case STATE_STOPPED:
            remoteState = RemoteControlClient.PLAYSTATE_STOPPED;
            mStateDescription = STATE_STOPPED_DESC;
            break;
        case STATE_STOPPING:
            remoteState = RemoteControlClient.PLAYSTATE_STOPPED;
            mStateDescription = STATE_STOPPING_DESC;
            break;
        case STATE_WAITING_FOR_DATA:
            mStateDescription = STATE_WAITING_FOR_DATA_DESC;
            break;
        case STATE_WAITING_FOR_QUEUE:
            mStateDescription = STATE_WAITING_FOR_QUEUE_DESC;
            break;
         default:
             mStateDescription = "";
             break;
        }
        if (mRemoteControlClientCompat != null
                && mRemoteControlClientState != remoteState) {
            mRemoteControlClientState = remoteState;
            mRemoteControlClientCompat
                    .setPlaybackState(mRemoteControlClientState);
        }
        if (proxy != null) {
            proxy.setProperty("state", state);
            // proxy.setProperty("stateDescription", stateDescription);
        }
        Log.d(TAG, "Audio state changed: " + mStateDescription, Log.DEBUG_MODE);

        if (!mIgnoreStateChange && proxyHasListeners(EVENT_STATE, false)) {
            KrollDict data = new KrollDict();
            data.put("state", state);
            data.put("description", mStateDescription);
            proxy.fireEvent(EVENT_STATE, data, false, false);
        }
    }

    public void setVolume(final float volume) {
        mVolume = volume;
        mPlayerHandler.setVolume(volume);
    }

    @Override
    public void onAppPaused() {
        inBackground = true;
        if (proxy != null
                && TiConvert
                        .toBoolean(proxy.getProperty("notifyOnPause"), true)) {
            mNotificationHelper.showNotification();
        }
    }

    @Override
    public void onAppResume() {
        inBackground = false;
    }

    public void onStartPlaying(final MultiPlayer.PlayingItem playingItem) {
        if (proxy != null && proxy.hasProperty(TiC.PROPERTY_TIME)) {
            seek(TiConvert.toLong(proxy.getProperty(TiC.PROPERTY_TIME), 0));
        }
        if (mPlayPending) {
            play();
        }

        if (!mIsSupposedToBePlaying) {
            mIsSupposedToBePlaying = true;
            notifyChange(PLAYSTATE_CHANGED);
        }
        // Update the notification
        mBuildNotification = mIsSupposedToBePlaying;
        buildNotification();
        notifyChange(META_CHANGED);

    }

    private void aquireWifiLock() {
        if (mWifiLock != null && !mWifiLock.isHeld()) {
            mWifiLock.acquire();
        }
    }

    private void releaseWifiLock() {
        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    void tryToGetAudioFocus() {
        if (mAudioFocus != AudioFocus.Focused
                && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager
                        .requestAudioFocus(mAudioFocusListener,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN)) {
            mAudioFocus = AudioFocus.Focused;
            RemoteControlHelper.registerRemoteControlClient(mAudioManager,
                    mRemoteControlClientCompat);
            AudioModule.widgetGetsFocused(this);
        }
        aquireWifiLock();

    }

    void giveUpAudioFocus() {
        if (mAudioFocus == AudioFocus.Focused
                && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager
                        .abandonAudioFocus(mAudioFocusListener)) {
            // Remove the audio focus listener and lock screen controls
            mAudioFocus = AudioFocus.NoFocusNoDuck;
            RemoteControlHelper.unregisterRemoteControlClient(mAudioManager,
                    mRemoteControlClientCompat);
            AudioModule.widgetAbandonsFocused(this);
        }
        releaseWifiLock();

    }

}
