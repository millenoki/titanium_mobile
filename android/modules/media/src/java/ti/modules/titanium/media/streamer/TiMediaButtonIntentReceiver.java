package ti.modules.titanium.media.streamer;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiActivityHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;


/**
 * Used to control headset playback. Single press: pause/resume. Double press:
 * next track Long press: voice search.
 */
public class TiMediaButtonIntentReceiver extends BroadcastReceiver {

    private static final int MSG_LONGPRESS_TIMEOUT = 1;

    private static final int LONG_PRESS_DELAY = 1000;

    private static final int DOUBLE_CLICK = 800;

    private static long mLastClickTime = 0;

    private static boolean mDown = false;

    private static boolean mLaunched = false;

    private static Handler mHandler = new Handler() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (!mLaunched) {
                        final Context context = TiApplication.getAppContext();
                        Intent intent = new Intent().setClassName(context, TiActivityHelper.getMainActivityName());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // You need this if starting
                        //  the activity from a service
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        context.startActivity(intent);
                        
                        mLaunched = true;
                    }
                    break;
            }
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String intentAction = intent.getAction();
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            AudioStreamerService currentStreamer = AudioStreamerService.focusedStreamer();
            if (currentStreamer != null) {
                final Intent i = new Intent(context, currentStreamer.getClass());
                i.setAction(AudioStreamerService.SERVICECMD);
                i.putExtra(AudioStreamerService.CMDNAME, AudioStreamerService.CMDPAUSE);
                context.startService(i);
            }
            
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            AudioStreamerService currentStreamer = AudioStreamerService.focusedStreamer();
            final KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null || currentStreamer == null) {
                return;
            }

            final int keycode = event.getKeyCode();
            final int action = event.getAction();
            final long eventtime = event.getEventTime();

            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = AudioStreamerService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = AudioStreamerService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = AudioStreamerService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = AudioStreamerService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = AudioStreamerService.CMDPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = AudioStreamerService.CMDPLAY;
                    break;
            }
            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
                        if ((AudioStreamerService.CMDTOGGLEPAUSE.equals(command) || AudioStreamerService.CMDPLAY
                                .equals(command))
                                && mLastClickTime != 0
                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT,
                                    context));
                        }
                    } else if (event.getRepeatCount() == 0) {
                        // Only consider the first event in a sequence, not the
                        // repeat events,
                        // so that we don't trigger in cases where the first
                        // event went to
                        // a different app (e.g. when the user ends a phone call
                        // by
                        // long pressing the headset button)

                        // The service may or may not be running, but we need to
                        // send it
                        // a command.
                        final Intent i = new Intent(context, currentStreamer.getClass());
                        i.setAction(AudioStreamerService.SERVICECMD);
                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK
                                && eventtime - mLastClickTime < DOUBLE_CLICK) {
                            i.putExtra(AudioStreamerService.CMDNAME, AudioStreamerService.CMDNEXT);
                            context.startService(i);
                            mLastClickTime = 0;
                        } else {
                            i.putExtra(AudioStreamerService.CMDNAME, command);
                            context.startService(i);
                            mLastClickTime = eventtime;
                        }
                        mLaunched = false;
                        mDown = true;
                    }
                } else {
                    mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
            }
        }
    }
}
