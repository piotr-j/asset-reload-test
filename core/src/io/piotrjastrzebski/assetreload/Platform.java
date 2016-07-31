package io.piotrjastrzebski.assetreload;

/**
 * Created by EvilEntity on 31/07/2016.
 */
public interface Platform {
	void start(Listener listener, boolean watch);
	void processAssets();
	void processAssetsAsync();

	void dispose();
	interface Listener {
		void assetsProcessed();
	}
}
