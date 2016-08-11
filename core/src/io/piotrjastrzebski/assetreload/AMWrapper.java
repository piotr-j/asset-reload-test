package io.piotrjastrzebski.assetreload;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Created by EvilEntity on 03/08/2016.
 */
public class AMWrapper {
	private static final String TAG = AMWrapper.class.getSimpleName();
	AssetManager managerA;
	AssetManager managerB;
	ObjectMap<AssetDescriptor, AssetManager> descToCurrent = new ObjectMap<>();
	ObjectMap<AssetDescriptor, AssetManager> descToNext = new ObjectMap<>();
	Array<AssetDescriptor> reloading = new Array<>();
	public AMWrapper () {
		managerA = new AssetManager();
		managerB = new AssetManager();
	}

	public <T> void reload (AssetDescriptor<T> descriptor) {
		if (reloading.contains(descriptor, true)) {
			Gdx.app.error(TAG, "Duplicate reloading!");
			return;
		}
		if (!descToCurrent.containsKey(descriptor)) {
			descToCurrent.put(descriptor, managerA);
			descToNext.put(descriptor, managerB);
		}
		descToNext.get(descriptor).load(descriptor);
		reloading.add(descriptor);
	}

	public boolean update () {
		if (managerA.update()) {
			if (managerB.update()){
				for (AssetDescriptor desc : reloading) {
					AssetManager current = descToCurrent.get(desc);
					AssetManager next = descToNext.get(desc);
					if (current.isLoaded(desc.fileName))
						current.unload(desc.fileName);
					descToCurrent.put(desc, next);
					descToNext.put(desc, current);
				}
				reloading.clear();
				return true;
			}
		}
		return false;
	}

	public <T> T get (AssetDescriptor<T> descriptor) {
		return descToCurrent.get(descriptor).get(descriptor);
	}

	public void dispose () {
		managerA.dispose();
		managerB.dispose();
	}
}
