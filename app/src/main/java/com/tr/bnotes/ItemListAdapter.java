package com.tr.bnotes;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.tr.bnotes.db.ItemDao;
import com.tr.bnotes.util.CurrencyUtil;
import com.tr.bnotes.util.DateUtil;
import com.tr.bnotes.util.Util;
import com.tr.expenses.R;

import java.util.Calendar;

public class ItemListAdapter extends RecyclerView.Adapter<ItemListAdapter.ItemViewHolder> {
    private Cursor mCursor;
    private ItemDao.ColumnIndices mCursorColumnIndices;
    private int mCount;
    private final ItemViewHolder.OnClickListener mOnClickListener;
    private final ItemViewHolder.OnLongClickListener mOnLongClickListener;

    // calendars are expensive to re-create in terms of allocation so we keep 'em cached
    private final Calendar mToday = Calendar.getInstance();
    private final Calendar mDateCal = Calendar.getInstance();

    private SparseBooleanArray mSelectedItems = new SparseBooleanArray();

    public ItemListAdapter(ItemViewHolder.OnClickListener onClickListener,
                           ItemViewHolder.OnLongClickListener onLongClickListener) {
        mOnClickListener = onClickListener;
        mOnLongClickListener = onLongClickListener;
    }

    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item, viewGroup, false);
        return new ItemViewHolder(v, mOnClickListener, mOnLongClickListener);
    }

    @Override
    public void onBindViewHolder(ItemViewHolder itemViewHolder, final int position) {
        mCursor.moveToPosition(position);
        final Item item = ItemDao.fromCursorRow(mCursorColumnIndices, mCursor);
        itemViewHolder.detailsTextView.setText(item.getDescription());
        itemViewHolder.headerTextView.setText(item.getSubType());

        mDateCal.setTimeInMillis(item.getTimeStamp());
        itemViewHolder.dateTextView.setText(DateUtil.formatForUI(mToday, mDateCal));

        if (isSelected(position)) {
            itemViewHolder.itemLayout.setActivated(true);
        } else {
            itemViewHolder.itemLayout.setActivated(false);
        }

        final String amount = CurrencyUtil.toUnsignedCurrencyString(item.getAmount());
        final int imageDrawableResId;
        final int imageColorFilter;
        final String moneyAmountText;
        final int moneyAmountTextColor;

        final int itemType = item.getType();

        final Context itemImageContext = itemViewHolder.itemImage.getContext();
        final Context moneyAmountViewContext = itemViewHolder.moneyAmountTextView.getContext();
        if (itemType == Item.TYPE_EXPENSE) {
            imageDrawableResId = R.drawable.ic_trending_down_black_48dp;
            imageColorFilter = ContextCompat.getColor(itemImageContext, R.color.accent_color);
            moneyAmountText = "-" + amount;
            moneyAmountTextColor = ContextCompat.getColor(moneyAmountViewContext, R.color.accent_color);
        } else if (itemType == Item.TYPE_INCOME) {
            imageDrawableResId = R.drawable.ic_trending_up_black_48dp;
            imageColorFilter = ContextCompat.getColor(itemImageContext, R.color.primary_color);
            moneyAmountText = "+" + amount;
            moneyAmountTextColor = ContextCompat.getColor(moneyAmountViewContext, R.color.primary_color);
        } else {
            throw new AssertionError("Income type not supported: " + itemType);
        }

        Glide.with(itemViewHolder.itemImage.getContext())
                .load(imageDrawableResId)
                .into(itemViewHolder.itemImage);
        itemViewHolder.itemImage.setColorFilter(imageColorFilter);
        itemViewHolder.moneyAmountTextView.setText(moneyAmountText);
        itemViewHolder.moneyAmountTextView.setTextColor(moneyAmountTextColor);
    }

    @Override
    public int getItemCount() {
        return mCount;
    }

    public Item getItem(int position) {
        mCursor.moveToPosition(position);
        return ItemDao.fromCursorRow(mCursorColumnIndices, mCursor);
    }

    public int itemId(int position) { // whoops, getItemId() clashes with the RecyclerView.Adapter.getItemId() :(
        mCursor.moveToPosition(position);
        return ItemDao.getItemId(mCursorColumnIndices, mCursor);
    }

    public void swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return;
        }
        final Cursor oldCursor = mCursor;
        mCursor = newCursor;
        mCursorColumnIndices = new ItemDao.ColumnIndices(mCursor);
        mCount = mCursor.getCount();
        clearSelected();
        Util.close(oldCursor);
        // Update today's date if the last cursor was swapped on previous day (in case somebody is
        // using the app @ midnight for example)
        mToday.setTimeInMillis(System.currentTimeMillis());
    }

    public void setSelected(int position, boolean selected) {
        if (selected) {
            mSelectedItems.put(position, true);
        } else {
            mSelectedItems.delete(position);
        }
        notifyItemChanged(position);
    }

    public boolean hasItemsSelected() {
        return mSelectedItems.size() > 0;
    }

    public void clearSelected() {
        if (hasItemsSelected()) {
            mSelectedItems.clear();
            notifyDataSetChanged();
        }
    }

    public SparseBooleanArray drainSelectedPositions() {
        SparseBooleanArray selectedItems = mSelectedItems;
        mSelectedItems = new SparseBooleanArray();
        return selectedItems;
    }

    public boolean isSelected(int position) {
        return mSelectedItems.get(position);
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        private final TextView detailsTextView;
        private final TextView headerTextView;
        private final ImageView itemImage;
        private final TextView dateTextView;
        private final TextView moneyAmountTextView;
        private final OnClickListener onClickListener;
        private final OnLongClickListener onLongClickListener;
        private final LinearLayout itemLayout;
        private final View view;

        public ItemViewHolder(View v, OnClickListener onClickListener,
                              OnLongClickListener onLongClickListener) {
            super(v);
            view = v;
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            itemLayout = (LinearLayout) view.findViewById(R.id.itemLayout);
            detailsTextView = (TextView) view.findViewById(R.id.itemDetailsTextView);
            headerTextView = (TextView) view.findViewById(R.id.itemTypeTextView);
            itemImage = (ImageView) view.findViewById(R.id.itemImageView);
            dateTextView = (TextView) view.findViewById(R.id.itemDateTextView);
            moneyAmountTextView = (TextView) view.findViewById(R.id.amountTextView);
            this.onClickListener = onClickListener;
            this.onLongClickListener = onLongClickListener;
        }

        @Override
        public void onClick(View v) {
            onClickListener.onClick(v, getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            onLongClickListener.onLongClick(v, getAdapterPosition());
            return true;
        }

        interface OnLongClickListener {
            void onLongClick(View v, int adapterPosition);
        }

        interface OnClickListener {
            void onClick(View v, int adapterPosition);
        }
    }
}
