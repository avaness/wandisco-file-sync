package org.wandisco;

import static junit.framework.TestCase.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import org.junit.Before;
import org.junit.Test;

public class TestSyncDir {


  private Path sourceDir;
  private Path targetDir;

  @Before
  public void init() throws IOException {
    sourceDir = Files.createTempDirectory("wandisco-source");
    //TODO delete on exit
    targetDir = Files.createDirectory(sourceDir.resolveSibling(sourceDir.getFileName().toString().replace("wandisco-source-", "wandisco-target-")));
    //TODO delete on exit
  }

  @Test
  public void updater_should_create_then_rename_then_change_attributes_then_delete_file() throws IOException {
    SyncDirUpdate updater = new SyncDirUpdate(sourceDir, targetDir);

    Path file_name = Paths.get("abc.def");
    Path source = sourceDir.resolve(file_name);
    Path target = targetDir.resolve(file_name);

    //creating file
    Files.createFile(source);

    assertTrue(Files.notExists(target));
    updater.create(file_name);
    assertTrue(Files.exists(target));

    Path new_name = Paths.get("efd.cba");
    Path new_source = sourceDir.resolve(new_name);
    Path new_target = targetDir.resolve(new_name);

    //renaming file
    Files.move(sourceDir.resolve(source), sourceDir.resolve(new_name));

    assertTrue(Files.notExists(new_target));
    updater.rename(file_name, new_name);
    assertTrue(Files.exists(new_target));
    assertTrue(Files.notExists(target));

    //deleting renamed file
    Files.delete(new_source);
    assertTrue(Files.notExists(new_source));
    updater.delete(new_name);
    assertTrue(Files.notExists(target));
  }

/*
  @Test
  public void should_update_file_attributes() {
    fail();
  }

*/
  @Test
  public void monitor_should_detect_file_create_rename_delete() throws IOException, InterruptedException {
    SyncDirMonitor monitor = new SyncDirMonitor(sourceDir.toString(), targetDir.toString());
    WatchService watchService = monitor.initWatcher();

    Path filename = Paths.get("abc.def");
    Path filename2 = Paths.get("efd.cba");


    Files.createFile(sourceDir.resolve(filename));
    //TODO replace by SyncDirUpdate mockup
    assertTrue(monitor.tryPollAndProcessUpdates(watchService, 1000) != 0);
    assertTrue(Files.exists(targetDir.resolve(filename)));

    Files.move(sourceDir.resolve(filename), sourceDir.resolve(filename2));
    assertTrue(monitor.tryPollAndProcessUpdates(watchService, 1000) != 0);
    assertTrue(Files.notExists(targetDir.resolve(filename)));
    assertTrue(Files.exists(targetDir.resolve(filename2)));

    Files.delete(sourceDir.resolve(filename2));
    assertTrue(monitor.tryPollAndProcessUpdates(watchService, 1000) != 0);
    assertTrue(Files.notExists(targetDir.resolve(filename2)));
  }

/*
  @Test
  public void monitor_should_detect_files_attribute_change() throws IOException, InterruptedException {
    SyncDirMonitor monitor = new SyncDirMonitor(sourceDir.toString(), targetDir.toString());
    WatchService watchService = monitor.initWatcher();

    Path filename = Paths.get("abc.def");

    final Path source = sourceDir.resolve(filename);
    Files.createFile(source);

    assertTrue(monitor.tryPollAndProcessUpdates(watchService,1000) != 0);
    assertTrue(Files.exists(targetDir.resolve(filename)));
    final BasicFileAttributes attrs = Files
        .readAttributes(targetDir.resolve(filename), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

    fail();
  }
*/

}
