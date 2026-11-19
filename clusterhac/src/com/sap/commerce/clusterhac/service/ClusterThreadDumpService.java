package com.sap.commerce.clusterhac.service;

import de.hybris.platform.core.model.media.MediaModel;
import java.io.InputStream;
import java.util.List;

/**
 * Manages the {@link MediaModel}s that hold saved cluster thread dumps (zipped jstack snapshots
 * produced by the thread-dump recording feature).
 */
public interface ClusterThreadDumpService {

  /**
   * Finds all saved thread-dump media.
   *
   * @return the matching media, or an empty list if none exist; never {@code null}
   */
  List<MediaModel> findThreadDumpMedia();

  /**
   * Removes all saved thread-dump media returned by {@link #findThreadDumpMedia()}. No-op when
   * there is nothing to remove.
   */
  void cleanUpThreadDumpMedia();

  /**
   * Streams the bytes of a saved thread-dump media by its code (the zip of jstack snapshots), for
   * in-place analysis. The caller is responsible for closing the returned stream.
   *
   * @param code the media code to look up; blank input yields {@code null} without querying
   * @return the media content stream, or {@code null} if {@code code} is blank or no such media exists
   */
  InputStream streamThreadDumpMedia(String code);
}
