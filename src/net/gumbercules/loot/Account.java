package net.gumbercules.loot;

import java.util.ArrayList;
import java.util.Date;

import android.database.*;
import android.database.sqlite.*;

public class Account
{
	public static final String KEY_NAME	= "name";
	public static final String KEY_BAL	= "balance";
	public static final String KEY_ID	= "a_id";
	
	private int id;
	String name;
	double initialBalance;
	int priority;
	private static int currentAccount;
	private double actual_balance;
	private double posted_balance;
	private double budget_balance;
	
	public Account()
	{
		this.id = -1;
	}
	
	public Account(String name, double initialBalance)
	{
		this.id = -1;
		this.name = name;
		this.initialBalance = initialBalance;
		this.priority = 1;
	}
	
	public int id()
	{
		return this.id;
	}
	
	public int write()
	{
		if (this.id == -1)
			return newAccount();
		else
			return updateAccount();
	}
	
	private int newAccount()
	{
		// insert the new row into the database
		String insert = "insert into accounts (name,balance,timestamp,priority) " +
				"values (?,?,strftime('%s','now'),?)";
		Object[] bindArgs = {this.name, new Double(this.initialBalance), new Long(this.priority)};
		SQLiteDatabase lootDB = Database.getDatabase();
		try
		{
			lootDB.beginTransaction();
			lootDB.execSQL(insert, bindArgs);
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return -1;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		// get the id of that row
		Cursor cur = lootDB.rawQuery("select max(id) from accounts", null);
		if (!cur.moveToFirst())
		{
			this.id = -1;
		}
		else
			this.id = cur.getInt(0);
		
		cur.close();
		return this.id;
	}
	
	private int updateAccount()
	{
		// update the row in the database
		String update = "update accounts set name = ?, balance = ?, priority = ?, " +
						"timestamp = strftime('%s','now') where id = ?";
		Object[] bindArgs = {this.name, new Double(this.initialBalance), new Long(this.priority),
				new Integer(this.id)};
		SQLiteDatabase lootDB = Database.getDatabase();
		try
		{
			lootDB.beginTransaction();
			lootDB.execSQL(update, bindArgs);
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return -1;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		return this.id;
	}
	
	public boolean erase()
	{
		// mark the row as 'purged' in the database, so it is still recoverable later
		String del = "update accounts set name = name || ' - Deleted ' || strftime('%s','now'), " +
				"purged = 1, timestamp = strftime('%s','now') where id = ?";
		Object[] bindArgs = {new Integer(this.id)};
		SQLiteDatabase lootDB = Database.getDatabase();
		lootDB.beginTransaction();
		
		try
		{
			lootDB.execSQL(del, bindArgs);
			if (!removeLinks())
				throw new SQLException();
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return false;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		return true;
	}
	
	private boolean removeLinks()
	{
		// remove any transfer links with transactions associated with this account
		SQLiteDatabase lootDB = Database.getDatabase();
		Cursor cur = lootDB.rawQuery("select id from transactions, transfers where id = trans_id1 " +
				" or id = trans_id2 order by id", null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return true;
		}
		
		int[] ids = new int[cur.getCount()];
		int i = 0;
		
		do
		{
			ids[i++] = cur.getInt(0);
		} while (cur.moveToNext());
		cur.close();
		
		if (ids.length == 0)
			return true;
		
		String id_list = "(";
		for (int id : ids)
			id_list += id + ",";
		id_list += "0)";	// no transfer will have 0 as a trans_id
		
		lootDB.beginTransaction();
		
		try
		{
			lootDB.execSQL("delete from transfers where trans_id1 in " + id_list + 
					" or trans_id2 in " + id_list);
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return false;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		return true;
	}
	
	private Double calculateBalance(String clause)
	{
		SQLiteDatabase lootDB = Database.getDatabase();
		String[] sArgs = {Integer.toString(this.id)};
		Cursor cur = lootDB.rawQuery("select sum(amount) from transactions where " + clause, sArgs);
		
		Double bal = null;
		if (cur.moveToFirst())
		{
			bal = cur.getDouble(0);
		}
		cur.close();
		
		return bal;
	}
	
	public double getActualBalance()
	{
		return this.actual_balance;
	}
	
	public Double calculateActualBalance()
	{
		Double bal = calculateBalance("account = ? and purged = 0 and budget = 0");
		if (bal != null)
		{
			this.actual_balance = bal + this.initialBalance;
			return this.actual_balance;
		}
		return bal;
	}
	
	public double getPostedBalance()
	{
		return this.posted_balance;
	}
	
	public Double calculatePostedBalance()
	{
		Double bal = calculateBalance("account = ? and posted = 1 and purged = 0");
		if (bal != null)
		{
			this.posted_balance = bal + this.initialBalance;
			return this.posted_balance;
		}
		return bal;
	}
	
	public double getBudgetBalance()
	{
		return this.budget_balance;
	}
	
	public Double calculateBudgetBalance()
	{
		Double bal = calculateBalance("account = ? and purged = 0");
		if (bal != null)
		{
			this.budget_balance = bal + this.initialBalance;
			return this.budget_balance;
		}
		return bal;
	}
	
	public boolean loadById(int id)
	{
		return loadById(id, false);
	}
	
	public boolean loadById(int id, boolean get_purged)
	{
		SQLiteDatabase lootDB;
		try
		{
			lootDB = Database.getDatabase();
		}
		catch (SQLException e)
		{
			return false;
		}
		
		int purged = 0;
		if (get_purged)
			purged = 1;
		
		String[] columns = {"id", "name", "balance", "priority"};
		String[] sArgs = {Integer.toString(id), Integer.toString(purged)};
		Cursor cur = lootDB.query("accounts", columns, "id = ? and purged = ?", sArgs,
				null, null, null, "1");
		if (!cur.moveToFirst())
		{
			cur.close();
			return false;
		}
		
		this.id = cur.getInt(0);
		this.name = cur.getString(1);
		this.initialBalance = cur.getDouble(2);
		this.priority = cur.getInt(3);
		cur.close();
		
		return true;
	}
	
	public static int getCurrentAccountNum()
	{
		return currentAccount;
	}
	
	public void setCurrentAccountNum()
	{
		currentAccount = this.id;
	}
	
	public static String[] getAccountNames()
	{
		SQLiteDatabase lootDB;
		try
		{
			lootDB = Database.getDatabase();
		}
		catch (SQLException e)
		{
			return null;
		}
		
		String[] columns = {"name"};
		Cursor cur = lootDB.query("accounts", columns, "purged = 0", null, null, null, "priority, id");
		ArrayList<String> accounts = new ArrayList<String>();
		
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		do
		{
			accounts.add(cur.getString(0));
		} while (cur.moveToNext());
		cur.close();
		
		String[] ret = new String[accounts.size()];
		for (int i = 0; i < ret.length; ++i)
			ret[i] = accounts.get(i);
		
		return ret;
	}
	
	public static int[] getAccountIds()
	{
		SQLiteDatabase lootDB;
		try
		{
			lootDB = Database.getDatabase();
		}
		catch (SQLException e)
		{
			return null;
		}
		
		String[] columns = {"id"};
		Cursor cur = lootDB.query("accounts", columns, "purged = 0", null, null, null, "priority, id");
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		ArrayList<Integer> ids = new ArrayList<Integer>();
		
		do
		{
			ids.add(cur.getInt(0));
		} while (cur.moveToNext());
		cur.close();
		
		// convert the Integer ArrayList to int[]
		int[] acc_ids = new int[ids.size()];
		for (int i = 0; i < ids.size(); ++i)
		{
			acc_ids[i] = ids.get(i).intValue();
		}
		
		return acc_ids;
	}
	
	public static int[] getDeletedAccountIds()
	{
		SQLiteDatabase lootDB = Database.getDatabase();
		
		String[] columns = {"id"};
		Cursor cur = lootDB.query("accounts", columns, "purged = 1", null, null, null, "priority, id");
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		int[] acc_ids = new int[cur.getCount()];
		int i = 0;
		
		do
			acc_ids[i++] = cur.getInt(0);
		while (cur.moveToNext());
		cur.close();
		
		return acc_ids;
	}
	
	public static Account getAccountByName( String name )
	{
		SQLiteDatabase lootDB;
		try
		{
			lootDB = Database.getDatabase();
		}
		catch (SQLException e)
		{
			return null;
		}
		
		String[] columns = {"id", "name", "balance"};
		String[] sArgs = {name};
		Cursor cur = lootDB.query("accounts", columns, "name = ? and purged = 0", sArgs,
				null, null, null, "1");
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		Account acct = new Account();
		acct.id = cur.getInt(0);
		acct.name = cur.getString(1);
		acct.initialBalance = cur.getDouble(2);
		cur.close();
		
		return acct;
	}
	
	public static Account getAccountById( int id )
	{
		Account acct = new Account();
		acct.loadById(id);
		return acct;
	}

	public int getNextCheckNum()
	{
		SQLiteDatabase lootDB = Database.getDatabase();
		Cursor cur = lootDB.rawQuery("select max(check_num) from transactions where account = " + this.id, null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return -1;
		}
		
		int check_num = cur.getInt(0);
		if (check_num >= 0)
			check_num += 1;
		cur.close();
		
		return check_num;
	}
	
	public int[] getTransactionIds()
	{
		SQLiteDatabase lootDB;
		try
		{
			lootDB = Database.getDatabase();
		}
		catch (SQLException e)
		{
			return null;
		}
		
		Cursor cur = lootDB.rawQuery("select id from transactions where account = " + this.id + 
				" and purged = 0 order by id asc", null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		int[] ids = new int[cur.getCount()];
		int i = 0;
		
		do
		{
			ids[i++] = cur.getInt(0);
		} while (cur.moveToNext());
		cur.close();
		
		return ids;
	}
	
	public int[] purgeTransactions(Date through)
	{
		SQLiteDatabase lootDB = Database.getDatabase();
		long time = through.getTime();
		
		// find the ids of the transactions
		Cursor cur = lootDB.rawQuery("select id from transactions where posted = 1 " +
				"and date <= " + time + " and account = " + this.id, null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		int[] ids = new int[cur.getCount()];
		int i = 0;
		
		do
		{
			ids[i++] = cur.getInt(0);
		} while (cur.moveToNext());
		cur.close();
		
		// find the sum of the soon-to-be purged transactions
		cur = lootDB.rawQuery("select sum(amount) from transactions " +
				"where posted = 1 and date <= " + time + 
				" and account = " + this.id + " and purged = 0", null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		// update the initial account balance to reflect these changes
		this.initialBalance += cur.getDouble(0);
		cur.close();
		
		lootDB.beginTransaction();
		if (write() == -1)
		{
			lootDB.endTransaction();
			return null;
		}
		
		// purge the posted transactions
		try
		{
			lootDB.execSQL("update transactions set purged = 1, timestamp = strftime('%s','now')" +
					" where posted = 1 and date <= " + time + " and account = " + this.id);
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return null;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		return ids;
	}
	
	private int[] getPurgedTransactions(Date through, boolean gte)
	{
		long time = through.getTime();
		SQLiteDatabase lootDB = Database.getDatabase();
		
		String op = " <= ";
		if (gte)
			op = " >= ";
		
		// get the ids of all the purged transactions after 'through'
		Cursor cur = lootDB.rawQuery("select id from transactions where purged = 1 and " +
				"date" + op + time + " and account = " + this.id, null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		int[] ids = new int[cur.getCount()];
		int i = 0;
		
		do
		{
			ids[i++] = cur.getInt(0);
		} while (cur.moveToNext());
		
		cur.close();

		return ids;
	}
	
	public boolean deletePurgedTransactions(Date through)
	{
		int[] ids = getPurgedTransactions(through, false);
		if (ids == null)
			return false;
		
		SQLiteDatabase lootDB = Database.getDatabase();
		lootDB.beginTransaction();
		
		Transaction trans;
		for (int id : ids)
		{
			trans = Transaction.getTransactionById(id, true);
			if (trans == null || !trans.erase())
			{
				lootDB.endTransaction();
				return false;
			}
		}
		
		lootDB.setTransactionSuccessful();
		lootDB.endTransaction();
		
		return true;
	}
	
	public int[] restorePurgedTransactions(Date through)
	{
		int[] ids = getPurgedTransactions(through, true);
		if (ids == null)
			return null;
		
		long time = through.getTime();
		SQLiteDatabase lootDB = Database.getDatabase();
		
		Cursor cur = lootDB.rawQuery("select sum(amount) from transactions where purged = 1 and " +
				"date >= " + time + " and account = " + this.id, null);
		if (!cur.moveToFirst())
		{
			cur.close();
			return null;
		}
		
		this.initialBalance -= cur.getDouble(0);
		cur.close();
		
		lootDB.beginTransaction();
		
		// update the initial account balance to remove the sum of these transactions
		if (write() == -1)
		{
			lootDB.endTransaction();
			return null;
		}
		
		try
		{
			lootDB.execSQL("update transactions set purged = 0, timestamp = strftime('%s','now')" +
					" where purged = 1 and date >= " + time + " and account = " + this.id);
			lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return null;
		}
		finally
		{
			lootDB.endTransaction();
		}

		return ids;
	}
	
	public static boolean clearDeletedAccount(int id)
	{
		SQLiteDatabase lootDB = Database.getDatabase();
		
		Cursor cur = lootDB.query("transactions", new String[]{"id"}, "account = " + id,
				null, null, null, null);
		int trans_len = 0;
		int repeat_len = 0;
		int[] trans_ids = null;
		if (cur.moveToFirst())
		{
			trans_len = cur.getCount();
			trans_ids = new int[trans_len];
			int i = 0;
			
			do
				trans_ids[i++] = cur.getInt(0);
			while (cur.moveToNext());
		}
		cur.close();
		
		String trans_id_group = "(";
		String repeat_id_group = "(";
		
		// if there are no transactions, we don't have to worry about deleting
		// any other rows that reference the transaction
		if (trans_len != 0)
		{
			int i = 0;
			trans_id_group = "(" + trans_ids[i++];
			for (; i < trans_len; ++i)
				trans_id_group += "," + trans_ids[i];
			trans_id_group += ")";
			
			cur = lootDB.query("repeat_transactions", new String[]{"repeat_id"},
					"trans_id in " + trans_id_group, null, null, null, null);
			if (cur.moveToFirst())
			{
				repeat_len = cur.getCount();
				repeat_id_group = "(" + cur.getInt(0);

				while (cur.moveToNext())
					repeat_id_group += "," + cur.getInt(0);
				repeat_id_group += ")";
			}
			cur.close();
		}
		
		lootDB.beginTransaction();
		
		try
		{
			if (repeat_len > 0)
			{
				lootDB.delete("repeat_pattern", "id in " + repeat_id_group, null);
				lootDB.delete("repeat_transactions", "repeat_id in " + repeat_id_group, null);
			}
			if (trans_len > 0)
			{
				lootDB.delete("transactions", "id in " + trans_id_group, null);
				lootDB.delete("tags", "trans_id in " + trans_id_group, null);
			}

			int removed = lootDB.delete("accounts", "id = " + id + " and purged = 1", null);
			if (removed > 0)
				lootDB.setTransactionSuccessful();
		}
		catch (SQLException e)
		{
			return false;
		}
		finally
		{
			lootDB.endTransaction();
		}
		
		return true;
	}
	
	public static boolean restoreDeletedAccount(int id)
	{
		return false;
	}
}
