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
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.RemoteControlClient;
import android.media.RemoteController.MetadataEditor;
import android.media.audiofx.AudioEffect;
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
import android.os.RemoteException;
import android.webkit.URLUtil;

import java.io.FileDescriptor;
import java.io.FileInputStream;
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
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.view.TiDrawableReference;

import ti.modules.titanium.filesystem.FileProxy;
import ti.modules.titanium.audio.AudioFocusHelper;
import ti.modules.titanium.audio.StreamerProxy;
import ti.modules.titanium.audio.MediaButtonHelper;
import ti.modules.titanium.audio.RemoteControlClientCompat;
import ti.modules.titanium.audio.RemoteControlHelper;
import ti.modules.titanium.audio.SoundProxy;
import ti.modules.titanium.audio.RemoteControlClientCompat.MetadataEditorCompat;

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
public class AudioStreamerService extends TiEnhancedService implements Target,
        Callback, AppStateListener {
    private static final String TAG = "AudioStreamerService";
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

//    public static final String CMDNOTIF = "buttonId";

    /**
     * Moves a list to the front of the queue
     */
    public static final int NOW = 1;

    /**
     * Moves a list to the next position in the queue
     */
    public static final int NEXT = 2;

    /**
     * Moves a list to the last position in the queue
     */
    public static final int LAST = 3;

    /**
     * Shuffles no songs, turns shuffling off
     */
    public static final int SHUFFLE_NONE = 0;

    /**
     * Shuffles all songs
     */
    public static final int SHUFFLE_NORMAL = 1;

    /**
     * Party shuffle
     */
    public static final int SHUFFLE_AUTO = 2;

    /**
     * Turns repeat off
     */
    public static final int REPEAT_NONE = 0;

    /**
     * Repeats the current track in a list
     */
    public static final int REPEAT_CURRENT = 1;

    /**
     * Repeats all the tracks in a list
     */
    public static final int REPEAT_ALL = 2;

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
    private static final LinkedList<Integer> mHistory = new LinkedList<Integer>();

    /**
     * Used to shuffle the tracks
     */
    private static final Shuffler mShuffler = new Shuffler();

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

    private int mPlayListLen = 0;

    private int mPlayPos = -1;

    private int mNextPlayPos = -1;

    private int mOpenFailedCounter = 0;

    private int mMediaMountedCount = 0;

    private int mShuffleMode = SHUFFLE_NONE;

    private int mRepeatMode = REPEAT_NONE;

    private int mServiceStartId = -1;

    private List<Object> mPlayList = null;

    private List<Object> mAutoShuffleList = null;

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
    public static final String EVENT_ACTION = "action";
    public static final String EVENT_PROGRESS = "progress";
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

    /**
     * Used to build the notification
     */
    private NotificationHelper mNotificationHelper;
    protected Handler uiHandler;
    MetadataEditorCompat currentMetadataEditor = null;

    public static AudioStreamerService sFocusedStreamer = null;

    public static void streamerGetsFocus(final AudioStreamerService streamer) {
        sFocusedStreamer = streamer;
    }

    public static void streamerAbandonsFocus(final AudioStreamerService streamer) {
        if (streamer == sFocusedStreamer) {
            sFocusedStreamer = null;
        }
    }

    public static AudioStreamerService focusedStreamer() {
        return sFocusedStreamer;
    }

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
        } else if (mPlayListLen > 0 || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            final Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }
        return super.onUnbind(intent);
    }

    public void onError(final int what, final String msg) {
        mPlayPending = false;
        mPausePending = false;
        stop();
        if (proxyHasListeners(EVENT_ERROR, false)) {
            KrollDict data = new KrollDict();
            data.putCodeAndMessage(what, msg);
            proxy.fireEvent(EVENT_ERROR, data, false, false);
        }
    }
    
    private int mCurrentBuffering = -1;
    public void onBufferingUpdate(int percent) {
        if (mCurrentBuffering == percent) return;
        mCurrentBuffering = percent;
        if (mCurrentBuffering == 100) {
            setState(isPlaying()?STATE_PLAYING:(isPaused()?STATE_PAUSED:STATE_STOPPED));
        }
        else {
            setState(STATE_BUFFERING);
        }
        if (proxyHasListeners(EVENT_BUFFERING, false)) {
            KrollDict event = dictForEvent();
            event.put(TiC.PROPERTY_PROGRESS, percent);
            proxy.fireEvent(EVENT_BUFFERING, event, false, false);
        }
    }
    
    private boolean proxyHasListeners(final String type, final boolean checkParent) {
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
            for (int i = 0; i < mPendingCommandIntents.size(); i++) {
                handleIntent(mPendingCommandIntents.get(i));
            }
            mPendingCommandIntents.clear();
            mPendingCommandIntents = null;
        }
    }
    @Override
    protected void bindToProxy(final TiEnhancedServiceProxy proxy) {
        super.bindToProxy(proxy);
        if (this.proxy != null) {
         // Initialze the notification helper
            mNotificationHelper = new NotificationHelper(this,
                    ((StreamerProxy) proxy).getNotificationIcon(), 
                    ((StreamerProxy) proxy).getNotificationViewId(),
                    ((StreamerProxy) proxy).getNotificationExtandedViewId());
            // Use the remote control APIs (if available and the user allows it) to
            // set the playback state
            mEnableLockscreenControls = ((StreamerProxy) proxy).getEnableLockscreenControls();
            setUpRemoteControlClient();

            setVolume(TiConvert.toFloat(proxy.getProperty(TiC.PROPERTY_VOLUME), 1.0f));
            
            
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
                packageName + ".TiMediaButtonIntentReceiver");
        MediaButtonHelper.registerMediaButtonEventReceiverCompat(mAudioManager, mMediaButtonReceiverComponent);
        

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
    
    private List<Intent> mPendingCommandIntents = new ArrayList<Intent>();
    
    private void handleIntent(final Intent intent) {
        if (!mStarted) {
            mPendingCommandIntents.add(intent);
            return;
        }
        if (intent != null) {
            final String action = intent.getAction();
            final String command = intent.getStringExtra(CMDNAME);
            if (proxyHasListeners(EVENT_ACTION, false)) {
                KrollDict event = dictForEvent();
                event.put("command", command);
                proxy.fireEvent(EVENT_ACTION, event, false, false);
            }
            if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
                gotoNext(false);
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
                updateMetadata();
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
        setState(STATE_STOPPING);
        if (mPlayPending) {
            mStopPending = true;
            return;
        }
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        else {
            setState(STATE_STOPPED);
        }
        // mFileToPlay = null;
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
            if (last < first) {
                return 0;
            } else if (first < 0) {
                first = 0;
            } else if (last >= mPlayListLen) {
                last = mPlayListLen - 1;
            }

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= last - first + 1;
            }
            for (int i = first; i <= first; i++) {
                mPlayList.remove(i);
            }
            mPlayListLen -= mPlayList.size();

            if (gotonext) {
                if (mPlayListLen == 0) {
                    stop(true);
                    mPlayPos = -1;
                } else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    final boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
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

    /**
     * Adds a list to the playlist
     * 
     * @param list
     *            The list to add
     * @param position
     *            The position to place the tracks
     */
    public void addToPlayList(final List<Object> list, int position) {
        if (position < 0) {
            position = 0;
        }
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }
        if (mPlayList == null) {
            mPlayList = new ArrayList<Object>();
        }

        mPlayList.addAll(position, list);
        mPlayListLen = mPlayList.size();

        if (mPlayListLen == 0) {
            closeCursor();
            notifyChange(META_CHANGED);
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

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            mCursor = mPlayList.get(mPlayPos);
            while (true) {
                if (mCursor != null && openFile(mCursor)) {
                    break;
                }
                closeCursor();
                if (mOpenFailedCounter++ < 10 && mPlayListLen > 1) {
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
                    mCursor = mPlayList.get(mPlayPos);
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

    /**
     * @param force
     *            True to force the player onto the track next, false otherwise.
     * @return The next position to play.
     */
    private int getNextPosition(final boolean force) {
        if (!force && mRepeatMode == REPEAT_CURRENT) {
            if (mPlayPos < 0) {
                return 0;
            }
            return mPlayPos;
        } else if (mShuffleMode == SHUFFLE_NORMAL) {
            if (mPlayPos >= 0) {
                mHistory.add(mPlayPos);
            }
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            final int numTracks = mPlayListLen;
            final int[] tracks = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                tracks[i] = i;
            }

            final int numHistory = mHistory.size();
            int numUnplayed = numTracks;
            for (int i = 0; i < numHistory; i++) {
                final int idx = mHistory.get(i).intValue();
                if (idx < numTracks && tracks[idx] >= 0) {
                    numUnplayed--;
                    tracks[idx] = -1;
                }
            }
            if (numUnplayed <= 0) {
                if (mRepeatMode == REPEAT_ALL || force) {
                    numUnplayed = numTracks;
                    for (int i = 0; i < numTracks; i++) {
                        tracks[i] = i;
                    }
                } else {
                    return -1;
                }
            }
            int skip = 0;
            if (mShuffleMode == SHUFFLE_NORMAL || mShuffleMode == SHUFFLE_AUTO) {
                skip = mShuffler.nextInt(numUnplayed);
            }
            int cnt = -1;
            while (true) {
                while (tracks[++cnt] < 0) {
                    ;
                }
                skip--;
                if (skip < 0) {
                    break;
                }
            }
            return cnt;
        } else if (mShuffleMode == SHUFFLE_AUTO) {
            doAutoShuffleUpdate();
            return mPlayPos + 1;
        } else {
            if (mPlayPos >= mPlayListLen - 1) {
                if (mRepeatMode == REPEAT_NONE && !force) {
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    /**
     * Sets the track track to be played
     */
    private void setNextTrack() {
        mNextPlayPos = getNextPosition(false);
        if (mNextPlayPos >= 0 && mPlayList != null) {
            mPlayer.openFile(mPlayList.get(mNextPlayPos), true);
        }
    }

    /**
     * Creates a shuffled playlist used for party mode
     */
    private boolean makeAutoShuffleList() {
        if (mPlayList != null) {
            mAutoShuffleList = new ArrayList<Object>(mPlayList);
        }
        else {
            mAutoShuffleList = null;
        }
        return true;
    }

    /**
     * Creates the party shuffle playlist
     */
    private void doAutoShuffleUpdate() {
        if (mAutoShuffleList == null) {
            return;
        }
        boolean notify = false;
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        final int toAdd = 7 - (mPlayListLen - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < toAdd; i++) {
            int lookback = mHistory.size();
            int idx = -1;
            while (true) {
                idx = mShuffler.nextInt(mAutoShuffleList.size());
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            mHistory.add(idx);
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            mPlayList.add(mAutoShuffleList.get(idx));
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**/
    private boolean wasRecentlyUsed(final int idx, int lookbacksize) {
        if (lookbacksize == 0) {
            return false;
        }
        final int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            lookbacksize = histsize;
        }
        final int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            final long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notify the change-receivers that something has changed.
     */
    private void notifyChange(final String what) {
//        final Intent intent = new Intent(what);
//        sendStickyBroadcast(intent);

        // Update the lockscreen controls
        updateRemoteControlClient(what);

        if (what.equals(META_CHANGED)) {
            if (proxyHasListeners(EVENT_CHANGE, false)) {
                proxy.fireEvent(EVENT_CHANGE, dictForEvent(), false, false);
            }
            getMetadataFromUrl(mPlayer.getPlayingFile());
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
//            if (mBuildNotification) {
                mNotificationHelper.goToIdleState(mIsSupposedToBePlaying);
//            }
        } else if (what.equals(META_CHANGED)) {
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
     * Indicates if the media storeage device has been mounted or not
     * 
     * @return 1 if Intent.ACTION_MEDIA_MOUNTED is called, 0 otherwise
     */
    public int getMediaMountedCount() {
        return mMediaMountedCount;
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

    /**
     * Removes all instances of the track with the given ID from the playlist.
     * 
     * @param id
     *            The id to be removed
     * @return how many instances of the track were removed
     */
    public int removeTrack(final Object object) {
        int numremoved = 0;
        synchronized (this) {
            int index = mPlayList.indexOf(object);
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
        return mCursor;
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
     * Returns the queue
     * 
     * @return The queue as a long[]
     */
    public Object[] getQueue() {
        synchronized (this) {
            return mPlayList.toArray();
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
     * @param position
     *            The position to start playback at
     */
    public void open(final List<Object> list, final int position) {
        if (list == null) {
            return;
        }
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            final Object old = getCurrent();
            final int listlength = list.size();
            boolean newlist = true;
            if (mPlayListLen == listlength) {
                newlist = !mPlayList.containsAll(list);
            }
            if (newlist) {
                addToPlayList(list, -1);
                notifyChange(QUEUE_CHANGED);
            }
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mShuffler.nextInt(mPlayListLen);
            }
            mHistory.clear();
            openCurrentAndNext();
//            if (old != getCurrent()) {
//                notifyChange(META_CHANGED);
//            }
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
                final long duration = mPlayer.duration();
                if (mRepeatMode != REPEAT_CURRENT && duration > 2000
                        && mPlayer.position() >= duration - 2000) {
                    gotoNext(true);
                }
                mPlayPending = false;
                mPlayer.start();
                
                
                mPlayerHandler.removeMessages(FADEDOWN);
                mPlayerHandler.sendEmptyMessage(FADEUP);
                
                if (!mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = true;
                    notifyChange(PLAYSTATE_CHANGED);
                }
            }
        } else if (mShuffleMode == SHUFFLE_AUTO && mPlayListLen <= 0) {
            setShuffleMode(SHUFFLE_AUTO);
        } else if (mPlayList == null) { // first play
            if (proxy != null) {
                open(((StreamerProxy) proxy).getInternalPlaylist(), 0);
            }
        }
        else {
            setQueuePosition(mPlayPos);
        }
    }

    /**
     * Temporarily pauses playback.
     */
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
     */
    public void gotoNext(final boolean force) {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                return;
            }
            final int pos = getNextPosition(force);
            if (pos == mPlayPos) {
                return;
            }
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
            openCurrentAndNext();
            if (mPlayPending) {
                play();
            }
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Changes from the current track to the previous played track
     */
    public void prev() {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_NORMAL) {
                // Go to previously-played track and remove it from the history
                final int histsize = mHistory.size();
                if (histsize == 0) {
                    return;
                }
                final Integer pos = mHistory.remove(histsize - 1);
                mPlayPos = pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            }
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
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
     * Moves an item in the queue from one position to another
     * 
     * @param from
     *            The position the item is currently at
     * @param to
     *            The position the item is being moved to
     */
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            Collections.swap(mPlayList, index1, index2);
            if (index1 < index2) {

                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                    mPlayPos--;
                }
            } else if (index2 < index1) {
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
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
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    mPlayListLen = 0;
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                } else {
                    mShuffleMode = SHUFFLE_NONE;
                }
            }
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }
    
    public void setPlaylist(final List<Object> list) {
        stop();
        mPlayList = null;
        mAutoShuffleList = null;
        mPlayPos = 0;
        mPlayListLen = 0;
        if (list != null) {
            addToPlayList(list, Integer.MAX_VALUE);
            notifyChange(QUEUE_CHANGED);
        }
        
    }

    /**
     * Sets the position of a track in the queue
     * 
     * @param index
     *            The position to place the track
     */
    public void setQueuePosition(final int index) {
        synchronized (this) {
            stop(false);
            mPlayPos = index;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
            }
        }
    }

    /**
     * Queues a new list for playback
     * 
     * @param list
     *            The list to queue
     * @param action
     *            The action to take
     */
    public void enqueue(final List<Object> list, final int action) {
        synchronized (this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.size();
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                play();
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Cycles through the different repeat modes
     */
    private void cycleRepeat() {
        if (mRepeatMode == REPEAT_NONE) {
            setRepeatMode(REPEAT_ALL);
        } else if (mRepeatMode == REPEAT_ALL) {
            setRepeatMode(REPEAT_CURRENT);
            if (mShuffleMode != SHUFFLE_NONE) {
                setShuffleMode(SHUFFLE_NONE);
            }
        } else {
            setRepeatMode(REPEAT_NONE);
        }
    }

    /**
     * Cycles through the different shuffle modes
     */
    private void cycleShuffle() {
        if (mShuffleMode == SHUFFLE_NONE) {
            setShuffleMode(SHUFFLE_NORMAL);
            if (mRepeatMode == REPEAT_CURRENT) {
                setRepeatMode(REPEAT_ALL);
            }
        } else if (mShuffleMode == SHUFFLE_NORMAL
                || mShuffleMode == SHUFFLE_AUTO) {
            setShuffleMode(SHUFFLE_NONE);
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
                mAppWidgetSmall.performUpdate(AudioStreamerService.this, small);
            } else if (mAppWidgetLarge != null
                    && mAppWidgetLarge.getWidgetUpdateId().equals(command)) {
                final int[] large = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLarge.performUpdate(AudioStreamerService.this, large);
            } else if (mAppWidgetLargeAlternate != null
                    && mAppWidgetLargeAlternate.getWidgetUpdateId().equals(
                            command)) {
                final int[] largeAlt = intent
                        .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetLargeAlternate.performUpdate(
                        AudioStreamerService.this, largeAlt);
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

    private static final class DelayedHandler extends Handler {

        private final WeakReference<AudioStreamerService> mService;

        /**
         * Constructor of <code>DelayedHandler</code>
         * 
         * @param service
         *            The service to use.
         */
        public DelayedHandler(final AudioStreamerService service) {
            mService = new WeakReference<AudioStreamerService>(service);
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

        private final WeakReference<AudioStreamerService> mService;

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
        public MusicPlayerHandler(final AudioStreamerService service,
                final Looper looper) {
            super(looper);
            mService = new WeakReference<AudioStreamerService>(service);
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
                    mService.get().gotoNext(true);
                } else {
                    mService.get().openCurrentAndNext();
                }
                break;
            case TRACK_WENT_TO_NEXT:
                mService.get().mPlayPos = mService.get().mNextPlayPos;
                mService.get().closeCursor();
                if (mService.get().mPlayPos != -1) {
                    mService.get().mCursor = mService.get().mPlayList.get(mService
                            .get().mPlayPos);
                    mService.get().notifyChange(META_CHANGED);
                    mService.get().buildNotification();
                    mService.get().setNextTrack();
                    break;
                }
                //dont break if -1 (stop)
            case TRACK_ENDED:
                if (mService.get().mRepeatMode == REPEAT_CURRENT) {
                    mService.get().seek(0);
                    mService.get().play();
                } else {
                    mService.get().gotoNext(false);
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

    private static final class MultiPlayer implements
            MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
            MediaPlayer.OnPreparedListener, OnInfoListener,
            OnBufferingUpdateListener {
        
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

        private final WeakReference<AudioStreamerService> mService;

        private CompatMediaPlayer mCurrentMediaPlayer = new CompatMediaPlayer();

        private CompatMediaPlayer mNextMediaPlayer;

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
        public MultiPlayer(final AudioStreamerService service) {
            mService = new WeakReference<AudioStreamerService>(service);
            mCurrentMediaPlayer.setWakeMode(mService.get(),
                    PowerManager.PARTIAL_WAKE_LOCK);
        }

        /**
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @throws Exception 
         */
        public void setDataSource(final String path) throws Exception {
            mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, path,
                    mIsPreparing);
            if (mIsInitialized) {
                final String next = null;
                setNextDataSource(next);
            }
        }

        /**
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @throws Exception 
         */
        public void setDataSource(FileDescriptor fd, long offset, long length) throws Exception {
            mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, fd, offset,
                    length);
            if (mIsInitialized) {
                final String next = null;
                setNextDataSource(next);
            }
        }

        /**
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @throws Exception 
         */
        public void setDataSource(FileDescriptor fd) throws Exception {
            mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, fd);
            if (mIsInitialized) {
                final String next = null;
                setNextDataSource(next);
            }
        }

        /**
         * @param player
         *            The {@link MediaPlayer} to use
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @return True if the <code>player</code> has been prepared and is
         *         ready to play, false otherwise
         * @throws Exception 
         */
        private boolean setDataSourceImpl(final MediaPlayer player,
                final String path, boolean remote) throws Exception {
            try {
                player.reset();
                player.setOnCompletionListener(this);
                player.setOnErrorListener(this);
                player.setOnInfoListener(this);
                player.setOnBufferingUpdateListener(this);
                player.setOnPreparedListener(null);
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                if (path.startsWith("content://")) {
                    player.setDataSource(mService.get(), Uri.parse(path));
                } else {
                    player.setDataSource(path);
                }
                if (remote) {
                    player.setOnPreparedListener(this);
                    player.prepareAsync();
                    Log.w(TAG, "prepareAsync from playUrl");
                    if (player == mCurrentMediaPlayer) {
                        mService.get().setState(STATE_STARTING);
                        mService.get().mPlayPending = true;
                    }
                } else {
                    player.prepare();
                }
            } catch (final Exception e) {
                // TODO: notify the user why the file couldn't be opened
                throw e;
//                return false;
            }
            if (player == mCurrentMediaPlayer) {
                final Intent intent = new Intent(
                        AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                        getAudioSessionId());
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get()
                        .getPackageName());
                mService.get().sendBroadcast(intent);
            }
            return true;
        }

        /**
         * @param player
         *            The {@link MediaPlayer} to use
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @return True if the <code>player</code> has been prepared and is
         *         ready to play, false otherwise
         */
        private boolean setDataSourceImpl(final MediaPlayer player,
                FileDescriptor fd, long offset, long length) {
            try {
                player.reset();
                player.setOnPreparedListener(null);
                player.setDataSource(fd, offset, length);
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.prepare();
            } catch (final IOException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (final IllegalArgumentException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            }
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setOnInfoListener(this);
            player.setOnBufferingUpdateListener(this);
            final Intent intent = new Intent(
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                    getAudioSessionId());
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get()
                    .getPackageName());
            mService.get().sendBroadcast(intent);
            return true;
        }

        /**
         * @param player
         *            The {@link MediaPlayer} to use
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @return True if the <code>player</code> has been prepared and is
         *         ready to play, false otherwise
         */
        private boolean setDataSourceImpl(final MediaPlayer player,
                FileDescriptor fd) {
            try {
                player.reset();
                player.setOnPreparedListener(null);
                player.setDataSource(fd);
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.prepare();
            } catch (final IOException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (final IllegalArgumentException todo) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            }
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setOnInfoListener(this);
            player.setOnBufferingUpdateListener(this);
            final Intent intent = new Intent(
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                    getAudioSessionId());
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get()
                    .getPackageName());
            mService.get().sendBroadcast(intent);
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
                        url = TiConvert.toString(((HashMap) object).get(TiC.PROPERTY_URL));
                    } else  {
                        url =  TiConvert.toString(object);
                    }
                    boolean isAsset = URLUtil.isAssetUrl(url);
                    if (preparingNext) {
                        mNextIsPreparing = false;
                    } else {
                        mIsPreparing = false;
                    }
                    if (isAsset || url.startsWith("android.resource")) {
                        Context context = TiApplication.getInstance();
                        AssetFileDescriptor afd = null;
                        try {
                            if (isAsset) {
                                String path = url.substring(TiConvert.ASSET_URL
                                        .length());
                                afd = context.getAssets().openFd(path);
                            } else {
                                Uri uri = Uri.parse(url);
                                afd = context.getResources().openRawResourceFd(
                                        TiRHelper.getResource("raw."
                                                + uri.getLastPathSegment()));
                            }
                            // Why mp.setDataSource(afd) doesn't work is a
                            // problem for
                            // another day.
                            // http://groups.google.com/group/android-developers/browse_thread/thread/225c4c150be92416

                            if (preparingNext) {
                                mNextPlayingFile = new PlayingItem(url, afd);
                                setNextDataSource(afd.getFileDescriptor(),
                                        afd.getStartOffset(), afd.getLength());
                            } else {
                                mPlayingFile = new PlayingItem(url, afd);
                                setDataSource(afd.getFileDescriptor(),
                                        afd.getStartOffset(), afd.getLength());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error setting file descriptor: ", e);
                        } finally {
                            if (afd != null) {
                                afd.close();
                            }
                        }
                    } else {
                        Uri uri = Uri.parse(url);
                        if (uri.getScheme().equals(TiC.PROPERTY_FILE)) {
                            if (TiC.ICS_OR_GREATER) {
                                mPlayingFile = new PlayingItem(uri.getPath());
                                setDataSource(mPlayingFile.path);
                            } else {
                                // For 2.2 and below, MediaPlayer uses the
                                // native player
                                // which requires
                                // files to have worldreadable access,
                                // workaround is to
                                // open an input
                                // stream to the file and give that to the
                                // player.
                                FileInputStream fis = null;
                                try {
                                    fis = new FileInputStream(uri.getPath());
                                    mPlayingFile = new PlayingItem(uri.getPath(), fis.getFD());
                                    setDataSource(fis.getFD());
                                } catch (IOException e) {
                                    Log.e(TAG,
                                            "Error setting file descriptor: ",
                                            e);
                                } finally {
                                    if (fis != null) {
                                        fis.close();
                                    }
                                }
                            }
                        } else {
                            if (preparingNext) {
                                mNextIsPreparing = true;
                                mNextPlayingFile = new PlayingItem(url);
                                setNextDataSource(mNextPlayingFile.path);
                            } else {
                                mIsPreparing = true;
                                mPlayingFile = new PlayingItem(url);
                                setDataSource(mPlayingFile.path);
                            }

                        }
                    }

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
         * Set the MediaPlayer to start when this MediaPlayer finishes playback.
         * 
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         * @throws Exception 
         */
        public void setNextDataSource(final String path) throws Exception {
            mCurrentMediaPlayer.setNextMediaPlayer(null);
            if (mNextMediaPlayer != null) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
            if (path == null) {
                return;
            }
            mNextMediaPlayer = new CompatMediaPlayer();
            mNextMediaPlayer.setWakeMode(mService.get(),
                    PowerManager.PARTIAL_WAKE_LOCK);
            mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            if (!setDataSourceImpl(mNextMediaPlayer, path, mNextIsPreparing)) {
//                mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
//            } else {
                if (mNextMediaPlayer != null) {
                    mNextMediaPlayer.release();
                    mNextMediaPlayer = null;
                }
            }
        }

        /**
         * Set the MediaPlayer to start when this MediaPlayer finishes playback.
         * 
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         */
        public void setNextDataSource(FileDescriptor fd, long offset,
                long length) {
            mCurrentMediaPlayer.setNextMediaPlayer(null);
            if (mNextMediaPlayer != null) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
            if (fd == null) {
                return;
            }
            mNextMediaPlayer = new CompatMediaPlayer();
            mNextMediaPlayer.setWakeMode(mService.get(),
                    PowerManager.PARTIAL_WAKE_LOCK);
            mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            if (setDataSourceImpl(mNextMediaPlayer, fd, offset, length)) {
                mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
            } else {
                if (mNextMediaPlayer != null) {
                    mNextMediaPlayer.release();
                    mNextMediaPlayer = null;
                }
            }
        }

        /**
         * Set the MediaPlayer to start when this MediaPlayer finishes playback.
         * 
         * @param path
         *            The path of the file, or the http/rtsp URL of the stream
         *            you want to play
         */
        public void setNextDataSource(FileDescriptor fd) {
            mCurrentMediaPlayer.setNextMediaPlayer(null);
            if (mNextMediaPlayer != null) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
            if (fd == null) {
                return;
            }
            mNextMediaPlayer = new CompatMediaPlayer();
            mNextMediaPlayer.setWakeMode(mService.get(),
                    PowerManager.PARTIAL_WAKE_LOCK);
            mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            if (setDataSourceImpl(mNextMediaPlayer, fd)) {
                mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
            } else {
                if (mNextMediaPlayer != null) {
                    mNextMediaPlayer.release();
                    mNextMediaPlayer = null;
                }
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
            return mCurrentMediaPlayer.isPlaying();
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
            mCurrentMediaPlayer.start();
            mIsPaused = !mCurrentMediaPlayer.isPlaying();
            mService.get().setState(
                    !mIsPaused ? STATE_PLAYING : mService
                            .get().mState);
            startProgressTimer();
        }

        /**
         * Resets the MediaPlayer to its uninitialized state.
         */
        public void stop() {
            mCurrentMediaPlayer.reset();
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
            mCurrentMediaPlayer.release();
        }

        /**
         * Pauses playback. Call start() to resume.
         */
        public void pause() {
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
            return mCurrentMediaPlayer.getDuration();
        }

        /**
         * Gets the current playback position.
         * 
         * @return The current position in milliseconds
         */
        public long position() {
            return mCurrentMediaPlayer.getCurrentPosition();
        }

        /**
         * Sets the current playback position.
         * 
         * @param whereto
         *            The offset in milliseconds from the start to seek to
         * @return The offset in milliseconds from the start to seek to
         */
        public long seek(final long whereto) {
            mCurrentMediaPlayer.seekTo((int) whereto);
            return whereto;
        }

        /**
         * Sets the volume on this player.
         * 
         * @param vol
         *            Left and right volume scalar
         */
        public void setVolume(final float vol) {
            mCurrentMediaPlayer.setVolume(vol, vol);
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
        public void setAudioStreamType(final int streamtype) {
            mCurrentMediaPlayer.setAudioStreamType(streamtype);
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            String msg = "Unknown media issue.";

            switch (what) {
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                msg = "Stream not interleaved or interleaved improperly.";
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                msg = "Stream does not support seeking";
                break;
            case MediaPlayer.MEDIA_INFO_UNKNOWN:
                msg = "Unknown media issue";
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                msg = "Video is too complex for decoder, video lagging.";
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                mService.get().getMetadataFromUrl(mPlayingFile);
                break;
            }

            mService.get().onError(TiC.ERROR_CODE_UNKNOWN, msg);
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onError(final MediaPlayer mp, final int what,
                final int extra) {
            if (mp == mNextMediaPlayer) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
                return false;
            }
            
            int code = what;
            if (what == 0) {
                code = -1;
            }
            boolean needsStop = false;
            String msg = "Unknown media error.";
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                msg = "Media server died";
                needsStop = true;
            }
            if (extra == -1004) {
                code = 404;
                msg = "File can't be accessed";
                needsStop = true;
            }
            mService.get().onError(code, msg);
            if (needsStop) {
                mIsInitialized = false;
                mPlayingFile = null;
                mService.get().mWakeLock.acquire(30000);
                mHandler.sendEmptyMessage(TRACK_ENDED);
                mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
            }
            switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                
                mCurrentMediaPlayer.release();
                mCurrentMediaPlayer = new CompatMediaPlayer();
                mCurrentMediaPlayer.setWakeMode(mService.get(),
                        PowerManager.PARTIAL_WAKE_LOCK);
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(SERVER_DIED), 2000);
                break;
            default:
                break;
            }
            return needsStop;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCompletion(final MediaPlayer mp) {
            if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
                mCurrentMediaPlayer.release();
                mCurrentMediaPlayer = mNextMediaPlayer;
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

        @Override
        public void onPrepared(MediaPlayer mp) {
            mp.setOnPreparedListener(null);
            if (mp == mCurrentMediaPlayer) {
                mIsPreparing = false;
                mService.get().setState(STATE_INITIALIZED);
                mService.get().notifyChange(PLAYSTATE_CHANGED);
                if (mIsInitialized) {
                    mService.get().onStartPlaying(mPlayingFile);
                }
            } else if (mp == mNextMediaPlayer) {
                mNextIsPreparing = false;
                mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
            }

        }

        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            if (mp == mCurrentMediaPlayer) {
                mService.get().onBufferingUpdate(percent);
            }
        }
        
        private void startProgressTimer() {
            if (mProgressTimer == null) {
                mProgressTimer = new Timer(true);
            } else {
                mProgressTimer.cancel();
                mProgressTimer = new Timer(true);
            }
            if (isPlaying()) {
                mService.get().onProgress(position());
            }
            mProgressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (isPlaying()) {
                            mService.get().onProgress(position());
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
    }

    private static final class CompatMediaPlayer extends MediaPlayer implements
            OnCompletionListener {

        private boolean mCompatMode = true;

        private MediaPlayer mNextPlayer;

        private OnCompletionListener mCompletion;

        /**
         * Constructor of <code>CompatMediaPlayer</code>
         */
        public CompatMediaPlayer() {
            try {
                MediaPlayer.class.getMethod("setNextMediaPlayer",
                        MediaPlayer.class);
                mCompatMode = false;
            } catch (final NoSuchMethodException e) {
                mCompatMode = true;
                super.setOnCompletionListener(this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setNextMediaPlayer(final MediaPlayer next) {
            if (mCompatMode) {
                mNextPlayer = next;
            } else {
                super.setNextMediaPlayer(next);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setOnCompletionListener(final OnCompletionListener listener) {
            if (mCompatMode) {
                mCompletion = listener;
            } else {
                super.setOnCompletionListener(listener);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCompletion(final MediaPlayer mp) {
            if (mNextPlayer != null) {
                // SystemClock.sleep(25);
                mNextPlayer.start();
            }
            mCompletion.onCompletion(this);
        }
    }

    @SuppressWarnings("unused")
    private static final class AudioStreamerServiceStub {

        private final WeakReference<AudioStreamerService> mService;

        private AudioStreamerServiceStub(final AudioStreamerService service) {
            mService = new WeakReference<AudioStreamerService>(service);
        }

        public void openFile(final Object object) throws RemoteException {
            mService.get().openFile(object);
        }

        public void open(final List<Object> list, final int position)
                throws RemoteException {
            mService.get().open(list, position);
        }

        public void stop() throws RemoteException {
            mService.get().stop();
        }

        public void pause() throws RemoteException {
            mService.get().pause();
        }

        public void play() throws RemoteException {
            mService.get().play();
        }

        public void prev() throws RemoteException {
            mService.get().prev();
        }

        public void next() throws RemoteException {
            mService.get().gotoNext(true);
        }

        public void enqueue(final List<Object> list, final int action)
                throws RemoteException {
            mService.get().enqueue(list, action);
        }

        public void setQueuePosition(final int index) throws RemoteException {
            mService.get().setQueuePosition(index);
        }

        public void setShuffleMode(final int shufflemode)
                throws RemoteException {
            mService.get().setShuffleMode(shufflemode);
        }

        public void setRepeatMode(final int repeatmode) throws RemoteException {
            mService.get().setRepeatMode(repeatmode);
        }

        public void moveQueueItem(final int from, final int to)
                throws RemoteException {
            mService.get().moveQueueItem(from, to);
        }

        public void refresh() throws RemoteException {
            mService.get().refresh();
        }

        public boolean isPlaying() throws RemoteException {
            return mService.get().isPlaying();
        }

        public Object[] getQueue() throws RemoteException {
            return mService.get().getQueue();
        }

        public long duration() throws RemoteException {
            return mService.get().duration();
        }

        public long position() throws RemoteException {
            return mService.get().position();
        }

        public long seek(final long position) throws RemoteException {
            return mService.get().seek(position);
        }

        public Object getCurrent() throws RemoteException {
            return mService.get().getCurrent();
        }

        public int getQueuePosition() throws RemoteException {
            return mService.get().getQueuePosition();
        }

        public int getShuffleMode() throws RemoteException {
            return mService.get().getShuffleMode();
        }

        public int getRepeatMode() throws RemoteException {
            return mService.get().getRepeatMode();
        }

        public int removeTracks(final int first, final int last)
                throws RemoteException {
            return mService.get().removeTracks(first, last);
        }

        public int removeTrack(final long id) throws RemoteException {
            return mService.get().removeTrack(id);
        }

        public int getMediaMountedCount() throws RemoteException {
            return mService.get().getMediaMountedCount();
        }

        public int getAudioSessionId() throws RemoteException {
            return mService.get().getAudioSessionId();
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

    protected void onProgress(long position) {
//        if (mRemoteControlClientCompat != null) {
//            mRemoteControlClientCompat.setPlaybackState(mRemoteControlClientState, position, 1.0f);
//        }
        if (proxyHasListeners(EVENT_PROGRESS, false)) {
            KrollDict event = dictForEvent();
            event.put("progress", position);
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
    private void updateMetadata(final HashMap<String, Object> dict) {
        if (mRemoteControlClientCompat == null || updatingMetadata)
            return;
        updatingMetadata = true;
        // for now we DONT call with "true". There is a recycle on the bitmap
        // which is bad news for when the bitmap is use somewhere else.
        // instead we update all datas :s
        currentMetadataEditor = mRemoteControlClientCompat.editMetadata(true);
        currentMetadataEditor
                .putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, null);
        if (dict != null) {
            for (Map.Entry<String, Object> entry : dict.entrySet()) {
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
            if (!dict.containsKey("duration") && mPlayer != null
                    && mPlayer.isPlaying()) {
                currentMetadataEditor.putLong(
                        MediaMetadataRetriever.METADATA_KEY_DURATION,
                        mPlayer.duration());
            }
            TiDrawableReference imageref = TiDrawableReference.fromObject(proxy,
                    dict.get("artwork"));
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
    
    public void getMetadataFromUrl(final MultiPlayer.PlayingItem playingItem) {
//        (new AsyncTask() {
//            @Override
//            protected Object doInBackground(Object[] objects) {
//                if (playingItem.fileDescriptor != null) {
//                    mMetadaReceiver.setDataSource(playingItem.fileDescriptor);
//                } else {
//                    mMetadaReceiver.setDataSource(playingItem.path);
//                }
//                try {
//                    for (int i = 0; i < METADATA_KEYS.length; i++) {
//                        String key = METADATA_KEYS[i];
//                        String value = mMetadaReceiver.extractMetadata(key);
//                    
//                        if (value != null) {
//                            Log.i(TAG, "Key: " + key + " Value: " + value);
//                        }
//                    }
//                    final byte[] art = mMetadaReceiver.getEmbeddedPicture();
//                    return BitmapFactory.decodeByteArray(art, 0, art.length);
//                 } catch (Exception e) {
//                 }
//                return null;
//            }
//        }).execute();
        
        
    }

    public void updateMetadata() {
        HashMap<String, Object> metadata = null;
        if (proxy != null && proxy.hasProperty(TiC.PROPERTY_METADATA)) {
            metadata = (HashMap<String, Object>) proxy
                    .getProperty(TiC.PROPERTY_METADATA);
        }
        else if (mCursor instanceof HashMap) {
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
        
        String stateDescription = "";
        int remoteState = mRemoteControlClientState;
        switch (state) {
        case STATE_BUFFERING:
            stateDescription = STATE_BUFFERING_DESC;
            break;
        case STATE_INITIALIZED:
            stateDescription = STATE_INITIALIZED_DESC;
            break;
        case STATE_PAUSED:
            remoteState = RemoteControlClient.PLAYSTATE_PAUSED;
            stateDescription = STATE_PAUSED_DESC;
            break;
        case STATE_PLAYING:
            remoteState = RemoteControlClient.PLAYSTATE_PLAYING;
            stateDescription = STATE_PLAYING_DESC;
            break;
        case STATE_STARTING:
            stateDescription = STATE_STARTING_DESC;
            break;
        case STATE_STOPPED:
            remoteState = RemoteControlClient.PLAYSTATE_STOPPED;
            stateDescription = STATE_STOPPED_DESC;
            break;
        case STATE_STOPPING:
            remoteState = RemoteControlClient.PLAYSTATE_STOPPED;
            stateDescription = STATE_STOPPING_DESC;
            break;
        case STATE_WAITING_FOR_DATA:
            stateDescription = STATE_WAITING_FOR_DATA_DESC;
            break;
        case STATE_WAITING_FOR_QUEUE:
            stateDescription = STATE_WAITING_FOR_QUEUE_DESC;
            break;
        }
        if (mRemoteControlClientCompat != null && mRemoteControlClientState != remoteState) {
            mRemoteControlClientState = remoteState;
            mRemoteControlClientCompat.setPlaybackState(mRemoteControlClientState);
        }
        if (proxy != null) {
            proxy.setProperty("state", state);
//            proxy.setProperty("stateDescription", stateDescription);
        }
        Log.d(TAG, "Audio state changed: " + stateDescription, Log.DEBUG_MODE);

        if (proxyHasListeners(EVENT_STATE, false)) {
            KrollDict data = new KrollDict();
            data.put("state", state);
            data.put("description", stateDescription);
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
        if (proxy != null && TiConvert.toBoolean(proxy.getProperty("notifyOnPause"), true)) {
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
        
        notifyChange(META_CHANGED);
        // Update the notification
        mBuildNotification = mIsSupposedToBePlaying;
        buildNotification();
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
        if (mAudioFocus != AudioFocus.Focused && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)) {
            mAudioFocus = AudioFocus.Focused;
            RemoteControlHelper
            .registerRemoteControlClient(mAudioManager, mRemoteControlClientCompat);
            streamerGetsFocus(this);
        }
        aquireWifiLock();
        
    }

    void giveUpAudioFocus() {
        if (mAudioFocus == AudioFocus.Focused && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.abandonAudioFocus(mAudioFocusListener)) {
            // Remove the audio focus listener and lock screen controls
            mAudioFocus = AudioFocus.NoFocusNoDuck;
            RemoteControlHelper
                    .unregisterRemoteControlClient(mAudioManager, mRemoteControlClientCompat);
            streamerAbandonsFocus(this);
        }
        releaseWifiLock();
            
    }
    private KrollDict dictForEvent() {
        KrollDict data = new KrollDict();
        data.put("track", mCursor);
        data.put(TiC.PROPERTY_DURATION, duration());
        data.put(TiC.PROPERTY_INDEX, mPlayPos);
        return data;
    }
}
