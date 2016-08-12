package io.piotrjastrzebski.assetreload.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import com.badlogic.gdx.utils.async.AsyncTask;
import io.piotrjastrzebski.assetreload.AssetsGame;
import io.piotrjastrzebski.assetreload.Platform;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 600;
		new LwjglApplication(new AssetsGame(new DesktopPlatform()), config);
	}

	private static class DesktopPlatform implements Platform {
		private static final String TAG = DesktopPlatform.class.getSimpleName();
		private Listener listener;
		private AsyncExecutor executor;
		private AtomicBoolean isProcessingAssets = new AtomicBoolean(false);
		private Thread watcher;

		@Override public void dispose () {
			if (watcher != null) watcher.interrupt();
			executor.dispose();
		}

		@Override public void start (final Listener listener, final boolean watch) {
			this.listener = listener;
			executor = new AsyncExecutor(1);

			if (watch) {
				watcher = new Thread(new Runnable() {
					@Override public void run () {
						Path rawAssets = Paths.get(System.getProperty("user.dir")).getParent().getParent().resolve("raw-assets");
						FileSystem fs = rawAssets.getFileSystem();
						try {
							WatchService watcher = fs.newWatchService();
							rawAssets.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

							while (true) {
								WatchKey key = watcher.take();
								for (WatchEvent<?> watchEvent : key.pollEvents()) {
									WatchEvent.Kind kind = watchEvent.kind();
									// we don't really care about specific event type, if stuff happens repack all the things
									// note: we get multiple events per change sometimes
									if (ENTRY_CREATE == kind || ENTRY_MODIFY == kind || ENTRY_DELETE == kind) {
										Path context = (Path)watchEvent.context();
										if (context.toAbsolutePath().toString().contains("atlas")) {
											processAssetsAsync("atlas");
										} else {
											processAssetsAsync("particle");
										}
									}
								}

								if (!key.reset()) {
									Gdx.app.error(TAG, "Watcher stopped?");
									break;
								}
							}
						} catch (IOException ex) {
							ex.printStackTrace();
						} catch (InterruptedException ex) {
							Gdx.app.log(TAG, "Watcher thread stopped");
						}
					}
				});
				watcher.start();
			}
		}

		@Override public void processAssetsAsync (final String type) {
			// submit only if there is nothing running
			// not 100% sure this is correct
			// this probably should be cancel previous one, start new
			// but there is no easy way to cancel packing
			if (isProcessingAssets.compareAndSet(false, true)) {
				executor.submit(new AsyncTask<Object>() {
					@Override public Object call () throws Exception {
						processAssets(type);
						isProcessingAssets.set(false);
						return null;
					}
				});
			} else {
				Gdx.app.log(TAG, "Not processing, already in progress");
			}
		}

		@Override public void processAssets (final String type) {
			Gdx.app.log(TAG, "Processing assets...");
			Path cwd = Paths.get(System.getProperty("user.dir"));
			// if we are in android/assets, we will assume that we are running from ide
			if (cwd.getFileName().toString().equals("assets") && cwd.getParent().getFileName().toString().equals("android")) {
				Path assets = cwd.resolve("raw/atlas");
				try {
					deletePath(assets);
					Files.createDirectory(assets);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				Path atlasses = cwd.resolve("pack");
				try {
					deletePath(atlasses);
					Files.createDirectory(atlasses);
				} catch (IOException e) {
					e.printStackTrace();
					Gdx.app.error(TAG, "", e);
					return;
				}
				Path raw = cwd.getParent().getParent().resolve("raw-assets/atlas");
				if (!Files.exists(assets)) {
					Gdx.app.error(TAG, "raw-assets doesn't exists! " + raw);
					return;
				}
				try {
					FileVisitor visitor = new FileVisitor(raw, assets);
					Files.walkFileTree(raw, visitor);
					TexturePacker.process(assets.toAbsolutePath().toString(), atlasses.toAbsolutePath().toString(), "assets");

					assets = cwd.resolve("particles");
					raw = cwd.getParent().getParent().resolve("raw-assets/particles");
					visitor = new FileVisitor(raw, assets);
					Files.walkFileTree(raw, visitor);
				} catch (IOException e) {
					e.printStackTrace();
				}

			} else {
				// TODO make this work?
			}

			Gdx.app.log(TAG, "Processing assets finished");
			Gdx.app.postRunnable(new Runnable() {
				@Override public void run () {
					listener.assetsProcessed(type);
				}
			});
		}

		private void deletePath (Path path) throws IOException {
			if (!Files.exists(path)) return;

			Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
				@Override public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
					if (Files.exists(file)) Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult postVisitDirectory (Path dir, IOException exc) throws IOException {
					if (Files.exists(dir)) Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	private static class FileVisitor extends SimpleFileVisitor<Path> {
		private static final String TAG = FileVisitor.class.getSimpleName();
		private final Path source;
		private final Path destination;

		FileVisitor (Path source, Path destination) {
			this.source = source;
			this.destination = destination;
		}

		@Override
		public FileVisitResult preVisitDirectory(final Path dir,final BasicFileAttributes attrs) throws IOException {
			Files.createDirectories(destination.resolve(source.relativize(dir)));
			return FileVisitResult.CONTINUE;
		}

		@Override public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
			Path target = destination.resolve(source.relativize(file));
			if (Files.exists(target)) {
				// there are not strings defined for this?
				FileTime sourceTime = (FileTime)Files.getAttribute(file, "lastModifiedTime");
				FileTime targetTime = (FileTime)Files.getAttribute(target, "lastModifiedTime");
				if (sourceTime.compareTo(targetTime) > 0) {
//					Gdx.app.log(TAG, "copy " + file);
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} else {
//				Gdx.app.log(TAG, "copy " + file);
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return FileVisitResult.CONTINUE;
		}
	}
}
