package protect.budgetwatch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class for managing data in the database
 */
public class DBHelper extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "BudgetWatch.db";
    public static final int ORIGINAL_DATABASE_VERSION = 1;
    public static final int DATABASE_VERSION = 2;

    /**
     * All strings used with the budget table
     */
    static class BudgetDbIds
    {
        public static final String TABLE = "budgets";
        public static final String NAME = "_id";
        public static final String MAX = "max";
    }

    /**
     * All strings used in the transaction table
     */
    static class TransactionDbIds
    {
        public static final String TABLE = "transactions";
        public static final String NAME = "_id";
        public static final String TYPE = "type";
        public static final String DESCRIPTION = "description";
        public static final String ACCOUNT = "account";
        public static final String BUDGET = "budget";
        public static final String VALUE = "value";
        public static final String NOTE = "note";
        public static final String DATE = "date";
        public static final String RECEIPT = "receipt";

        public static final int EXPENSE = 1;
        public static final int REVENUE = 2;
    }

    public DBHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // create table for budgets
        db.execSQL(
                "create table  " + BudgetDbIds.TABLE + "(" +
                        BudgetDbIds.NAME + " text primary key," +
                        BudgetDbIds.MAX + " INTEGER not null)");
       // create table for transactions
        db.execSQL("create table " + TransactionDbIds.TABLE + "(" +
                TransactionDbIds.NAME + " INTEGER primary key autoincrement," +
                TransactionDbIds.TYPE + " INTEGER not null," +
                TransactionDbIds.DESCRIPTION + " TEXT not null," +
                TransactionDbIds.ACCOUNT + " TEXT," +
                TransactionDbIds.BUDGET + " TEXT," +
                TransactionDbIds.VALUE + " REAL not null," +
                TransactionDbIds.NOTE + " TEXT," +
                TransactionDbIds.DATE + " INTEGER not null," +
                TransactionDbIds.RECEIPT + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // Upgrade from version 1 to version 2
        if(oldVersion < 2 && newVersion >= 2)
        {
            db.execSQL("ALTER TABLE " + TransactionDbIds.TABLE
                + " ADD COLUMN " + TransactionDbIds.RECEIPT + " TEXT");
        }
    }

    /**
     * Insert a budget into the database.
     *
     * @param name
     *      name of the budget
     * @param max
     *      the value of the budget, per month
     * @return true if the insertion was successful,
     * false otherwise
     */
    public boolean insertBudget(final String name, final int max)
    {
        SQLiteDatabase db = getWritableDatabase();
        boolean result = insertBudget(db, name, max);
        db.close();

        return result;
    }

    /**
     * Insert a budget into the database, using a provided
     * writable database instance. This is useful if
     * multiple insertions will occur in the same transaction.
     *
     * @param writableDb
     *      writable database instance to use
     * @param name
     *      name of the budget
     * @param max
     *      the value of the budget, per month
     * @return true if the insertion was successful,
     * false otherwise
     */
    public boolean insertBudget(SQLiteDatabase writableDb, final String name, final int max)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(BudgetDbIds.NAME, name);
        contentValues.put(BudgetDbIds.MAX, max);

        final long newId = writableDb.insert(BudgetDbIds.TABLE, null, contentValues);
        return (newId != -1);
    }

    /**
     * Update the budget value of a given budget in the database
     *
     * @param name
     *      name of the budget to update
     * @param max
     *      updated budget value to commit
     * @return true if the provided budget exists and the value
     * was successfully updated, false otherwise.
     */
    public boolean updateBudget(final String name, final int max)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(BudgetDbIds.MAX, max);

        SQLiteDatabase db = getWritableDatabase();
        int rowsUpdated = db.update(BudgetDbIds.TABLE, contentValues, BudgetDbIds.NAME + "=?",
                new String[]{name});
        db.close();

        return (rowsUpdated == 1);
    }

    /**
     * Delete a given budget from the database
     *
     * @param name
     *      name of the budget to delete
     * @return if the budget was successfully deleted,
     * false otherwise
     */
    public boolean deleteBudget (final String name)
    {
        SQLiteDatabase db = getWritableDatabase();
        int rowsDeleted =  db.delete(BudgetDbIds.TABLE,
                BudgetDbIds.NAME + " = ? ",
                new String[]{name});
        db.close();
        return (rowsDeleted == 1);
    }

    /**
     * Get Budget object for the named budget in the database,
     * but only fill in the 'name' and 'max' fields;
     * the value of other fields is undefined.
     *
     * @param name
     *      name of the budget to query
     * @return Budget object representing the named budget,
     * or null if it could not be queried
     */
    public Budget getBudgetStoredOnly(final String name)
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data = db.rawQuery("select * from " + BudgetDbIds.TABLE +
                " where " + BudgetDbIds.NAME + "=?", new String[]{name});

        Budget budget = null;

        if(data.getCount() == 1)
        {
            data.moveToFirst();
            String goalName = data.getString(data.getColumnIndexOrThrow(BudgetDbIds.NAME));
            int goalMax = data.getInt(data.getColumnIndexOrThrow(BudgetDbIds.MAX));

            budget = new Budget(goalName, goalMax, 0);
        }

        data.close();
        db.close();

        return budget;
    }

    /**
     * Get Budget objects for each budget in the database,
     * with the 'current' field filled out from all transactions
     * between the provided dates.
     *
     * @param startDateMs
     *      first date in milliseconds for transactions to compute
     *      into the 'current' field.
     * @param endDateMs
     *      last date in milliseconds for transactions to compute
     *      into the 'current' field.
     * @return list of Budget objects, or an empty field if none
     * could be found.
     */
    public List<Budget> getBudgets(long startDateMs, long endDateMs)
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data = db.rawQuery("select * from " + BudgetDbIds.TABLE +
                " ORDER BY " + BudgetDbIds.NAME, null);

        LinkedList<Budget> budgets = new LinkedList<>();

        // Determine over how many months the budgets represent.
        // Adjust the budget max to match the number of months
        // represented.
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(startDateMs);
        final int MONTHS_PER_YEAR = 12;
        int startMonths = date.get(Calendar.YEAR) * MONTHS_PER_YEAR + date.get(Calendar.MONTH);
        date.setTimeInMillis(endDateMs);
        int endMonths = date.get(Calendar.YEAR) * MONTHS_PER_YEAR + date.get(Calendar.MONTH);
        int totalMonthsInRange = endMonths - startMonths + 1;

        if(data.moveToFirst())
        {
            do
            {
                String name = data.getString(data.getColumnIndexOrThrow(BudgetDbIds.NAME));
                int max = data.getInt(data.getColumnIndexOrThrow(BudgetDbIds.MAX)) * totalMonthsInRange;
                int current = getTotalForBudget(name, startDateMs, endDateMs);

                budgets.add(new Budget(name, max, current));
            } while(data.moveToNext());
        }

        data.close();
        db.close();

        return budgets;
    }

    /**
     * Determine the total value, expenses - revenues, for all transactions
     * of the given budget over the provided time interval.
     *
     * @param budget
     *      budget to compute value for
     * @param startDateMs
     *      first date in milliseconds for transactions to use in
     *      the computation
     * @param endDateMs
     *      last date in milliseconds for transactions to use in
     *      the computation
     * @return value of all expenses less all revenues for all transactions
     * in the given time window, where the expense and revenue values
     * are truncated into integers.
     */
    public int getTotalForBudget(String budget, long startDateMs, long endDateMs)
    {
        int expense = getTotalForTransactionType(TransactionDbIds.EXPENSE, budget, startDateMs, endDateMs);
        int revenue = getTotalForTransactionType(TransactionDbIds.REVENUE, budget, startDateMs, endDateMs);

        return expense - revenue;
    }

    /**
     * Get the total value of all transactions of the provided type for the provided
     * budget over the given interval, truncated to an integer.
     *
     * @param type
     *      transaction type, either EXPENSE or REVENUE
     * @param budget
     *      budget to compute value for
     * @param startDateMs
     *      first date in milliseconds for transactions to use in
     *      the computation
     * @param endDateMs
     *      last date in milliseconds for transactions to use in
     *      the computation
     * @return value of either all expenses or revenues for all transactions
     * in the given time window, where the values is truncated into an integer.
     */
    public int getTotalForTransactionType(int type, String budget, long startDateMs, long endDateMs)
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data = db.rawQuery("select total(" + TransactionDbIds.VALUE + ") " +
                        "from " + TransactionDbIds.TABLE + " " +
                        "WHERE " + TransactionDbIds.BUDGET + " = ? AND " +
                        TransactionDbIds.TYPE + " = ? AND " +
                        TransactionDbIds.DATE + " >= ? AND " +
                        TransactionDbIds.DATE + " <= ?",
                new String[]{budget, Integer.toString(type),
                        Long.toString(startDateMs), Long.toString(endDateMs)});
        data.moveToFirst();
        int value = data.getInt(0);
        data.close();
        db.close();

        return value;
    }

    /**
     * @return list of all budget names in the database
     */
    public List<String> getBudgetNames()
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data = db.rawQuery("select " + BudgetDbIds.NAME + " from " + BudgetDbIds.TABLE +
                " ORDER BY " + BudgetDbIds.NAME, null);

        LinkedList<String> budgetNames = new LinkedList<>();

        if(data.moveToFirst())
        {
            do
            {
                String name = data.getString(data.getColumnIndexOrThrow(BudgetDbIds.NAME));

                budgetNames.add(name);
            } while(data.moveToNext());
        }

        data.close();
        db.close();

        return budgetNames;
    }

    /**
     * @return the number of budgets in the database
     */
    public int getBudgetCount()
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data =  db.rawQuery("SELECT Count(*) FROM " + BudgetDbIds.TABLE, null);

        int numItems = 0;

        if(data.getCount() == 1)
        {
            data.moveToFirst();
            numItems = data.getInt(0);
        }

        data.close();
        db.close();

        return numItems;
    }

    /**
     * Insert a transaction into the database.
     *
     * @return true if the insertion was successful,
     * false otherwise
     */
    public boolean insertTransaction(final int type, final String description, final String account, final String budget,
                                 final double value, final String note, final long dateInMs, final String receipt)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TransactionDbIds.TYPE, type);
        contentValues.put(TransactionDbIds.DESCRIPTION, description);
        contentValues.put(TransactionDbIds.ACCOUNT, account);
        contentValues.put(TransactionDbIds.BUDGET, budget);
        contentValues.put(TransactionDbIds.VALUE, value);
        contentValues.put(TransactionDbIds.NOTE, note);
        contentValues.put(TransactionDbIds.DATE, dateInMs);
        contentValues.put(TransactionDbIds.RECEIPT, receipt);

        SQLiteDatabase db = getWritableDatabase();
        long newId = db.insert(TransactionDbIds.TABLE, null, contentValues);
        db.close();

        return (newId != -1);
    }

    /**
     * Insert a transaction into the database, using a provided
     * writable database instance. This is useful if
     * multiple insertions will occur in the same transaction.
     *
     * @param writableDb
     *      writable database instance to use
     * @return true if the insertion was successful,
     * false otherwise
     */
    public boolean insertTransaction(SQLiteDatabase writableDb, final int id, final int type, final String description, final String account, final String budget,
                                     final double value, final String note, final long dateInMs, final String receipt)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TransactionDbIds.NAME, id);
        contentValues.put(TransactionDbIds.TYPE, type);
        contentValues.put(TransactionDbIds.DESCRIPTION, description);
        contentValues.put(TransactionDbIds.ACCOUNT, account);
        contentValues.put(TransactionDbIds.BUDGET, budget);
        contentValues.put(TransactionDbIds.VALUE, value);
        contentValues.put(TransactionDbIds.NOTE, note);
        contentValues.put(TransactionDbIds.DATE, dateInMs);
        contentValues.put(TransactionDbIds.RECEIPT, receipt);

        long newId = writableDb.insert(TransactionDbIds.TABLE, null, contentValues);
        return (newId != -1);
    }

    /**
     * Update the transaction in the database. The unique ID for
     * the transaction must be provided, all other fields represent
     * the values as will be updated in the database.
     *
     * @param id
     *      unique id for the transaction
     * @return true if the provided transaction exists and the value
     * was successfully updated, false otherwise.
     */
    public boolean updateTransaction(final int id, final int type, final String description,
                                     final String account, final String budget, final double value,
                                     final String note, final long dateInMs, final String receipt)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TransactionDbIds.TYPE, type);
        contentValues.put(TransactionDbIds.DESCRIPTION, description);
        contentValues.put(TransactionDbIds.ACCOUNT, account);
        contentValues.put(TransactionDbIds.BUDGET, budget);
        contentValues.put(TransactionDbIds.VALUE, value);
        contentValues.put(TransactionDbIds.NOTE, note);
        contentValues.put(TransactionDbIds.DATE, dateInMs);
        contentValues.put(TransactionDbIds.RECEIPT, receipt);

        SQLiteDatabase db = getWritableDatabase();
        int rowsUpdated = db.update(TransactionDbIds.TABLE, contentValues,
                TransactionDbIds.NAME + "=?",
                new String[]{Integer.toString(id)});
        db.close();

        return (rowsUpdated == 1);
    }

    /**
     * Get Transaction object for the named transaction in the database,
     *
     * @param id
     *      id of the transaction to query
     * @return Transaction object representing the named transaction,
     * or null if it could not be queried
     */
    public Transaction getTransaction(final int id)
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data = db.rawQuery("select * from " + TransactionDbIds.TABLE +
                " where " + TransactionDbIds.NAME + "=?", new String[]{String.format("%d", id)});

        Transaction transaction = null;

        if(data.getCount() == 1)
        {
            data.moveToFirst();
            transaction = Transaction.toTransaction(data);
        }

        data.close();
        db.close();

        return transaction;
    }

    /**
     * Returns the number of transactions in the database
     * of the provided type.
     *
     * @param type
     *      transaction type to query, either EXPENSE or
     *      REVENUE
     * @return the number of transactions in the database
     * of the given type
     */
    public int getTransactionCount(final int type)
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor data =  db.rawQuery("SELECT Count(*) FROM " + TransactionDbIds.TABLE +
                " where " + TransactionDbIds.TYPE + "=?", new String[]{Integer.valueOf(type).toString()});

        int numItems = 0;

        if(data.getCount() == 1)
        {
            data.moveToFirst();
            numItems = data.getInt(0);
        }

        data.close();
        db.close();

        return numItems;
    }

    /**
     * Delete a given transaction from the database
     *
     * @param id
     *      id of the transaction to delete
     * @return if the transaction was successfully deleted,
     * false otherwise
     */
    public boolean deleteTransaction(final int id)
    {
        SQLiteDatabase db = getWritableDatabase();
        int rowsDeleted =  db.delete(TransactionDbIds.TABLE,
                TransactionDbIds.NAME + " = ? ",
                new String[]{Integer.valueOf(id).toString()});
        db.close();
        return (rowsDeleted == 1);
    }

    /**
     * @return Cursor pointing to all expense transactions
     * in the database
     */
    public Cursor getExpenses()
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor res =  db.rawQuery("select * from " + TransactionDbIds.TABLE + " where " +
                TransactionDbIds.TYPE + "=" + TransactionDbIds.EXPENSE +
                " ORDER BY " + TransactionDbIds.DATE + " DESC", null);
        return res;
    }

    /**
     * @return Cursor pointing to all revenue transactions
     * in the database
     */
    public Cursor getRevenues()
    {
        SQLiteDatabase db = getReadableDatabase();
        Cursor res =  db.rawQuery("select * from " + TransactionDbIds.TABLE + " where " +
                TransactionDbIds.TYPE + "=" + TransactionDbIds.REVENUE +
                " ORDER BY " + TransactionDbIds.DATE + " DESC", null);
        return res;
    }

    /**
     * Returns a Cursor pointing to all transactions in the database
     * with a receipt. If the provided endDate is not null, further
     * restricts returned transactions to have occurred on or before
     * the given date.
     *
     * @param endDate
     *      date to limit transactions by; if not null will only
     *      returns transactions on or before the given date.
     * @return Cursor pointing to transactions with receipts.
     */
    public Cursor getTransactionsWithReceipts(Long endDate)
    {
        List<String> argList = new ArrayList<>();
        if(endDate != null)
        {
            argList.add(endDate.toString());
        }
        String [] args = argList.toArray(new String[]{});

        SQLiteDatabase db = getReadableDatabase();
        Cursor res =  db.rawQuery("select * from " + TransactionDbIds.TABLE + " where " +
                " LENGTH(" + TransactionDbIds.RECEIPT + ") > 0 " +
                (endDate != null ? " AND " + TransactionDbIds.DATE + "<=? " : ""),
                args);
        return res;
    }
}
