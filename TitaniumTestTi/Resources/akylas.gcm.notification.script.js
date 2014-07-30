Ti.API.info('script test2');
var value = Ti.App.Properties.getString('ti.android.gcm.notification.last');
Ti.API.info('value: ' + JSON.stringify(value));
var last = JSON.parse(value);
Ti.API.info('script gcm: ' + JSON.stringify(last));

var intent = Ti.Android.createIntent({
    className: Ti.Android.appActivityClassName,
    action: Ti.Android.ACTION_MAIN
});
intent.addCategory(Ti.Android.CATEGORY_LAUNCHER);

// Create a PendingIntent to tie together the Activity and Intent
var pending = Ti.Android.createPendingIntent({
    intent: intent,
    flags: Ti.Android.FLAG_UPDATE_CURRENT
});

// Create the notification
var notification = Ti.Android.createNotification({
    flags: Ti.Android.FLAG_SHOW_LIGHTS | Ti.Android.FLAG_AUTO_CANCEL,
    contentTitle: 'notif_title',
    tickerText: 'notif_title',
    contentText: 'notif_desc',
    contentIntent: pending,
    ledOnMS: 3000,
    ledARGB: 0xFFff0000
});
// Send the notification.
Ti.Android.NotificationManager.notify(1234, notification);