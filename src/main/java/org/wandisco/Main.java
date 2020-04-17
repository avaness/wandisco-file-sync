package org.wandisco;

import java.io.IOException;

public class Main {

  //TODO add params check and usage info
  public static void main(String[] args) throws IOException, InterruptedException {
    new SyncDirMonitor(args[0], args[1]).run();
  }

}
