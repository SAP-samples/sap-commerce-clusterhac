package com.sap.commerce.clusterhac.service;

import de.hybris.platform.core.model.media.MediaModel;
import java.io.InputStream;
import java.util.List;

public interface ClusterThreadDumpService {

  List<MediaModel> findThreadDumpMedia();

  void cleanUpThreadDumpMedia();

  /**
   * Streams the bytes of a saved thread-dump Media by its code (the zip of jstack snapshots),
   * for in-place analysis. Returns null if no such media exists. Caller closes the stream.
   */
  InputStream streamThreadDumpMedia(String code);
}
