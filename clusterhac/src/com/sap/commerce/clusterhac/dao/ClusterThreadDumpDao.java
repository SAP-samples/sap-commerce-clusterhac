package com.sap.commerce.clusterhac.dao;

import de.hybris.platform.core.model.media.MediaModel;
import java.util.List;

public interface ClusterThreadDumpDao {

    List<MediaModel> findThreadDumpMedia();
}
