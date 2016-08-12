package io.piotrjastrzebski.assetreload;

import com.artemis.*;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.ParticleEffectLoader;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class AssetsGame extends ApplicationAdapter implements InputProcessor {
	private static final String TAG = AssetsGame.class.getSimpleName();
	SpriteBatch batch;
	TextureRegion img;

	private Platform platform;

	World world;

	public AssetsGame (Platform platform) {
		this.platform = platform;
	}

	@Override
	public void create () {
		batch = new SpriteBatch();

		Gdx.input.setInputProcessor(this);

		WorldConfiguration cfg = new WorldConfiguration();
		cfg.register(batch);
		cfg.setSystem(new Assets(platform));
		cfg.setSystem(Renderer.class);

		world = new World(cfg);

		EntityEdit edit = world.createEntity().edit();
		Assets.Asset asset = edit.create(Assets.Asset.class);
		asset.path = "badlogic";
		asset.type = TextureRegion.class;
		Renderer.Renderable renderable = edit.create(Renderer.Renderable.class);
		renderable.x = 400 - 128;
		renderable.y = 300 - 128;

		edit = world.createEntity().edit();
		asset = edit.create(Assets.Asset.class);
		asset.path = "tree";
		asset.type = TextureRegion.class;
		renderable = edit.create(Renderer.Renderable.class);
		renderable.x = 100;
		renderable.y = 100;

		edit = world.createEntity().edit();
		asset = edit.create(Assets.Asset.class);
		asset.path = "particles/test.p";
		asset.type = ParticleEffect.class;
		renderable = edit.create(Renderer.Renderable.class);
		renderable.x = 600;
		renderable.y = 450;
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(.5f, .5f, .5f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		world.setDelta(1f/60f);
		world.process();
	}
	
	@Override
	public void dispose () {
		batch.dispose();
		world.dispose();
		platform.dispose();
	}

	public static class Assets extends BaseEntitySystem implements Platform.Listener {
		protected ComponentMapper<Asset> mAsset;
		protected ComponentMapper<Renderer.Renderable> mRenderable;
		AssetManagers assetManager;
		boolean loadInProgress = false;
		AssetDescriptor<ParticleEffect> particleDesc;
		ParticleEffect effect;

		public Assets (Platform platform) {
			super(Aspect.all(Asset.class));
			platform.start(this, true);
			platform.processAssetsAsync();
			// this works, but we are leaking assets doing this
			assetManager = new AssetManagers();
			ParticleEffectLoader.ParticleEffectParameter params = new ParticleEffectLoader.ParticleEffectParameter();
			params.atlasFile = ATLAS_NAME;
			particleDesc = new AssetDescriptor<>("particles/test.p", ParticleEffect.class, params);
		}

		public void updateAllAssets() {
			IntBag entities = getSubscription().getEntities();
			for (int id = 0; id < entities.size(); id++) {
				updateAsset(entities.get(id));
			}
		}

		@Override protected void inserted (int entityId) {
			updateAsset(entityId);
		}

		private void updateAsset (int entityId) {
			if (atlas == null) return;
			Asset asset = mAsset.get(entityId);
			Renderer.Renderable renderable = mRenderable.create(entityId);
			renderable.type = asset.type;
			if (asset.type == ParticleEffect.class) {
				renderable.effect = new ParticleEffect(effect);
			} else if (asset.type == TextureRegion.class) {
				renderable.region = atlas.findRegion(asset.path);
			}
		}

		private String ATLAS_NAME = "pack/assets.atlas";
		private AssetDescriptor<TextureAtlas> atlasDescriptor = new AssetDescriptor<>(ATLAS_NAME, TextureAtlas.class);

		@Override protected void processSystem () {
			if (loadInProgress) {
				if (assetManager.update()) {
					loadInProgress = false;
				}
			}
		}

		private TextureAtlas atlas;
		@Override public void assetsProcessed () {
			if (loadInProgress) throw new AssertionError("Asset reload before old ones loaded!");
			loadInProgress = true;
			assetManager.load(atlasDescriptor);
			assetManager.load(particleDesc);
			assetManager.listener = new AssetManagers.Listener() {
				@Override public void onReload (Array<AssetDescriptor> reloaded) {
					atlas = assetManager.get(atlasDescriptor);
					effect = assetManager.get(particleDesc);
					ParticleEmitter first = effect.getEmitters().first();
					first.setContinuous(true);
					first.getScale().setHigh(24, 48);
					first.getScale().setLow(8, 16);
					first.getVelocity().setHigh(64, 128);
					first.getVelocity().setLow(48, 96);
					first.setAdditive(true);
					updateAllAssets();
				}
			};
		}

		public static class Asset extends Component {
			public String path;
			public Class type;
		}

		@Override protected void dispose () {
			assetManager.dispose();
//			descToNext.dispose();
		}
	}

	public static class Renderer extends IteratingSystem {
		@Wire SpriteBatch batch;
		protected ComponentMapper<Renderable> mRenderable;
		public Renderer () {
			super(Aspect.all(Renderable.class));
		}

		@Override protected void begin () {
			batch.begin();
		}

		@Override protected void process (int entityId) {
			Renderable r = mRenderable.get(entityId);
			r.angle += world.delta * 45;
			r.progress += world.delta * r.dir;
			if (r.progress > 1) r.dir = -1;
			if (r.progress < 0) r.dir = 1;
			float x = Interpolation.exp5.apply(r.x - 75, r.x + 75, r.progress);
			float y = Interpolation.exp5.apply(r.y + 75, r.y - 75, r.progress);
			if (r.type == TextureRegion.class) {
				batch.draw(r.region,
					x, y,
					r.region.getRegionWidth()/2, r.region.getRegionHeight()/2,
					r.region.getRegionWidth(), r.region.getRegionHeight(),
					1, 1, r.angle);
			} else if (r.type == ParticleEffect.class) {
				batch.end();
				batch.begin();
				r.effect.setPosition(x, y);
				r.effect.draw(batch, world.delta);
			}
		}

		@Override protected void end () {
			batch.end();
		}

		public static class Renderable extends Component {
			public Class type;
			public TextureRegion region;
			public ParticleEffect effect;
			public float progress;
			public float x;
			public float y;
			public float angle;
			public int dir = 1;
		}
	}

	@Override public boolean keyDown (int keycode) {
		return false;
	}

	@Override public boolean keyUp (int keycode) {
		return false;
	}

	@Override public boolean keyTyped (char character) {
		return false;
	}

	@Override public boolean touchDown (int screenX, int screenY, int pointer, int button) {
		return false;
	}

	@Override public boolean touchUp (int screenX, int screenY, int pointer, int button) {
		return false;
	}

	@Override public boolean touchDragged (int screenX, int screenY, int pointer) {
		return false;
	}

	@Override public boolean mouseMoved (int screenX, int screenY) {
		return false;
	}

	@Override public boolean scrolled (int amount) {
		return false;
	}
}
