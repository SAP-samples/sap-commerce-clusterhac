package com.sap.commerce.clusterhac.service.impl;

import com.sap.commerce.clusterhac.dao.ClusterThreadDumpDao;
import com.sap.commerce.clusterhac.service.ClusterThreadDumpService;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class DefaultClusterThreadDumpService implements ClusterThreadDumpService {

  private final ClusterThreadDumpDao clusterThreadDumpDao;
  private final ModelService modelService;
  private final MediaService mediaService;

  public DefaultClusterThreadDumpService(ClusterThreadDumpDao clusterThreadDumpDao,
      ModelService modelService, MediaService mediaService) {
    this.clusterThreadDumpDao = clusterThreadDumpDao;
    this.modelService = modelService;
    this.mediaService = mediaService;
  }

  @Override
  public List<MediaModel> findThreadDumpMedia() {
    return clusterThreadDumpDao.findThreadDumpMedia();
  }

  @Override
  public void cleanUpThreadDumpMedia() {
    modelService.removeAll(findThreadDumpMedia());
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
