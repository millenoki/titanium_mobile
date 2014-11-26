package com.akylas.titanium.ks;

import ti.modules.titanium.media.TiSound;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;

public class TiMediaButtonEventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        TiSound sound = TiSound.focussedSound();
        if (intent.getAction() == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            if (sound != null) {
                sound.pause();
            }
        } else if (intent.getAction() == Intent.ACTION_MEDIA_BUTTON) {
            // The event will fire twice, up and down.
            // we only want to handle the down event though.
            KeyEvent key = (KeyEvent) intent
                    .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (key.getAction() != KeyEvent.ACTION_DOWN)
                return;
            if (sound != null) {
                sound.onMediaKey(key);
            }
        }
    }
}
