package com.tr.bnotes;

import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;

import com.tr.bnotes.db.ItemDao;
import com.tr.bnotes.util.CurrencyUtil;
import com.tr.bnotes.util.DateUtil;
import com.tr.bnotes.util.PieChartUtil;
import com.tr.bnotes.util.Util;
import com.tr.expenses.R;

import java.util.Calendar;
import java.util.Map;

import lecho.lib.hellocharts.view.PieChartView;

public class StatsActivity extends AppCompatActivity {

    private static final int TAB_POSITION_INCOME = 0;
    private static final int TAB_POSITION_EXPENSE = 1;
    private static final String FROM_DATE_KEY = "from_date";
    private static final String TO_DATE_KEY = "to_date";

    private int[] mExpenseColors;
    private int[] mIncomeColors;

    private int mPrimaryColor;
    private int mAccentColor;
    private int mSecondaryTextColor;

    private TextView mFromDateView;
    private TextView mToDateView;
    private TextView mIncomeView;
    private TextView mExpenseView;
    private TextView mNoChartDataTextView;
    private TextView mMargin;
    private ConsolidatedStatement mConsolidatedStatement;
    private PieChartView mChart;

    private int mSelectedTabPosition;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mFromDateView != null && mToDateView != null) {
            long toDate = DateUtil.parse(mToDateView.getText().toString());
            long fromDate = DateUtil.parse(mFromDateView.getText().toString());
            outState.putLong(TO_DATE_KEY, toDate);
            outState.putLong(FROM_DATE_KEY, fromDate);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        initColors();

        Toolbar toolbar = (Toolbar) findViewById(R.id.statsToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mChart = (PieChartView) findViewById(R.id.chart);
        mFromDateView = (TextView) findViewById(R.id.fromDateText);
        mNoChartDataTextView = (TextView) findViewById(R.id.noChartDataTextView);
        mToDateView = (TextView) findViewById(R.id.toDateText);
        mIncomeView = (TextView) findViewById(R.id.incomeAmountView);
        mExpenseView = (TextView) findViewById(R.id.expenseAmountView);
        mMargin = (TextView) findViewById(R.id.marginAmountTextView);

        if (savedInstanceState != null) {
            long toDate = savedInstanceState.getLong(TO_DATE_KEY);
            long fromDate = savedInstanceState.getLong(FROM_DATE_KEY);
            mToDateView.setText(DateUtil.format(toDate));
            mFromDateView.setText(DateUtil.format(fromDate));
        } else {
            long now = System.currentTimeMillis();
            mFromDateView.setText(DateUtil.format(now));
            mToDateView.setText(DateUtil.format(now));
        }

        PieChartUtil.initChart(mChart);
        setupDateViews();
        setupTabLayout();

        refreshDisplayedData();
    }

    private void setupDateViews() {
        mFromDateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long previousTimeValue = DateUtil.parse(mFromDateView.getText().toString());
                Util.showDatePicker(v.getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        long newDateMillis = millisFromCalendarDate(year, monthOfYear, dayOfMonth);
                        updateDateView(mFromDateView, newDateMillis);
                        long toDate = DateUtil.parse(mToDateView.getText().toString());
                        long fromDate = DateUtil.parse(mFromDateView.getText().toString());
                        if (fromDate > toDate) {
                            updateDateView(mToDateView, newDateMillis);
                        }
                        refreshDisplayedData();
                    }
                }, previousTimeValue);
            }
        });

        mToDateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long previousTimeValue = DateUtil.parse(mToDateView.getText().toString());
                Util.showDatePicker(v.getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        long newDateMillis = millisFromCalendarDate(year, monthOfYear, dayOfMonth);
                        updateDateView(mToDateView, newDateMillis);
                        // check if we are before the fromdate
                        long toDate = DateUtil.parse(mToDateView.getText().toString());
                        long fromDate = DateUtil.parse(mFromDateView.getText().toString());
                        if (toDate < fromDate) {
                            updateDateView(mFromDateView, newDateMillis);
                        }
                        refreshDisplayedData();
                    }
                }, previousTimeValue);
            }
        });
    }


    private void setupTabLayout() {
        final TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.addTab(tabLayout.newTab().setText(getResources().getString(R.string.income_chart)),
                TAB_POSITION_INCOME);
        tabLayout.addTab(tabLayout.newTab().setText(getResources().getString(R.string.expense_chart)),
                TAB_POSITION_EXPENSE);
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == TAB_POSITION_EXPENSE) {
                    mSelectedTabPosition = TAB_POSITION_EXPENSE;
                    tabLayout.setSelectedTabIndicatorColor(mAccentColor);
                } else {
                    mSelectedTabPosition = TAB_POSITION_INCOME;
                    tabLayout.setSelectedTabIndicatorColor(mPrimaryColor);
                }
                displayChart();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void refreshDisplayedData() {
        final long from = DateUtil.parse(mFromDateView.getText().toString());
        final long to = DateUtil.parse(mToDateView.getText().toString());

        new AsyncTask<Void, Void, ConsolidatedStatement>() {
            @Override
            protected ConsolidatedStatement doInBackground(Void... params) {
                return ItemDao.readConsolidatedStatementForPeriod(StatsActivity.this, from, to);
            }

            @Override
            protected void onPostExecute(ConsolidatedStatement consolidatedStatement) {
                mConsolidatedStatement = consolidatedStatement;
                displayStatementData();
                displayChart();
            }
        }.execute();
    }

    private void displayChart() {
        if (mSelectedTabPosition == TAB_POSITION_EXPENSE) {
            String noExpenseString = getResources().getString(R.string.no_expenses_in_this_period);
            displayChart(mConsolidatedStatement.getExpenses(), mExpenseColors, noExpenseString);
        } else {
            String noIncomeString = getResources().getString(R.string.no_income_in_this_period);
            displayChart(mConsolidatedStatement.getIncomes(), mIncomeColors, noIncomeString);
        }
    }

    private void displayChart(Map<String, Long> values, int[] colors, String noValueString) {
        if (values.size() == 0) {
            mNoChartDataTextView.setText(noValueString);
            mChart.setVisibility(View.GONE);
            mNoChartDataTextView.setVisibility(View.VISIBLE);
        } else {
            PieChartUtil.setData(mChart, values, colors);
            mNoChartDataTextView.setVisibility(View.GONE);
            mChart.setVisibility(View.VISIBLE);
        }
    }

    private void displayStatementData() {
        final int marginViewColor;
        final long margin = mConsolidatedStatement.getMargin();
        if (margin > 0) {
            marginViewColor = mPrimaryColor;
        } else if (margin < 0) {
            marginViewColor = mAccentColor;
        } else {
            marginViewColor = mSecondaryTextColor;
        }

        mMargin.setTextColor(marginViewColor);
        mIncomeView.setText(CurrencyUtil.toUnsignedCurrencyString(mConsolidatedStatement.getTotalIncome()));
        mExpenseView.setText(CurrencyUtil.toUnsignedCurrencyString(mConsolidatedStatement.getTotalExpense()));
        mMargin.setText(CurrencyUtil.toSignedCurrencyString(margin));
    }

    private void initColors() {
        mPrimaryColor = ContextCompat.getColor(StatsActivity.this, R.color.primary_color);
        mAccentColor = ContextCompat.getColor(StatsActivity.this, R.color.accent_color);
        mSecondaryTextColor = ContextCompat.getColor(StatsActivity.this, R.color.text_secondary);

        mExpenseColors = new int[]{
                ContextCompat.getColor(this, R.color.expense_graph_color_1),
                ContextCompat.getColor(this, R.color.expense_graph_color_2),
                ContextCompat.getColor(this, R.color.expense_graph_color_3),
                ContextCompat.getColor(this, R.color.expense_graph_color_4),
                ContextCompat.getColor(this, R.color.expense_graph_color_5)
        };

        mIncomeColors = new int[]{
                ContextCompat.getColor(this, R.color.income_graph_color_1),
                ContextCompat.getColor(this, R.color.income_graph_color_2),
                ContextCompat.getColor(this, R.color.income_graph_color_3),
                ContextCompat.getColor(this, R.color.income_graph_color_4),
                ContextCompat.getColor(this, R.color.income_graph_color_5)
        };
    }

    private static void updateDateView(TextView dateView, long date) {
        dateView.setText(DateUtil.format(date));
    }

    private static long millisFromCalendarDate(int year, int monthOfYear, int dayOfMonth) {
        Calendar newDate = Calendar.getInstance();
        newDate.set(year, monthOfYear, dayOfMonth);
        return newDate.getTimeInMillis();
    }
}
