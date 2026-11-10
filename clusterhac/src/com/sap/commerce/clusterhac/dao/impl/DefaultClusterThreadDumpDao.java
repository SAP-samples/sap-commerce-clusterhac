package com.sap.commerce.clusterhac.dao.impl;

import com.sap.commerce.clusterhac.dao.ClusterThreadDumpDao;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import java.util.List;

public class DefaultClusterThreadDumpDao implements ClusterThreadDumpDao {

    private static final String THREAD_DUMP_SEARCH_QUERY = "SELECT {PK} FROM {Media} WHERE {code} LIKE 'thread_dump_%'";

    private final FlexibleSearchService flexibleSearchService;

    public DefaultClusterThreadDumpDao(FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }

    @Override
    public List<MediaModel> findThreadDumpMedia() {
        return flexibleSearchService.<MediaModel>search(THREAD_DUMP_SEARCH_QUERY).getResult();
    }
}
