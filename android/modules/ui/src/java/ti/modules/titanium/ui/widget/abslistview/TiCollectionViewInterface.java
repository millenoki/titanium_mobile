package ti.modules.titanium.ui.widget.abslistview;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;

import android.view.View;

public interface TiCollectionViewInterface {
    public KrollProxy getChildByBindId(int sectionIndex, int itemIndex, String bindId);
    public int findItemPosition(int sectionIndex, int sectionItemIndex);

    public View getCellAt(int sectionIndex, int itemIndex);
    public TiAbsListViewTemplate getTemplate(String template, final boolean canReturnDefault);

    public int getHeaderViewCount();
    public HashMap<String, Object> getToPassProps();
    public String getSearchText();
    public AbsListSectionProxy[] getSections();
    public int getSectionCount();
    public void processSectionsAndNotify(Object[] sections);
    public AbsListSectionProxy getSectionAt(int sectionIndex);
    public void scrollToItem(int sectionIndex, int itemIndex, boolean animated);
    public KrollDict getItem(int sectionIndex, int itemIndex);
    public void setMarker(HashMap<String, Integer> m);
    public void scrollToBottom(int y, boolean animated);
    public void scrollToTop(int y, boolean animated);
    public void appendSection(Object section);
    public void deleteSectionAt(int index);
    public void insertSectionAt(int index, Object section);
    public void replaceSectionAt(int index, Object section);
}
