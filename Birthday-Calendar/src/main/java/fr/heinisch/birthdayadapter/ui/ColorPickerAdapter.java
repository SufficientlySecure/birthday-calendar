/*
 * Copyright (C) 2023 Matthias
 *
 * This file is part of Birthday Adapter.
 *
 * Birthday Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fr.heinisch.birthdayadapter.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import fr.heinisch.birthdayadapter.R;

public class ColorPickerAdapter extends RecyclerView.Adapter<ColorPickerAdapter.ViewHolder> {

    public static final int CUSTOM_COLOR = Integer.MAX_VALUE;

    private final int[] colors;
    private final OnColorSelectedListener colorListener;
    private final OnCustomColorSelectedListener customColorListener;
    private final int numColumns;
    private int itemSize;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public interface OnCustomColorSelectedListener {
        void onCustomColorSelected();
    }

    ColorPickerAdapter(int[] colors, int numColumns, OnColorSelectedListener colorListener, OnCustomColorSelectedListener customColorListener) {
        this.colors = colors;
        this.numColumns = numColumns;
        this.colorListener = colorListener;
        this.customColorListener = customColorListener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.post(() -> {
            if (recyclerView.getWidth() > 0) {
                int horizontalPadding = recyclerView.getPaddingLeft() + recyclerView.getPaddingRight();
                int availableWidth = recyclerView.getWidth() - horizontalPadding;
                itemSize = availableWidth / numColumns;
                notifyDataSetChanged(); // Redraw the items with the new size
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (itemSize > 0) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            int horizontalMargins = layoutParams.leftMargin + layoutParams.rightMargin;
            int size = itemSize - horizontalMargins;
            layoutParams.width = size;
            layoutParams.height = size;
            holder.itemView.setLayoutParams(layoutParams);
        }
        holder.bind(colors[position], colorListener, customColorListener);
    }

    @Override
    public int getItemCount() {
        return colors.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView colorView;

        ViewHolder(View view) {
            super(view);
            colorView = (ImageView) view;
        }

        void bind(final int color, final OnColorSelectedListener colorListener, final OnCustomColorSelectedListener customColorListener) {
            if (color == CUSTOM_COLOR) {
                colorView.setBackgroundResource(R.drawable.color_picker_custom_background);
                itemView.setOnClickListener(v -> customColorListener.onCustomColorSelected());
            } else {
                // Make sure the default background is set for recycling views
                colorView.setBackgroundResource(R.drawable.color_picker_item_background);
                if (colorView.getBackground() instanceof GradientDrawable) {
                    GradientDrawable background = (GradientDrawable) colorView.getBackground().mutate();
                    background.setColor(color);
                }
                itemView.setOnClickListener(v -> colorListener.onColorSelected(color));
            }
        }
    }
}
