package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import ti.modules.titanium.ui.widget.TiUISnackbar;

@Kroll.proxy(creatableInModule=UIModule.class)
public class SnackbarProxy extends ViewProxy
{
    public SnackbarProxy()
    {
        super();
    }

    @Override
    public TiUIView createView(Activity activity)
    {
        return new TiUISnackbar(this);
    }

    @Override
    protected void handleShow(KrollDict options) {
        super.handleShow(options);

        TiUISnackbar n = (TiUISnackbar) getOrCreateView();
        if (n != null) {
          n.show(options);
        }
    }

    @Override
    public String getApiName()
    {
        return "Ti.UI.Snackbar";
    }
}
