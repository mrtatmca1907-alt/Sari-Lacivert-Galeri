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

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    private final Context context;
    private final List<Album> albums;
    private final OnAlbumClickListener listener;

    public AlbumAdapter(
            Context context,
            List<Album> albums,
            OnAlbumClickListener listener
    ) {
        this.context = context;
        this.albums = albums;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_album, parent, false);

        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull AlbumViewHolder holder,
            int position
    ) {
        Album album = albums.get(position);

        holder.txtAlbumName.setText(album.getName());

        String countText =
                album.getItemCount()
                        + " öğe • "
                        + album.getPhotoCount()
                        + " foto • "
                        + album.getVideoCount()
                        + " video";

        holder.txtAlbumCount.setText(countText);
        holder.txtAlbumPath.setText(album.getPath());

        if (album.isCoverVideo()) {
            holder.txtVideoBadge.setVisibility(View.VISIBLE);
        } else {
            holder.txtVideoBadge.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(album.getCoverUri())
                .centerCrop()
                .placeholder(R.color.surface_alt)
                .error(R.color.surface_alt)
                .into(holder.imgCover);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAlbumClick(album);
            }
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void notifyAlbumsChanged() {
        notifyDataSetChanged();
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {

        ImageView imgCover;
        TextView txtAlbumName;
        TextView txtAlbumCount;
        TextView txtAlbumPath;
        TextView txtVideoBadge;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);

            imgCover = itemView.findViewById(R.id.imgCover);
            txtAlbumName = itemView.findViewById(R.id.txtAlbumName);
            txtAlbumCount = itemView.findViewById(R.id.txtAlbumCount);
            txtAlbumPath = itemView.findViewById(R.id.txtAlbumPath);
            txtVideoBadge = itemView.findViewById(R.id.txtVideoBadge);
        }
    }
}
