package com.truszko1.cs460.cryptocloud.app;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;


public class LoginActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        final EditText password = (EditText) findViewById(R.id.password);
        final Button unlockButton = (Button) findViewById(R.id.submitPassword);
        final ImageView animImageView = (ImageView) findViewById(R.id.ivAnimation);
        animImageView.setVisibility(View.GONE);

        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animImageView.setBackgroundResource(R.drawable.anim);
                animImageView.post(new Runnable() {
                    @Override
                    public void run() {
                        password.setVisibility(View.GONE);
                        unlockButton.setVisibility(View.GONE);
                        animImageView.setVisibility(View.VISIBLE);
                        AnimationDrawable frameAnimation =
                                (AnimationDrawable) animImageView.getBackground();
                        frameAnimation.setOneShot(true);
                        frameAnimation.start();
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                intent.putExtra("password", password.getText().toString());
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                LoginActivity.this.startActivity(intent);
                            }
                        }, 1200);
                    }
                });
            }
        });


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
