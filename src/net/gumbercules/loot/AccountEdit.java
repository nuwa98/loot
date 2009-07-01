package net.gumbercules.loot;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class AccountEdit extends Activity
{
	private EditText mNameEdit;
	private EditText mBalanceEdit;
	private EditText mPriorityEdit;
	private int mRowId;
	private int mFinishIntent;
	private CurrencyWatcher mCurrencyWatcher;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account_edit);
		
		// only assume the finish intent is OK if we explicitly set it
		mFinishIntent = RESULT_CANCELED;
		
		mNameEdit = (EditText)findViewById(R.id.NameEdit);
		mBalanceEdit = (EditText)findViewById(R.id.BalanceEdit);
		mCurrencyWatcher = new CurrencyWatcher();
		mBalanceEdit.addTextChangedListener(mCurrencyWatcher);
		mPriorityEdit = (EditText)findViewById(R.id.PriorityEdit);
		mPriorityEdit.setKeyListener(new DigitsKeyListener());
		Button SaveButton = (Button)findViewById(R.id.SaveButton);
		Button CancelButton = (Button)findViewById(R.id.CancelButton);
		
		mRowId = savedInstanceState != null ? savedInstanceState.getInt(Account.KEY_ID) : 0;
		if (mRowId == 0)
		{
			Bundle extras = getIntent().getExtras();
			mRowId = extras != null ? extras.getInt(Account.KEY_ID) : 0;
		}
		
		SaveButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View view)
			{
				mFinishIntent = RESULT_OK;
				setResult(mFinishIntent);
				finish();
			}
		});
		
		CancelButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View view)
			{
				setResult(mFinishIntent);
				finish();
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt(Account.KEY_ID, mRowId);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		saveState();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		populateFields();
	}
	
	private void populateFields()
	{
		if (mRowId != 0)
		{
			Account acct = Account.getAccountById(mRowId);
			
			if (mNameEdit != null)
				mNameEdit.setText(acct.name);
			if (mBalanceEdit != null)
			{
				NumberFormat nf = NumberFormat.getCurrencyInstance();
				String num = nf.format(acct.initialBalance);
				StringBuilder sb = new StringBuilder();
				sb.append(mCurrencyWatcher.getAcceptedChars());
				String accepted = "[^" + sb.toString() + "]";
				num = num.replaceAll(accepted, "");
				
				mBalanceEdit.setText(num);
			}
			if (mPriorityEdit != null)
			{
				mPriorityEdit.setText(Integer.toString(acct.priority));
			}
		}
	}
	
	private void saveState()
	{
		if (mFinishIntent == RESULT_CANCELED)
			return;
		
		Account acct;
		if (mRowId != 0)
			acct = Account.getAccountById(mRowId);
		else
			acct = new Account();
		
		acct.name = mNameEdit.getText().toString();
		String balText = mBalanceEdit.getText().toString();
		String priText = mPriorityEdit.getText().toString();
		
		if (acct.name == "" || balText == "")
		{
			setResult(RESULT_CANCELED);
			return;
		}
		
		try
		{
			DecimalFormatSymbols dfs = new DecimalFormatSymbols();
			char sep = dfs.getMonetaryDecimalSeparator();
			
			if (sep != '.')
				balText = balText.replaceAll(String.valueOf(sep), ".");
			acct.initialBalance = new Double(balText);
		}
		catch (NumberFormatException e)
		{
			// if there is no data (or bad data) in the field, set it to zero
			acct.initialBalance = 0.0;
		}
		
		try
		{
			acct.priority = new Integer(priText);
		}
		catch (NumberFormatException e)
		{
			acct.priority = 1;
		}
		
		int id = acct.write();

		if (id != -1)
			mRowId = id;
	}
}
