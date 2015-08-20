package de.luhmer.owncloudnewsreader.adapter;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashSet;

import de.greenrobot.dao.query.LazyList;
import de.greenrobot.event.EventBus;

import de.luhmer.owncloudnewsreader.R;
import de.luhmer.owncloudnewsreader.SettingsActivity;
import de.luhmer.owncloudnewsreader.database.DatabaseConnectionOrm;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.events.podcast.PodcastCompletedEvent;
import de.luhmer.owncloudnewsreader.events.podcast.UpdatePodcastStatusEvent;
import de.luhmer.owncloudnewsreader.helper.PostDelayHandler;
import de.luhmer.owncloudnewsreader.interfaces.IPlayPausePodcastClicked;

/**
 * Created by daniel on 28.06.15.
 */
public class NewsListRecyclerAdapter extends RecyclerView.Adapter<ViewHolder> {
    private static final String TAG = "NewsListRecyclerAdapter";

    private long idOfCurrentlyPlayedPodcast = -1;

    private LazyList<RssItem> lazyList;
    private int titleLineCount;
    private DatabaseConnectionOrm dbConn;
    private ForegroundColorSpan bodyForegroundColor;
    private PostDelayHandler pDelayHandler;
    private FragmentActivity activity;
    private HashSet<Long> stayUnreadItems = new HashSet<>();

    private IPlayPausePodcastClicked playPausePodcastClicked;

    public NewsListRecyclerAdapter(FragmentActivity activity, LazyList<RssItem> lazyList, IPlayPausePodcastClicked playPausePodcastClicked) {
        this.lazyList = lazyList;
        this.activity = activity;
        this.playPausePodcastClicked = playPausePodcastClicked;

        pDelayHandler = new PostDelayHandler(activity);

        bodyForegroundColor = new ForegroundColorSpan(activity.getResources().getColor(android.R.color.secondary_text_dark));

        dbConn = new DatabaseConnectionOrm(activity);
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        titleLineCount = Integer.parseInt(mPrefs.getString(SettingsActivity.SP_TITLE_LINES_COUNT, "2"));
        setHasStableIds(true);

        EventBus.getDefault().register(this);
    }

    public void onEventMainThread(UpdatePodcastStatusEvent podcast) {
        if (podcast.isPlaying()) {
            if (podcast.getRssItemId() != idOfCurrentlyPlayedPodcast) {
                idOfCurrentlyPlayedPodcast = podcast.getRssItemId();
                notifyDataSetChanged();

                Log.v(TAG, "Updating Listview - Podcast started");
            }
        } else if (idOfCurrentlyPlayedPodcast != -1) {
            idOfCurrentlyPlayedPodcast = -1;
            notifyDataSetChanged();

            Log.v(TAG, "Updating Listview - Podcast paused");
        }
    }

    public void onEventMainThread(PodcastCompletedEvent podcastCompletedEvent) {
        idOfCurrentlyPlayedPodcast = -1;
        notifyDataSetChanged();

        Log.v(TAG, "Updating Listview - Podcast completed");
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        Integer layout = 0;
        switch(Integer.parseInt(mPrefs.getString(SettingsActivity.SP_FEED_LIST_LAYOUT, "0"))) {
            case 0:
                layout = R.layout.subscription_detail_list_item_simple;
                break;
            case 1:
                layout = R.layout.subscription_detail_list_item_extended;
                break;
            case 2:
                layout = R.layout.subscription_detail_list_item_extended_webview;
                break;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);

        final ViewHolder holder = new ViewHolder(view, titleLineCount);

        holder.flPlayPausePodcastWrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.isPlaying()) {
                    playPausePodcastClicked.pausePodcast();
                } else {
                    playPausePodcastClicked.openPodcast(holder.getRssItem());
                }
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final RssItem item = lazyList.get(position);

        holder.setRssItem(item);

        holder.setStayUnread(stayUnreadItems.contains(item.getId()));

        holder.setClickListener((RecyclerItemClickListener) activity);

        //Podcast stuff
        if (DatabaseConnectionOrm.ALLOWED_PODCASTS_TYPES.contains(item.getEnclosureMime())) {
            final boolean isPlaying = idOfCurrentlyPlayedPodcast == item.getId();
            //Enable podcast buttons in view
            holder.flPlayPausePodcastWrapper.setVisibility(View.VISIBLE);

            holder.setPlaying(isPlaying);

            holder.setDownloadPodcastProgressbar();
        } else {
            holder.flPlayPausePodcastWrapper.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolder holder) {
        EventBus.getDefault().unregister(holder);
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder holder) {
        EventBus.getDefault().register(holder);
    }

    public void ChangeReadStateOfItem(ViewHolder viewHolder, boolean isChecked) {
        RssItem rssItem = viewHolder.getRssItem();
        if(rssItem.getRead_temp() != isChecked) { //Only perform database operations if really needed
            rssItem.setRead_temp(isChecked);
            dbConn.updateRssItem(rssItem);

            pDelayHandler.DelayTimer();

            viewHolder.setReadState(isChecked);
            //notifyItemChanged(viewHolder.getAdapterPosition());

            stayUnreadItems.add(rssItem.getId());
        }
    }

    public void toggleReadStateOfItem(ViewHolder viewHolder) {
        RssItem rssItem = viewHolder.getRssItem();
        boolean isRead = !rssItem.getRead_temp();
        ChangeReadStateOfItem(viewHolder,isRead);
    }

    public void toggleStarredStateOfItem(ViewHolder viewHolder) {
        RssItem rssItem = viewHolder.getRssItem();
        boolean isStarred = !rssItem.getStarred_temp();
        rssItem.setStarred_temp(isStarred);
        if(isStarred) {
            ChangeReadStateOfItem(viewHolder,true);
        } else {
            dbConn.updateRssItem(rssItem);
            pDelayHandler.DelayTimer();
        }
        viewHolder.setStarred(isStarred);
    }

    @Override
    public int getItemCount() {
        return lazyList != null ? lazyList.size(): 0;
    }

    @Override
    public long getItemId(int position) {
        if(lazyList != null) {
            RssItem item = lazyList.get(position);
            return item != null ? item.getId() : 0;
        }
        return 0;
    }

    public RssItem getItem(int position) {
        return lazyList.get(position);
    }

    public void setLazyList(LazyList<RssItem> lazyList) {
        this.lazyList.close();
        this.lazyList = lazyList;
        notifyDataSetChanged();
    }
}
