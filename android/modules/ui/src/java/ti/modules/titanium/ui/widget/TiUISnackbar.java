package ti.modules.titanium.ui.widget;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.view.TiUIView;

import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.TextView;

public class TiUISnackbar extends TiUIView {
    private static final String TAG = "TiUINotifier";

    private Snackbar snackbar;

    public TiUISnackbar(TiViewProxy proxy) {
        super(proxy);
        Log.d(TAG, "Creating a notifier", Log.DEBUG_MODE);
        
        View theView = null;
        if (proxy.getProperty("view") instanceof TiViewProxy) {
            theView = ((TiViewProxy) proxy.getProperty("view")).getOuterView();
        } else {
            theView = ((TiBaseActivity) proxy.getActivity()).getLayout();
        }
        snackbar = Snackbar.make(theView, "", Snackbar.LENGTH_SHORT);
        useCustomLayoutParams = true;
//        this.nativeView = snackbar.getView();
        setNativeView(snackbar.getView());
    }

    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_MESSAGE:
        case TiC.PROPERTY_TEXT:
        case TiC.PROPERTY_TITLE:
            snackbar.setText(TiConvert.toString(newValue));
            break;

        case TiC.PROPERTY_DURATION:
            snackbar.setDuration(TiConvert.toInt(newValue));
            break;
        case TiC.PROPERTY_COLOR:
            snackbar.setActionTextColor(TiConvert.toColor(newValue));
            break;
        case "actionColor":
            View sbView = snackbar.getView();
            try {
                TextView textView = (TextView) sbView.findViewById(TiRHelper.getResource("android.support.design.R$",
                        "id.snackbar_text"));
                textView.setTextColor(TiConvert.toColor(newValue));
            } catch (ResourceNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            break;
        case "action":
            final String action = TiConvert.toString(newValue);
            snackbar.setAction(action, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    HashMap data = new HashMap();
                    data.put("action", action);
                    proxy.fireEvent("action", data, false, true);
                }
            });
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

    public void show(KrollDict options) {
//        Snackbar.make(((TiBaseActivity) proxy.getActivity()).getLayout(), "test", Snackbar.LENGTH_SHORT).show();
        snackbar.show();
    }

    public void hide(KrollDict options) {
        snackbar.dismiss();
    }
}
