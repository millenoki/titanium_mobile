package org.appcelerator.titanium.proxy;

import java.util.List;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollPropertyChange;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.view.KrollProxyReusableListener;

@Kroll.proxy
public class AnimatableReusableProxy extends AnimatableProxy implements KrollProxyReusableListener {
    
    public AnimatableReusableProxy() {
        super();
        setModelListener(this);
    }

    @Override
    public void setReusing(boolean reusing) {
    }

    @Override
    public void listenerAdded(String arg0, int arg1, KrollProxy arg2) {
    }

    @Override
    public void listenerRemoved(String arg0, int arg1, KrollProxy arg2) {
    }

    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
    }
    @Override
    public void processApplyProperties(KrollDict d) {
        aboutToProcessProperties(d);
        for (Map.Entry<String, Object> entry : d.entrySet()) {
            propertySet(entry.getKey(), entry.getValue(), null, true);
        }
        didProcessProperties();
    }
    
    @Override
    public void processProperties(KrollDict d)
    {
        aboutToProcessProperties(d);
        for (Map.Entry<String, Object> entry : d.entrySet()) {
            propertySet(entry.getKey(), entry.getValue(), null, false);
        }
        didProcessProperties();

    }
    
    protected void aboutToProcessProperties(KrollDict d) {
    }
    protected void didProcessProperties() {
    }
    
    @Override
    public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
    {
        propertySet(key, newValue, oldValue, true);
        didProcessProperties();
    }

    @Override
    public void propertiesChanged(List<KrollPropertyChange> changes, KrollProxy proxy)
    {
        for (KrollPropertyChange change : changes) {
            propertySet(change.getName(), change.getNewValue(), change.getOldValue(), true);
        }
        didProcessProperties();
    }
}
