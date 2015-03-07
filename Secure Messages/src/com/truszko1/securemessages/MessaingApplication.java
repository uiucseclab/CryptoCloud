package com.truszko1.securemessages;

import android.app.Application;
import com.parse.Parse;
import com.parse.ParseInstallation;
public class MessaingApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		Parse.initialize(this, "bgzOxpvbbNUFr9dnFr1JuN0Y2hKbysJ3z3Le4bnF",
				"oO4FyR59UBOTVzVCv79WANzPzFrOQvzzerN21sSw");
		ParseInstallation.getCurrentInstallation().saveInBackground();
	}
}
