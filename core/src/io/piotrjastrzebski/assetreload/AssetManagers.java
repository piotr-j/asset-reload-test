package io.piotrjastrzebski.assetreload;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.ParticleEffectLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
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
	ObjectMap<String, AssetDescriptor> descriptors = new ObjectMap<>();

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
			Gdx.app.log(TAG, "Skip duplicate reload " + descriptor);
			return;
		}
		reloading.add(descriptor);
		Array<AssetDescriptor> loadAfter = new Array<>();
		if (!descToCurrent.containsKey(descriptor)) {
			descToCurrent.put(descriptor, managerA);
			descToNext.put(descriptor, managerB);
			descriptors.put(descriptor.fileName, descriptor);
		} else {
			AssetManager current = descToCurrent.get(descriptor);
//			AssetManager next = descToNext.get(descriptor);
			// super janky!
			if (descriptor.type == ParticleEffect.class) {
				Gdx.app.log(TAG, "Reload ParticleEffect " + descriptor);
//				Gdx.app.log(TAG, "Reloading particle effect");
				Array<String> dependencies = current.getDependencies(descriptor.fileName);
				if (dependencies != null) {
					for (String dependency : new Array<>(dependencies)) {
						Class type = current.getAssetType(dependency);
						if (current.isLoaded(dependency))
							current.unload(dependency);
						if (!descriptors.containsKey(dependency)) {
							descriptors.put(dependency, new AssetDescriptor(dependency, type));
						}
						Gdx.app.log(TAG, "PRELOAD " + descriptors.get(dependency));
						load(descriptors.get(dependency));
					}
				}
			} else if (descriptor.type == TextureAtlas.class) {
				Gdx.app.log(TAG, "Reload TextureAtlas " + descriptor);
				// how do we know what depends on this? Go over all assets? kinda janky but eh
				for (String name : current.getAssetNames()) {
					Class assetType = current.getAssetType(name);
					Array<String> dependencies = current.getDependencies(name);
					if (dependencies != null) {
						for (String dependency : new Array<>(dependencies)) {
							if (dependency.equals(descriptor.fileName)) {
								if (!descriptors.containsKey(name)) {
									descriptors.put(name, new AssetDescriptor(name, assetType));
								}
								loadAfter.add(descriptors.get(name));
							}
						}
					}
				}
			}
		}
		descToNext.get(descriptor).load(descriptor);
		for (AssetDescriptor desc : loadAfter) {
			Gdx.app.log(TAG, "POSTLOAD " + desc);
			load(desc);
		}
		loadAfter.clear();
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
				unloadQueue.add(new UnloadTask(current, desc.fileName));
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
					if (task.manager.isLoaded(task.fileName)) {
						task.manager.unload(task.fileName);
					}
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
