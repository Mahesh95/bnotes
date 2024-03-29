package com.tr.bnotes;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

import com.tr.bnotes.db.ItemDao;
import com.tr.bnotes.util.CurrencyUtil;
import com.tr.bnotes.util.DateUtil;
import com.tr.bnotes.util.Util;
import com.tr.expenses.R;

import java.util.Calendar;

public class ItemDetailsActivity extends AppCompatActivity {
    // Constants for startActivityForResult()
    public static final int REQUEST_CODE = 0;
    public static final int RESULT_CREATED_OR_UPDATED = 1;

    public static final String EXTRA_ITEM_DATA = "EXTRA_ITEM_DATA";
    public static final String EXTRA_ACTIVITY_TYPE = "EXTRA_ACTIVITY_TYPE";

    public static final int ACTIVITY_TYPE_EXPENSE = 0;
    public static final int ACTIVITY_TYPE_INCOME = 1;

    private static final int MAX_AMOUNT_FIELD_LENGTH = 13;

    private Item mOriginalItem;
    private int mActivityItemType;

    private TextView mSubTypeTextView;
    private TextView mDateTextView;
    private EditText mAmountEditText;
    private TextView mDetailsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        Toolbar toolbar = (Toolbar) findViewById(R.id.detailsToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setupContent();
        setupListeners();
    }


    private void setupContent() {
        mSubTypeTextView = (TextView) findViewById(R.id.typeValueEditText);
        mDateTextView = (TextView) findViewById(R.id.dateValText);
        final TextView signTextView = (TextView) findViewById(R.id.signView);
        mAmountEditText = (EditText) findViewById(R.id.amountView);
        mDetailsTextView = (TextView) findViewById(R.id.detailsEditText);

        mOriginalItem = getIntent().getParcelableExtra(EXTRA_ITEM_DATA);
        if (mOriginalItem != null) { // we are passed an item to display
            mDateTextView.setText(DateUtil.format(mOriginalItem.getTimeStamp()));
            mDetailsTextView.setText(mOriginalItem.getDescription());
            mDetailsTextView.setText(mOriginalItem.getDescription());
            ;
            mAmountEditText.setText(CurrencyUtil.toUnsignedCurrencyString(mOriginalItem.getAmount()));
            mActivityItemType = mOriginalItem.getType();
            mSubTypeTextView.setText(mOriginalItem.getSubType());
        } else { // we opened in order to create a new item
            mActivityItemType = getIntent().getIntExtra(EXTRA_ACTIVITY_TYPE, -1);
            if (mActivityItemType == ACTIVITY_TYPE_EXPENSE) {
                setTitle(getString(R.string.new_expense));
            } else if (mActivityItemType == ACTIVITY_TYPE_INCOME) {
                setTitle(getString(R.string.new_income));
            } else {
                throw new AssertionError();
            }

            mDateTextView.setText(DateUtil.format(System.currentTimeMillis()));
            mAmountEditText.setText(CurrencyUtil.toUnsignedCurrencyString(0));
        }

        final String sign;
        final int color;
        if (mActivityItemType == Item.TYPE_EXPENSE) {
            color = ContextCompat.getColor(this, R.color.accent_color);
            getSupportActionBar().setBackgroundDrawable(ContextCompat.getDrawable(this, R.color.accent_color));
            sign = "-";
        } else {
            color = ContextCompat.getColor(this, R.color.primary_color);
            getSupportActionBar().setBackgroundDrawable(ContextCompat.getDrawable(this, R.color.primary_color));
            sign = "+";
        }
        mSubTypeTextView.setTextColor(color);
        mAmountEditText.setTextColor(color);
        signTextView.setText(sign);
        signTextView.setTextColor(color);
    }

    private void setupListeners() {
        mDateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long previousTimeValue = DateUtil.parse(mDateTextView.getText().toString());
                Util.showDatePicker(mDateTextView.getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(year, monthOfYear, dayOfMonth);
                        mDateTextView.setText(DateUtil.format(cal.getTimeInMillis()));
                    }
                }, previousTimeValue);
            }
        });

        mAmountEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAmountEditText.setSelection(mAmountEditText.getText().length());
            }
        });

        // http://stackoverflow.com/a/5191860
        mAmountEditText.addTextChangedListener(new TextWatcher() {
            private String current = "";
            private String lastValidAmount;

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().equals(current)) {
                    // disable this listener while performing shenanigans to mAmountEditText so as
                    // not to fall into recursion on setText() call.
                    mAmountEditText.removeTextChangedListener(this);
                    current = CurrencyUtil.sanitize(s.toString());
                    if (current.length() <= MAX_AMOUNT_FIELD_LENGTH) { // ok to go, update the field
                        mAmountEditText.setText(current);
                        lastValidAmount = current;
                    } else { // field is too long
                        mAmountEditText.setText(lastValidAmount);
                        IntrusiveToast.show(ItemDetailsActivity.this, getString(R.string.value_is_too_large));
                    }
                    mAmountEditText.addTextChangedListener(this);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                mAmountEditText.setSelection(mAmountEditText.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        mSubTypeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                hideKeyboard(v);
                displaySubTypePickerDialog();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_item_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.actionSaveItem:
                if (saveItem()) {
                    setResult(RESULT_CREATED_OR_UPDATED);
                    finish();
                    return true;
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void displaySubTypePickerDialog() {
        new AsyncTask<Void, Void, Void>() {
            private String title;
            private String[] items;

            @Override
            protected Void doInBackground(Void... params) {
                String other = getString(R.string.other_ellipsized);
                if (mActivityItemType == Item.TYPE_EXPENSE) {
                    title = getString(R.string.pick_expense_type);
                    items = ItemDao.getExpenseTypes(ItemDetailsActivity.this, other);
                } else {
                    title = getString(R.string.pick_income_type);
                    items = ItemDao.getIncomeTypes(ItemDetailsActivity.this, other);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                displaySubTypePickerDialog(title, items);
            }
        }.execute();
    }

    private void displaySubTypePickerDialog(final String title, final String[] items) {
        DialogFragment subTypePickerDialog = new DialogFragment() {
            @NonNull
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                final String other = getString(R.string.other_ellipsized);
                builder.setTitle(title).setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String text = items[which];
                        if (text.equals(other)) {
                            displayCustomSubTypePickerDialog();
                        } else {
                            mSubTypeTextView.setText(items[which]);
                        }
                    }
                });
                return builder.create();
            }
        };
        subTypePickerDialog.show(getSupportFragmentManager(), null);
    }

    private void displayCustomSubTypePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String title;
        if (mActivityItemType == Item.TYPE_EXPENSE) {
            title = getString(R.string.custom_expense);
        } else {
            title = getString(R.string.custom_income);
        }
        builder.setTitle(title);

        final EditText input = new EditText(this);

        builder.setView(input);
        input.performClick();
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSubTypeTextView.setText(input.getText());
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    /**
     * Saves currently displayed item into the database
     */
    private boolean saveItem() {
        String subType = mSubTypeTextView.getText().toString();
        if (subType.isEmpty()) {
            IntrusiveToast.show(this, getString(R.string.please_pick_the_type));
            return false;
        }
        long date = DateUtil.parse(mDateTextView.getText().toString());
        long amount = CurrencyUtil.fromString(mAmountEditText.getText().toString());
        String details = mDetailsTextView.getText().toString();

        final int id;
        if (mOriginalItem != null) {
            id = mOriginalItem.getId();
        } else {
            id = Item.NO_ID;
        }
        final Item item = new Item(mActivityItemType, subType, details, amount, date, id);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ItemDao.putItemIntoDb(ItemDetailsActivity.this, item);
                return null;
            }
        }.execute();
        return true;
    }
}
