package io.piotrjastrzebski.assetreload;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Created by PiotrJ on 09/08/16.
 */
public class AssetManagers {
	private static final String TAG = AssetManagers.class.getSimpleName();
	AssetManager managerA;
	AssetManager managerB;
	ObjectMap<AssetDescriptor, AssetManager> descToCurrent = new ObjectMap<>();
	ObjectMap<AssetDescriptor, AssetManager> descToNext = new ObjectMap<>();
	Array<AssetDescriptor> reloading = new Array<>();

	public AssetManagers () {
		this(new InternalFileHandleResolver());
	}

	public AssetManagers (FileHandleResolver resolver) {
		managerA = new AssetManager(resolver);
		managerB = new AssetManager(resolver);
		managerA.getLogger().setLevel(Logger.DEBUG);
		managerB.getLogger().setLevel(Logger.DEBUG);
	}

	public <T> void load (String fileName, Class<T> type) {
		load(fileName, type, null);
	}

	public <T> void load (String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
		managerA.load(fileName, type, parameter);
	}

	public <T> T get (String fileName, Class<T> type) {
		return managerA.get(fileName, type);
	}

	public <T> void load (AssetDescriptor<T> descriptor) {
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

	private static class UnloadTask {
		AssetManager manager;
		String fileName;

		UnloadTask (AssetManager manager, String fileName) {
			this.manager = manager;
			this.fileName = fileName;
		}
	}

	public Listener listener;

	private Array<UnloadTask> unloadQueue = new Array<>();
	public boolean update () {
		if (managerA.update() && managerB.update()) {
			for (AssetDescriptor desc : reloading) {
				AssetManager current = descToCurrent.get(desc);
				AssetManager next = descToNext.get(desc);
				if (current.isLoaded(desc.fileName)) {
					unloadQueue.add(new UnloadTask(current, desc.fileName));
				}
				descToCurrent.put(desc, next);
				descToNext.put(desc, current);
			}
			if (listener != null) {
				// we need to  pass in what was reloaded
				listener.onReload(reloading);
			}
			reloading.clear();
			if (unloadQueue.size > 0) {
				for (UnloadTask task : unloadQueue) {
					task.manager.unload(task.fileName);
				}
				unloadQueue.clear();
			}
			return true;
		}
		return false;
	}

	public <T> T get (AssetDescriptor<T> descriptor) {
		return descToCurrent.get(descriptor).get(descriptor);
	}

	public <T, P extends AssetLoaderParameters<T>> void setLoader (Class<T> type, AssetLoader<T, P> loader) {
		managerA.setLoader(type, loader);
		managerB.setLoader(type, loader);
	}

	public <T, P extends AssetLoaderParameters<T>> void setLoader (Class<T> type, String suffix, AssetLoader<T, P> loader) {
		managerA.setLoader(type, suffix, loader);
		managerB.setLoader(type, suffix, loader);
	}

	public void dispose () {
		managerA.dispose();
		managerB.dispose();
	}

	public interface Listener {
		void onReload (Array<AssetDescriptor> reloading);
	}
}
