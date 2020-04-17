package org.wandisco;

import static com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SyncDirMonitor implements Runnable {

  private final Path sourceDir;

  private final SyncDirUpdate updater;

  private WatchService watchService;

  public SyncDirMonitor(String sourceDir, String targetDir) throws BadPathException {
    this.sourceDir = Paths.get(sourceDir);
    Path target = Paths.get(targetDir);

    this.updater = new SyncDirUpdate(this.sourceDir, target);

    //verifying sourceDir and target are directories
    verifyIsDirectory(this.sourceDir);
    verifyIsDirectory(target);

    //todo add check if sourceDir is not inside targetDir and vice versa
    //TODO add check if sourceDir is symlinked into targetDir or vice versa
  }

  private void verifyIsDirectory(Path path) throws BadPathException {
    if (!Files.exists(path) || ! Files.isDirectory(path))
      throw new BadPathException(String.format("not exist or not a directory %s", path));
  }

  public void run() {

    try(WatchService watchService = initWatcher()) {

      //TODO updater.delete()
      //TODO it's a rapid hack to cleanup target directory on startup
      //TODO add scanning target directory and compare to sourceDir, extract diff: new, changed, updated, deleted entries
//      cleanupDir(target);

      //scan sourceDir directory, add everything as newly created entries
      updater.createOrUpdate(getDirSnapshot(sourceDir));


      try {
        while (true) {
          pollAndProcessUpdates(watchService);
        }
      } catch (ClosedWatchServiceException e) {
        System.out.println("gracefully stopped, bye");
      }

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      //todo review how to process and report InterruptedException, possibly it's legal case to interrupt
      throw new RuntimeException(e);
    }
  }

  WatchService initWatcher() throws IOException {
    watchService = FileSystems.getDefault().newWatchService();

    //todo uncomment FILE_TREE when adding nested directories support
    sourceDir.register(watchService, new Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}, FILE_TREE);

    return watchService;
  }

  int tryPollAndProcessUpdates(WatchService watchService, long ms) throws IOException, InterruptedException {

    WatchKey key = ms == 0? watchService.poll(): watchService.poll(ms, TimeUnit.MILLISECONDS);

    if (key == null)
      return 0;

    try {
      return processEvents(key.pollEvents());
    } finally {
      key.reset();
    }

  }

  void pollAndProcessUpdates(WatchService watchService) throws InterruptedException, IOException {
    WatchKey key = watchService.take();

    try {
      processEvents(key.pollEvents());
    } finally {
      key.reset();
    }
  }

  int processEvents(List<WatchEvent<?>> events) throws IOException {
    for (WatchEvent<?> event : events) {
      if (event.kind() == ENTRY_CREATE) {
        updater.create((Path) event.context());
      } else if (event.kind() == ENTRY_DELETE) {
        updater.delete((Path) event.context());
      } else if (event.kind() == ENTRY_MODIFY) {
        updater.update((Path) event.context());
      }
    }
    return events.size();
  }

  public void close() throws IOException {
    watchService.close();
  }

  private List<Path> getDirSnapshot(Path path) throws IOException {
    //TODO use
    try(Stream<Path> entries = Files.walk(path)){
      //skipping directory itself
      return entries.skip(1)
          .map(path::relativize)
          .collect(Collectors.toList());
    }
  }

}
