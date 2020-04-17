package org.wandisco;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncDirUpdate {

  private Logger log = LoggerFactory.getLogger(SyncDirUpdate.class);

  private final Path sourceDir;
  private final Path targetDir;

  public SyncDirUpdate(Path sourceDir, Path targetDir) {
    this.sourceDir = sourceDir;
    this.targetDir = targetDir;
  }

  public void createOrUpdate(List<Path> snapshot) throws IOException {
    for(Path p: snapshot) {
      if (Files.notExists(targetDir.resolve(p)))
        create(p);
      else
        update(p);
    }
  }

  public void create(Path path) throws IOException {
    Path sourcePath = sourceDir.resolve(path);
    Path targetPath = targetDir.resolve(path);

    if (Files.isDirectory(sourcePath)) {
      log.info("creating directory {}", path);
      Files.createDirectory(targetPath);
    }
    else {
      log.info("creating file {}", path);
      copyFile(sourcePath, targetPath);
    }
  }

  private void copyFile(Path sourcePath, Path targetPath) throws IOException {
    //todo dirty hack to avoid NoSuchFileException
    if (!Files.exists(sourcePath))
      return;

    if (!Files.isSymbolicLink(sourcePath)) {
      Files.copy(sourcePath, targetPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
    }
    else {
      Path link = Files.readSymbolicLink(sourcePath);
      log.info("copying symlink {} -> {}", sourcePath, link);
      //TODO unfortunately windows prohibits to create symlinks even being administrator
      Files.copy(sourcePath, targetPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
//      Files.createSymbolicLink(targetPath, link);
    }
  }

  public void update(Path path) throws IOException {
    log.info("updating {}", path);

    Path sourcePath = sourceDir.resolve(path);
    Path targetPath = targetDir.resolve(path);

    if(Files.isDirectory(sourcePath)) {
      log.info("{} is directory, skipping", path);
      return;
    }

    Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

    Path tmpPath = targetDir.resolve(tmp);

    if (Files.exists(sourcePath)) {
      log.info("copying {} to {}", path, tmpPath);
      copyFile(sourcePath, tmpPath);
      log.info("renaming {} to {}", tmp, targetPath);
      Files.move(tmpPath, targetPath, REPLACE_EXISTING, ATOMIC_MOVE);
    }
  }

  public void delete(Path path) throws IOException {
    Path source = sourceDir.resolve(path);
    Path target = targetDir.resolve(path);
    if (!Files.isDirectory(source)) {
      log.info("deleting file {}", path);
      Files.deleteIfExists(target);
    }
    else {
      log.info("deleting dir {}", path);
      deleteDir(target);
    }
  }

  //TODO no usage from main code, only from tests, review
  public void rename(Path file1, Path file2) throws IOException {
    log.info("renaming {} to {}", file1, file2);
    Files.move(targetDir.resolve(file1), targetDir.resolve(file2));
  }

  private void deleteDir(Path path) throws IOException {
    log.info("deleting dir {}", path);
    try (Stream<Path> entries = Files.walk(path)) {
      entries.skip(1).sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(file -> {
            log.info("deleting {}", file);
            //dirty hack to not to process errors on delete
            file.delete();
          });
    }
  }
}
