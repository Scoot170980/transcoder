package protect.budgetwatch;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class BudgetViewActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.budget_view_activity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        setTitle(R.string.addBudgetTitle);

        Button saveButton = (Button)findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                EditText budgetNameField = (EditText) findViewById(R.id.budgetName);
                String budgetName = budgetNameField.getText().toString();
                EditText valueField = (EditText) findViewById(R.id.value);
                String valueStr = valueField.getText().toString();

                int value;

                try
                {
                    value = Integer.parseInt(valueStr);
                }
                catch (NumberFormatException e)
                {
                    value = Integer.MIN_VALUE;
                }

                if (budgetName.length() > 0 && value >= 0)
                {
                    DBHelper db = new DBHelper(BudgetViewActivity.this);

                    db.insertBudget(budgetName, value);

                    Toast.makeText(BudgetViewActivity.this, "Budget account: " + budgetName,
                            Toast.LENGTH_SHORT).show();

                    finish();
                }
                else
                {
                    int messageId = R.string.budgetTypeMissing;
                    if (budgetName.isEmpty() == false)
                    {
                        messageId = R.string.budgetValueMissing;
                    }

                    Snackbar.make(v, messageId, Snackbar.LENGTH_LONG).show();
                }
            }
        });

        Button cancelButton = (Button)findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });
    }
}
