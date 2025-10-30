package com.nidoham.ytpremium.player;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.media3.common.Player;

import com.nidoham.ytpremium.PlayerActivity;

public class PlayerActionReceiver extends BroadcastReceiver {
    public static final String ACTION_PREV = "com.nidoham.ytpremium.action.PREV";
    public static final String ACTION_PLAY_PAUSE = "com.nidoham.ytpremium.action.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.nidoham.ytpremium.action.NEXT";
    public static final String ACTION_QUALITY = "com.nidoham.ytpremium.action.QUALITY";
    public static final String ACTION_SEEK_BACK = "com.nidoham.ytpremium.action.SEEK_BACK";
    public static final String ACTION_SEEK_FORWARD = "com.nidoham.ytpremium.action.SEEK_FORWARD";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Intent serviceIntent = new Intent(context, PlayerService.class);
        context.bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                PlayerService.LocalBinder localBinder = (PlayerService.LocalBinder) binder;
                PlayerService service = localBinder.getService();

                switch (action) {
                    case ACTION_PREV:
                        service.playPrevious();
                        break;
                    case ACTION_PLAY_PAUSE:
                        Player player = service.getPlayer();
                        if (player != null) {
                            player.setPlayWhenReady(!player.getPlayWhenReady());
                            service.updateNotification(); // Refresh play/pause icon
                        }
                        break;
                    case ACTION_NEXT:
                        service.playNext();
                        break;
                    case ACTION_SEEK_BACK:
                        service.seekBack();
                        break;
                    case ACTION_SEEK_FORWARD:
                        service.seekForward();
                        break;
                    case ACTION_QUALITY:
                        Intent launchIntent = new Intent(context, PlayerActivity.class);
                        launchIntent.setAction(PlayerActivity.ACTION_SHOW_QUALITY_DIALOG);
                        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(launchIntent);
                        break;
                }
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Ignore
            }
        }, Context.BIND_AUTO_CREATE);
    }
}