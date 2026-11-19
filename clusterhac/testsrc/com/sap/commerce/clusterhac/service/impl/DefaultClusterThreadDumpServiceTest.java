/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.commerce.clusterhac.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.commerce.clusterhac.dao.ClusterThreadDumpDao;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


/**
 * Unit tests for {@link DefaultClusterThreadDumpService}: constructor null-checks,
 * {@code findThreadDumpMedia} null-result safety, {@code cleanUpThreadDumpMedia} (empty vs.
 * non-empty), and {@code streamThreadDumpMedia} (blank-code guard, code filter, media-stream
 * mapping). Mockito only; no platform.
 */
@UnitTest
@ExtendWith(MockitoExtension.class)
class DefaultClusterThreadDumpServiceTest
{
	@Mock
	private ClusterThreadDumpDao clusterThreadDumpDao;
	@Mock
	private ModelService modelService;
	@Mock
	private MediaService mediaService;

	private DefaultClusterThreadDumpService service;

	@BeforeEach
	void setUp()
	{
		service = new DefaultClusterThreadDumpService(clusterThreadDumpDao, modelService, mediaService);
	}

	private static MediaModel media(final String code)
	{
		// Mock the model rather than instantiating it — hybris item-model setters need a persistence
		// context that a pure unit test doesn't have.
		final MediaModel m = mock(MediaModel.class);
		lenient().when(m.getCode()).thenReturn(code);
		return m;
	}

	@Test
	void blankCodeReturnsNullWithoutHittingDao()
	{
		assertNull(service.streamThreadDumpMedia(null));
		assertNull(service.streamThreadDumpMedia("  "));
		verify(clusterThreadDumpDao, never()).findThreadDumpMedia();
	}

	@Test
	void matchingCodeReturnsMappedStream()
	{
		// Build all mocks up front — creating a media mock (which stubs getCode) inside a when(...)
		// argument would nest stubbing and trip Mockito's UnfinishedStubbingException.
		final MediaModel other = media("dump-1");
		final MediaModel wanted = media("dump-2");
		final InputStream stream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
		when(clusterThreadDumpDao.findThreadDumpMedia()).thenReturn(List.of(other, wanted));
		when(mediaService.getStreamFromMedia(wanted)).thenReturn(stream);

		final InputStream result = service.streamThreadDumpMedia("dump-2");

		assertSame(stream, result, "the stream for the matching media should be returned");
	}

	@Test
	void noMatchingCodeReturnsNull()
	{
		final MediaModel m1 = media("dump-1");
		final MediaModel m2 = media("dump-2");
		when(clusterThreadDumpDao.findThreadDumpMedia()).thenReturn(List.of(m1, m2));

		final InputStream result = service.streamThreadDumpMedia("does-not-exist");

		assertNull(result);
		verify(mediaService, never()).getStreamFromMedia(any());
	}

	@Test
	void findThreadDumpMediaReturnsEmptyListWhenDaoReturnsNull()
	{
		when(clusterThreadDumpDao.findThreadDumpMedia()).thenReturn(null);

		assertTrue(service.findThreadDumpMedia().isEmpty(), "a null DAO result must surface as an empty list");
	}

	@Test
	void cleanUpRemovesAllFoundMedia()
	{
		final MediaModel m1 = media("dump-1");
		final MediaModel m2 = media("dump-2");
		final List<MediaModel> found = List.of(m1, m2);
		when(clusterThreadDumpDao.findThreadDumpMedia()).thenReturn(found);

		service.cleanUpThreadDumpMedia();

		verify(modelService).removeAll(found);
	}

	@Test
	void cleanUpDoesNothingWhenNoMedia()
	{
		when(clusterThreadDumpDao.findThreadDumpMedia()).thenReturn(List.of());

		service.cleanUpThreadDumpMedia();

		verify(modelService, never()).removeAll(any());
	}

	@Test
	void constructorRejectsNullDependencies()
	{
		assertThrows(NullPointerException.class,
				() -> new DefaultClusterThreadDumpService(null, modelService, mediaService));
		assertThrows(NullPointerException.class,
				() -> new DefaultClusterThreadDumpService(clusterThreadDumpDao, null, mediaService));
		assertThrows(NullPointerException.class,
				() -> new DefaultClusterThreadDumpService(clusterThreadDumpDao, modelService, null));
	}
}
