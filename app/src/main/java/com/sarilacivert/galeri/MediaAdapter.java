package com.sarilacivert.galeri;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

public class MediaAdapter
        extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(MediaItem item, int position);
    }

    private final Context context;
    private final List<MediaItem> mediaItems;
    private final OnMediaClickListener listener;

    private int columnCount = 3;

    public MediaAdapter(
            Context context,
            List<MediaItem> mediaItems,
            OnMediaClickListener listener
    ) {
        this.context = context;
        this.mediaItems = mediaItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(context)
                .inflate(
                        R.layout.item_media,
                        parent,
                        false
                );

        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MediaViewHolder holder,
            int position
    ) {

        MediaItem item = mediaItems.get(position);

        /*
         * Kare kutular.
         * 3 / 4 / 5 sütuna geçince boyut otomatik değişir.
         */
        int screenWidth =
                context.getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int itemSize =
                screenWidth / columnCount;

        ViewGroup.LayoutParams params =
                holder.itemView.getLayoutParams();

        params.width =
                ViewGroup.LayoutParams.MATCH_PARENT;

        params.height =
                itemSize;

        holder.itemView.setLayoutParams(params);

        Glide.with(context)
                .load(item.getUri())
                .centerCrop()
                .placeholder(R.color.surface_alt)
                .error(R.color.surface_alt)
                .into(holder.imgMedia);

        if (item.isVideo()) {

            holder.txtVideoBadge.setVisibility(
                    View.VISIBLE
            );

            holder.txtDuration.setVisibility(
                    View.VISIBLE
            );

            holder.txtDuration.setText(
                    formatDuration(
                            item.getDuration()
                    )
            );

        } else {

            holder.txtVideoBadge.setVisibility(
                    View.GONE
            );

            holder.txtDuration.setVisibility(
                    View.GONE
            );
        }

        holder.itemView.setOnClickListener(
                v -> {

                    int adapterPosition =
                            holder.getBindingAdapterPosition();

                    if (
                            adapterPosition
                                    != RecyclerView.NO_POSITION
                                    && listener != null
                    ) {

                        listener.onMediaClick(
                                mediaItems.get(
                                        adapterPosition
                                ),
                                adapterPosition
                        );
                    }
                }
        );
    }

    private String formatDuration(long milliseconds) {

        if (milliseconds <= 0) {
            return "00:00";
        }

        long totalSeconds =
                milliseconds / 1000;

        long hours =
                totalSeconds / 3600;

        long minutes =
                (totalSeconds % 3600) / 60;

        long seconds =
                totalSeconds % 60;

        if (hours > 0) {

            return String.format(
                    Locale.getDefault(),
                    "%d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
            );

        } else {

            return String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    minutes,
                    seconds
            );
        }
    }

    public void setColumnCount(int columnCount) {

        if (
                columnCount < 3
                        || columnCount > 5
        ) {
            return;
        }

        this.columnCount =
                columnCount;

        notifyDataSetChanged();
    }

    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    public void notifyMediaChanged() {
        notifyDataSetChanged();
    }

    static class MediaViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imgMedia;

        TextView txtVideoBadge;
        TextView txtDuration;

        MediaViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            imgMedia =
                    itemView.findViewById(
                            R.id.imgMedia
                    );

            txtVideoBadge =
                    itemView.findViewById(
                            R.id.txtVideoBadge
                    );

            txtDuration =
                    itemView.findViewById(
                            R.id.txtDuration
                    );
        }
    }
    }
