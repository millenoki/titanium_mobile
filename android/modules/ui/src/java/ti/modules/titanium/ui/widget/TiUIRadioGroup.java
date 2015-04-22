package ti.modules.titanium.ui.widget;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import android.content.Context;
import android.support.v7.widget.AppCompatRadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

public class TiUIRadioGroup extends TiUIView implements OnCheckedChangeListener {
    RadioGroup radioGroup;
    private boolean ignoreChangeEvent = false;

    public TiUIRadioGroup(TiViewProxy proxy) {
        super(proxy);
        radioGroup = new RadioGroup(proxy.getActivity());
        radioGroup.setOnCheckedChangeListener(this);
    }
    
    
    private AppCompatRadioButton createButton(final Context context, Object value) {
        final AppCompatRadioButton result = new AppCompatRadioButton(context);
        if (value instanceof HashMap) {
            HashMap options = (HashMap) value;
            result.setText(TiConvert.toString(options, TiC.PROPERTY_TITLE));
        } else {
            result.setText(TiConvert.toString(value));
        }
        return result;
    }
    
    private void processButtons(final Context context, Object value) {
        if (!(value instanceof Object[])) {
            return;
        }
        radioGroup.removeAllViews();
        Object[] array = (Object[]) value;
        for (int i = 0; i < array.length; i++) {
            AppCompatRadioButton button = createButton(context, array[i]);
            if (button != null) {
                button.setId(i);
                radioGroup.addView(button);
            }
        }
    }

    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {

        switch (key) {
        case TiC.PROPERTY_LAYOUT: {
            switch (TiConvert.toString(newValue)) {
            case TiC.LAYOUT_HORIZONTAL:
                radioGroup.setOrientation(RadioGroup.HORIZONTAL);
                break;
            case TiC.LAYOUT_VERTICAL:
            default:
                radioGroup.setOrientation(RadioGroup.VERTICAL);
                break;
            }
            break;
        }
        case TiC.PROPERTY_VALUE:
            ignoreChangeEvent = !changedProperty;
            check(TiConvert.toInt(newValue, -1));
            break;
        case TiC.PROPERTY_BUTTONS:
            processButtons(proxy.getActivity(), newValue);
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (!ignoreChangeEvent) {
            proxy.setProperty(TiC.PROPERTY_VALUE, checkedId);
            if (hasListeners(TiC.EVENT_CHANGE)) {
                KrollDict data = new KrollDict();
                data.put(TiC.PROPERTY_VALUE, checkedId);
                fireEvent(TiC.EVENT_CHANGE, data, false, false);
            }
        }
        ignoreChangeEvent = false;

    }
    
    public void check(int id) {
        radioGroup.check(id);
    }
}
