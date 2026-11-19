package com.sap.commerce.clusterhac.service.impl;

import com.sap.commerce.clusterhac.dao.ClusterThreadDumpDao;
import com.sap.commerce.clusterhac.service.ClusterThreadDumpService;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClusterThreadDumpService implements ClusterThreadDumpService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultClusterThreadDumpService.class);

  private final ClusterThreadDumpDao clusterThreadDumpDao;
  private final ModelService modelService;
  private final MediaService mediaService;

  public DefaultClusterThreadDumpService(ClusterThreadDumpDao clusterThreadDumpDao,
      ModelService modelService, MediaService mediaService) {
    this.clusterThreadDumpDao = Objects.requireNonNull(clusterThreadDumpDao, "clusterThreadDumpDao");
    this.modelService = Objects.requireNonNull(modelService, "modelService");
    this.mediaService = Objects.requireNonNull(mediaService, "mediaService");
  }

  @Override
  public List<MediaModel> findThreadDumpMedia() {
    final List<MediaModel> media = clusterThreadDumpDao.findThreadDumpMedia();
    return media != null ? media : Collections.emptyList();
  }

  @Override
  public void cleanUpThreadDumpMedia() {
    final List<MediaModel> media = findThreadDumpMedia();
    if (media.isEmpty()) {
      LOG.debug("No thread-dump media to clean up");
      return;
    }
    LOG.info("Removing {} thread-dump media", media.size());
    modelService.removeAll(media);
  }

  @Override
  public InputStream streamThreadDumpMedia(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return findThreadDumpMedia().stream()
        .filter(m -> StringUtils.equals(m.getCode(), code))
        .findFirst()
        .map(mediaService::getStreamFromMedia)
        .orElse(null);
  }
}
