package io.piotrjastrzebski.assetreload;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		initialize(new AssetsGame(new AndroidPlatform()), config);
	}

	private static class AndroidPlatform implements Platform {
		@Override public void start (Listener listener, boolean watch) {

		}

		@Override public void processAssets (String type) {

		}

		@Override public void processAssetsAsync (String particle) {

		}

		@Override public void dispose () {

		}
	}
}
