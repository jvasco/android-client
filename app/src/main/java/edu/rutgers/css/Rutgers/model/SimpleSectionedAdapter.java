package edu.rutgers.css.Rutgers.model;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Simple {@link edu.rutgers.css.Rutgers.model.SectionedListAdapter} implementation that should
 * be suitable for most general uses.
 * Uses the {@link SimpleSection} class to represent sections.
 */
public class SimpleSectionedAdapter<U> extends SectionedListAdapter<SimpleSection<U>, U> {

    public SimpleSectionedAdapter(@NonNull Context context, int itemResource, int headerResource, int textViewId) {
        super(context, itemResource, headerResource, textViewId);
    }

    @Override
    public String getSectionHeader(SimpleSection<U> section) {
        return section.getHeader();
    }

    @Override
    public U getSectionItem(SimpleSection<U> section, int position) {
        return section.getItems().get(position);
    }

    @Override
    public int getSectionItemCount(SimpleSection<U> section) {
        return section.getItems().size();
    }

}
