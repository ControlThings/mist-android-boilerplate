package fi.ct.mist.boilerplate;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class AuthActivity extends AppCompatActivity {

    private wish.Identity userIdentity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        Intent intent = getIntent();
        userIdentity = (wish.Identity) intent.getSerializableExtra(MainActivity.USER_IDENTITY);

        TextView textView = findViewById(R.id.textView);
        textView.setText(userIdentity.getAlias());
    }
}
