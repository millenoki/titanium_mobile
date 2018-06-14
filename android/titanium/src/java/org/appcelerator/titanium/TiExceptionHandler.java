/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollExceptionHandler;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.CurrentActivityListener;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.os.Process;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

/**
 * A utility class for creating a dialog that displays Javascript errors
 */
@SuppressLint("NewApi")
public class TiExceptionHandler implements Handler.Callback, KrollExceptionHandler
{
	private static final String TAG = "TiExceptionHandler";
	private static final int MSG_OPEN_ERROR_DIALOG = 10011;
	private static LinkedList<ExceptionMessage> errorMessages = new LinkedList<ExceptionMessage>();
	private static boolean dialogShowing = false;
	private static Handler mainHandler;
	private static int layoutId = -1;
    private static int styleId;
    private static int layoutLocationId;
	private static int layoutLocationTitleId;
	private static int layoutMessageId;
	private static int layoutMessageTitleId;
	private static int layoutSourceId;
	private static int layoutSourceTitleId;
	private static int layoutCallstackId;
	private static int layoutCallstackTitleId;

	private static final String ERROR_TITLE = "title";
	private static final String ERROR_MESSAGE = "message";
	private static final String ERROR_SOURCENAME = "sourceName";
	private static final String ERROR_LINE = "lineNumber";
	private static final String ERROR_LINESOURCE = "lineSource";
	private static final String ERROR_LINEOFFSET = "columnNumber";
	private static final String ERROR_JS_STACK = "stack";
	private static final String ERROR_JAVA_STACK = "javaStack";

	private static final String fill(int count)
	{
		char[] string = new char[count];
		Arrays.fill(string, ' ');
		return new String(string);
	}

	public static final KrollDict getErrorDict(ExceptionMessage error)
	{
		final KrollDict dict = new KrollDict();
		dict.put(ERROR_TITLE, error.title);
		dict.put(ERROR_MESSAGE, error.message);
		dict.put(ERROR_SOURCENAME, error.sourceName);
		dict.put(ERROR_LINE, error.line);
		dict.put(ERROR_LINESOURCE, error.lineSource);
		dict.put(ERROR_LINEOFFSET, error.lineOffset);
        if (error.jsStack != null) {
            String[] callstackArray = error.jsStack.split("\n");
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (i = 1; i < callstackArray.length; i++) {
                String value = callstackArray[i];
                if (value.indexOf("at Module._runScript") != -1) {
                    break;
                }
                if (i != 1) {
                    sb.append("\n");
                }
                sb.append(value);
            }
            error.jsStack = sb.toString();
        }
		dict.put(ERROR_JS_STACK, error.jsStack);
		dict.put(ERROR_JAVA_STACK, error.javaStack);
		return dict;
	}

	public static String getError(KrollDict error)
	{
		String output = new String();

		final String sourceName = error.getString(ERROR_SOURCENAME);
		final int line = error.getInt(ERROR_LINE);
		final String lineSource = error.getString(ERROR_LINESOURCE);
		final int lineOffset = error.getInt(ERROR_LINEOFFSET);
		final String jsStack = error.getString(ERROR_JS_STACK);
		final String javaStack = error.getString(ERROR_JAVA_STACK);
		final String message = error.getString(ERROR_MESSAGE);

		if (sourceName != null) {
			output += sourceName + ":" + line + "\n";
		}
		if (lineSource != null) {
			output += lineSource + "\n";
			output += fill(lineOffset - 1) + "^\n";
		}
		// sometimes the stacktrace can include the error
		// don't re-print the error if that is the case
		if (!jsStack.contains("Error:")) {
			output += message + "\n";
		}
		if (jsStack != null) {
			output += jsStack + "\n";
		}
		if (javaStack != null) {
			output += javaStack;
		}

		return output;
	}

	public TiExceptionHandler()
	{
		mainHandler = new Handler(TiMessenger.getMainMessenger().getLooper(), this);
	}

	public void openErrorDialog(ExceptionMessage error)
	{
		if (TiApplication.isUIThread()) {
			handleOpenErrorDialog(error);
		} else {
			TiMessenger.sendBlockingMainMessage(mainHandler.obtainMessage(MSG_OPEN_ERROR_DIALOG), error);
		}
	}

	protected static void handleOpenErrorDialog(final ExceptionMessage error)
	{
		final TiApplication tiApp = TiApplication.getInstance();
		if (tiApp == null) {
			return;
		}

		final Activity activity = tiApp.getRootOrCurrentActivity();
		if (activity == null || activity.isFinishing()) {
			return;
		}

		final KrollDict dict = getErrorDict(error);
		tiApp.fireAppEvent("uncaughtException", dict);
		Log.e(TAG, getError(dict));

		if (tiApp.getDeployType().equals(TiApplication.DEPLOY_TYPE_PRODUCTION)) {
			return;
		}

		if (!dialogShowing) {
			dialogShowing = true;
			tiApp.waitForCurrentActivity(new CurrentActivityListener() {
				@Override
				public void onCurrentActivityReady(Activity activity)
				{
					createDialog(error);
				}
			});
		} else {
			errorMessages.add(error);
		}
	}

	protected static void createDialog(final ExceptionMessage error)
	{
		TiApplication application = TiApplication.getInstance();
		if (layoutId == -1) {
            try {
                layoutId = TiRHelper.getApplicationResource("layout.ti_exception_dialog");
                styleId = TiRHelper.getApplicationResource("style.TiExceptionDialogStyle");
                layoutLocationId = TiRHelper.getApplicationResource("id.tiexd_location");
                layoutLocationTitleId = TiRHelper.getApplicationResource("id.tiexd_location_title");
                layoutSourceId = TiRHelper.getApplicationResource("id.tiexd_source");
                layoutSourceTitleId = TiRHelper.getApplicationResource("id.tiexd_source_title");
                layoutMessageId = TiRHelper.getApplicationResource("id.tiexd_message");
                layoutMessageTitleId = TiRHelper.getApplicationResource("id.tiexd_message_title");
                layoutCallstackId = TiRHelper.getApplicationResource("id.tiexd_callstack");
                layoutCallstackTitleId = TiRHelper.getApplicationResource("id.tiexd_callstack_title");
            } catch (ResourceNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }
		if (application == null || layoutId <= 0) {
			return;
		}
		
		Activity activity = application.getCurrentActivity();

		ViewGroup layout = (ViewGroup) application.getCurrentActivity().getLayoutInflater().inflate(layoutId, null);
		
		if (layout == null) {
		    return;
            
		}
		String location = error.sourceName;
		if (error.line != -1) {
		    if (error.lineOffset != -1) {
		        location = "[" + error.line + "," + error.lineOffset + "] " + location;
		    } else {
		        location = "[" + error.line + "] " + location;
		    }
		}
		if (location != null) {
		      ((TextView) layout.findViewById(layoutLocationId)).setText(location);
		} else {
		    layout.findViewById(layoutLocationId).setVisibility(View.GONE);
		    layout.findViewById(layoutLocationTitleId).setVisibility(View.GONE);
		}
        if (error.message != null) {
            ((TextView) layout.findViewById(layoutMessageId)).setText(error.message);
        } else {
            layout.findViewById(layoutMessageId).setVisibility(View.GONE);
            layout.findViewById(layoutMessageTitleId).setVisibility(View.GONE);
        }
        if (error.lineSource != null) {
            ((TextView) layout.findViewById(layoutSourceId)).setText(error.lineSource.trim());
        } else {
            layout.findViewById(layoutSourceId).setVisibility(View.GONE);
            layout.findViewById(layoutSourceTitleId).setVisibility(View.GONE);
        }
        if (error.jsStack != null || error.javaStack != null) {

            if (error.javaStack != null) {
                ((TextView) layout.findViewById(layoutCallstackId)).append(error.javaStack);
            }
            if (error.jsStack != null) {
                ((TextView) layout.findViewById(layoutCallstackId)).append(error.jsStack);
            }
        } else {
            layout.findViewById(layoutCallstackId).setVisibility(View.GONE);
            layout.findViewById(layoutCallstackTitleId).setVisibility(View.GONE);
        }
		OnClickListener clickListener = new OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				if (which == DialogInterface.BUTTON_POSITIVE) {
					Process.killProcess(Process.myPid());
				}
			}
		};

		final AlertDialog.Builder builder = new AlertDialog.Builder(activity, styleId)
												.setTitle(error.title)
												.setView(layout)
												.setPositiveButton("Kill", clickListener)
                                                .setOnDismissListener(new OnDismissListener() {
                                                    @Override
                                                    public void onDismiss(DialogInterface dialog) {
                                                        if (!errorMessages.isEmpty()) {
                                                            handleOpenErrorDialog(errorMessages.removeFirst());
                                                        } else {
                                                            dialogShowing = false;
                                                        }
                                                    }
                                                })
												.setCancelable(false);
        if (error.canContinue) {
            builder.setNeutralButton("Continue", clickListener);
        }
		final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
		dialog.show();
        dialogShowing = true;

//		final Window window = activity.getWindow();
//		Rect displayRect = new Rect();
//		window.getDecorView().getWindowVisibleDisplayFrame(displayRect);
//		dialog.getWindow().setLayout(displayRect.width(), (int) (displayRect.height() * 0.95));
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_OPEN_ERROR_DIALOG:
				AsyncResult asyncResult = (AsyncResult) msg.obj;
				ExceptionMessage errorMessage = (ExceptionMessage) asyncResult.getArg();
				handleOpenErrorDialog(errorMessage);
				asyncResult.setResult(null);
				return true;
			default:
				break;
		}

		return false;
	}

	/**
	 * Handles the exception by opening an error dialog with an error message
	 * @param error An error message containing line number, error title, message, etc
	 * @module.api
	 */
	public void handleException(ExceptionMessage error)
	{
		openErrorDialog(error);
	}
	public void handleException(HashMap map)
    {
	    ExceptionMessage message = new ExceptionMessage(
                TiConvert.toString(map, "name"),
                TiConvert.toString(map, ERROR_MESSAGE),
                TiConvert.toString(map, ERROR_SOURCENAME),
                TiConvert.toInt(map, ERROR_LINE, -1),
                TiConvert.toString(map, ERROR_LINESOURCE),
                TiConvert.toInt(map, ERROR_LINEOFFSET, -1),
                TiConvert.toString(map, ERROR_JS_STACK),
                TiConvert.toString(map, ERROR_JAVA_STACK));
	    message.canContinue = TiConvert.toBoolean(map, "canContinue", true);
	    handleException(message);
    }
    
	public boolean isShowing() {
	    return dialogShowing;
	}
}
