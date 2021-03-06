package edu.rutgers.css.Rutgers.link;

import android.os.AsyncTask;
import android.os.Bundle;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.TimeUnit;

import edu.rutgers.css.Rutgers.api.ApiRequest;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.channels.dtable.fragments.DTable;
import edu.rutgers.css.Rutgers.channels.dtable.model.DTableChannel;
import edu.rutgers.css.Rutgers.channels.dtable.model.DTableElement;
import edu.rutgers.css.Rutgers.channels.dtable.model.DTableRoot;
import edu.rutgers.css.Rutgers.model.Channel;

import static edu.rutgers.css.Rutgers.utils.LogUtils.LOGE;

/**
 * Perform the initial load and post to an event bus
 */
public class LinkLoadTask extends AsyncTask<LinkLoadArgs, Void, Bundle> {
    private final String homeCampus;

    private static final String TAG = "InitLoadAsyncTask";

    public LinkLoadTask(final String homeCampus) {
        super();
        this.homeCampus = homeCampus;
    }

    @Override
    protected Bundle doInBackground(LinkLoadArgs... args) {
        if (args.length != 1) {
            return null;
        }

        LinkLoadArgs firstArgs = args[0];
        Channel channel = firstArgs.getChannel();
        String channelTag = channel.getView();
        List<String> pathParts = firstArgs.getPathParts();

        switch (channelTag) {
            case "dtable":
                return switchDtable(channel, pathParts);
            default:
                return null;
        }
    }

    @Override
    protected void onPostExecute(Bundle args) {
        LinkBus.getInstance().post(args);
    }

    /**
     * @param channel dtable
     * @param pathParts names of dtable elements
     * @return bundle to start this dtable
     */
    public Bundle switchDtable(Channel channel, List<String> pathParts) {
        try {
            // JSON representing the dtable
            JsonObject jsonObject;
            if (StringUtils.isNotBlank(channel.getApi())) {
                jsonObject = ApiRequest.api(channel.getApi(), TimeUnit.HOURS, JsonObject.class);
            } else {
                jsonObject = ApiRequest.json(channel.getUrl(), TimeUnit.HOURS, JsonObject.class);
            }

            DTableRoot root = new DTableRoot(jsonObject, null);
            for (int i = 0; i < pathParts.size(); i++) {
                final String part = pathParts.get(i);
                boolean found = false;
                for (final DTableElement child : root.getChildren()) {
                    final String decoded = URLDecoder.decode(child.getTitle(), "UTF-8");
                    if (decoded.equals(part)) {
                        if (child instanceof DTableRoot) {
                            // look for the next element in this root's children
                            root = (DTableRoot) child;
                            found = true;
                            break;
                        } else if (child instanceof DTableChannel) {
                            if (i != pathParts.size() - 1) {
                                return null;
                            }

                            DTableChannel dTableChannel = (DTableChannel) child;
                            Bundle newArgs = new Bundle();
                            // Must have view and title set to launch a channel
                            newArgs.putString(ComponentFactory.ARG_COMPONENT_TAG, dTableChannel.getView());
                            newArgs.putString(ComponentFactory.ARG_TITLE_TAG, dTableChannel.getChannelTitle(homeCampus));
                            newArgs.putString(ComponentFactory.ARG_HANDLE_TAG, channel.getHandle());
                            newArgs.putStringArrayList(ComponentFactory.ARG_HIST_TAG, dTableChannel.getParent().getHistory());

                            // Add optional fields to the arg bundle
                            if (dTableChannel.getUrl() != null) {
                                newArgs.putString(ComponentFactory.ARG_URL_TAG, dTableChannel.getUrl());
                            }

                            if (dTableChannel.getData() != null) {
                                newArgs.putString(ComponentFactory.ARG_DATA_TAG, dTableChannel.getData());
                            }

                            if (dTableChannel.getCount() > 0) {
                                newArgs.putInt(ComponentFactory.ARG_COUNT_TAG, dTableChannel.getCount());
                            }

                            return newArgs;
                        }
                    }
                }
                if (!found) {
                    return null;
                }
            }

            return DTable.createArgs(root.getTitle(), channel.getHandle(), null, root);
        } catch (JsonSyntaxException | IOException e) {
            LOGE(TAG, e.getMessage());
            return null;
        }
    }
}
