package org.appcelerator.titanium.proxy;

import java.util.ArrayList;
import java.util.HashMap;
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
   public void handleCreationDict(HashMap dict) {
        super.handleCreationDict(dict);
        if (modelListener != null) {
            internalApplyModelProperties(dict);
        }
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
    public void processApplyProperties(HashMap d) {
        aboutToProcessProperties(d);
        handleProperties(d, true);
        didProcessProperties();
    }
    
    @Override
    public void processProperties(HashMap d)
    {
        aboutToProcessProperties(d);
        handleProperties(d, false);
        didProcessProperties();

    }
    
    protected void aboutToProcessProperties(HashMap d) {
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
        handleProperties(changes, true);
        didProcessProperties();
    }
    
    protected ArrayList<String> keySequence() {
        return null;
    }
    
    protected void handleProperties(HashMap<String, Object> d, final boolean changed) {
        final HashMap currents = changed?getShallowProperties():null;
        if (keySequence() != null) {
            for (final String key : keySequence()) {
                if (d.containsKey(key)) {
                    propertySet(key, d.get(key), changed?currents.get(key):null,
                            changed);
                    d.remove(key);
                }
            }
        }
        for (Map.Entry entry : d.entrySet()) {
            final Object key = entry.getKey();
            if (key instanceof String) {
                propertySet((String) key, entry.getValue(), changed?currents.get(key):null, changed);
            }
        }
    }
    
    protected void handleProperties(List<KrollPropertyChange> changes, final boolean changed) {
        KrollDict d = new KrollDict();
        for (KrollPropertyChange change : changes) {
            d.put(change.getName(), change.getNewValue());
        }
        handleProperties(d, changed);
    }
}
