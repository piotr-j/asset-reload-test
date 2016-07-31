package io.piotrjastrzebski.assetreload;

import com.artemis.*;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

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
		edit = world.createEntity().edit();
		asset = edit.create(Assets.Asset.class);
		asset.path = "tree";
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
		AssetManager current, next, manager1, manager2;
		boolean loadInProgress = false;
		public Assets (Platform platform) {
			super(Aspect.all(Asset.class));
			platform.start(this, true);
			platform.processAssetsAsync();
			manager1 = current = new AssetManager();
			manager2 = next = new AssetManager();
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
			renderable.region = atlas.findRegion(asset.path);
		}

		private String ATLAS_NAME = "pack/assets.atlas";
		@Override protected void processSystem () {
			if (loadInProgress) {
				if (next.update()) {
					loadInProgress = false;
					AssetManager old = current;
					old.clear();
					current = next;
					next = old;
					atlas = current.get(ATLAS_NAME, TextureAtlas.class);
					updateAllAssets();
				}
			}
		}

		private TextureAtlas atlas;
		@Override public void assetsProcessed () {
			if (loadInProgress) throw new AssertionError("Asset reload before old ones loaded!");
			loadInProgress = true;
			next.load(ATLAS_NAME, TextureAtlas.class);
		}

		public static class Asset extends Component {
			public String path;
		}

		@Override protected void dispose () {
			current.dispose();
			next.dispose();
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
			batch.draw(r.region,
				x, y,
				r.region.getRegionWidth()/2, r.region.getRegionHeight()/2,
				r.region.getRegionWidth(), r.region.getRegionHeight(),
				1, 1, r.angle);
		}

		@Override protected void end () {
			batch.end();
		}

		public static class Renderable extends Component {
			public TextureRegion region;
			public float progress;
			public float x = MathUtils.random(50, 300);
			public float y = MathUtils.random(50, 300);
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
