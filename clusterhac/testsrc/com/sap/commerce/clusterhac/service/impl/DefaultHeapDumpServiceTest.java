/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.commerce.clusterhac.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sap.commerce.clusterhac.service.HeapDumpService.HeapDumpState;
import com.sap.commerce.clusterhac.service.HeapDumpService.Status;
import com.sap.commerce.clusterhac.service.impl.AzureBlobPipeStreamer.BlobSink;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.util.Config;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Unit tests for the heap-dump streaming engine. Covers the pure, testable logic:
 * {@link AzureBlobPipeStreamer} blob naming (gzip-magic sniff), chunk/block boundary behaviour and
 * byte accounting (with a fake {@link BlobSink}, no real Azure), plus {@link DefaultHeapDumpService}
 * config resolution and the missing-credentials guard.
 */
@UnitTest
class DefaultHeapDumpServiceTest {

  /** In-memory {@link BlobSink} recording every append; no Azure contact. */
  private static class FakeSink implements BlobSink {

    final String name;
    final List<Integer> appendedLengths = new ArrayList<>();
    boolean created;

    FakeSink(String name) {
      this.name = name;
    }

    @Override
    public void create() {
      created = true;
    }

    @Override
    public void appendBlock(InputStream data, long length) {
      appendedLengths.add((int) length);
    }

    @Override
    public String getBlobName() {
      return name;
    }

    @Override
    public String getBlobUrl() {
      return "https://acct.blob.core.windows.net/jmap/" + name;
    }
  }

  /** Streamer that never touches real Azure: container check is a no-op, sink is the fake. */
  private static class TestStreamer extends AzureBlobPipeStreamer {

    FakeSink sink;
    String createdName;

    TestStreamer() {
      super("UseDevelopmentStorage=true", "jmap");
    }

    @Override
    protected void ensureContainerExists() {
      // no-op in tests
    }

    @Override
    protected BlobSink createSink(String name) {
      this.createdName = name;
      this.sink = new FakeSink(name);
      return sink;
    }
  }

  @Test
  void namesBlobWithHostPrefixUserAndGzSuffixWhenGzip() throws Exception {
    TestStreamer streamer = new TestStreamer();

    byte[] gz = gzip("some heap bytes".getBytes());
    AzureBlobPipeStreamer.Result result =
        streamer.stream(new ByteArrayInputStream(gz), "host1", "alice", null);

    // <host>/<host>_<user>_<timestamp>.hprof.gz
    assertTrue(streamer.createdName.startsWith("host1/host1_alice_"),
        "blob sits under the <host>/ prefix and encodes host_user_: " + streamer.createdName);
    assertTrue(streamer.createdName.endsWith(".hprof.gz"), "gzip magic must yield a .gz suffix");
    assertTrue(streamer.sink.created, "the blob must be created before appending");
    assertEquals((long) gz.length, result.getBytesStreamed());
  }

  @Test
  void namesBlobWithoutGzSuffixWhenStreamIsPlainAndFallsBackToUnknownUser() throws Exception {
    TestStreamer streamer = new TestStreamer();

    byte[] plain = "not a gzip stream".getBytes();
    streamer.stream(new ByteArrayInputStream(plain), "host2", null, null);

    assertTrue(streamer.createdName.startsWith("host2/host2_unknown_"),
        "blank user folds to 'unknown': " + streamer.createdName);
    assertTrue(streamer.createdName.endsWith(".hprof"), "plain stream must not get a .gz suffix");
    assertFalse(streamer.createdName.endsWith(".gz"));
  }

  @Test
  void splitsLargePayloadIntoMultipleAppendBlocksAndReportsProgress() throws Exception {
    TestStreamer streamer = new TestStreamer();

    // ~10 MB forces more than one block (block size is just under 4 MB).
    int size = 10 * 1024 * 1024;
    byte[] big = new byte[size];
    AtomicLong lastProgress = new AtomicLong();
    AzureBlobPipeStreamer.Result result =
        streamer.stream(new ByteArrayInputStream(big), "big", "bob", lastProgress::set);

    assertTrue(streamer.sink.created);
    assertTrue(streamer.sink.appendedLengths.size() >= 2, "10 MB must span multiple append blocks");
    for (int len : streamer.sink.appendedLengths) {
      assertTrue(len <= AzureBlobPipeStreamer.BLOCK_SIZE + AzureBlobPipeStreamer.BUFFER_SIZE,
          "no block exceeds the Azure 4 MB append limit (with buffer margin)");
    }
    long summed = streamer.sink.appendedLengths.stream().mapToLong(Integer::longValue).sum();
    assertEquals((long) size, summed, "every byte is appended exactly once");
    assertEquals((long) size, result.getBytesStreamed());
    assertEquals((long) size, lastProgress.get(), "progress ends at the full byte count");
  }

  @Test
  void isConfiguredRequiresConnectionStringAndContainer() {
    try (MockedStatic<Config> config = Mockito.mockStatic(Config.class)) {
      config.when(() -> Config.getString(DefaultHeapDumpService.P_CONNECTION_STRING, ""))
          .thenReturn("");
      config.when(() -> Config.getString(DefaultHeapDumpService.P_HOTFOLDER_CONNECTION_STRING, ""))
          .thenReturn("");
      config.when(() -> Config.getString(eq(DefaultHeapDumpService.P_CONTAINER), any()))
          .thenReturn("jmap");

      assertFalse(new DefaultHeapDumpService().isConfigured(),
          "no connection string anywhere => not configured");
    }
  }

  @Test
  void isConfiguredTrueWhenAllPresent() {
    try (MockedStatic<Config> config = Mockito.mockStatic(Config.class)) {
      config.when(() -> Config.getString(DefaultHeapDumpService.P_CONNECTION_STRING, ""))
          .thenReturn("DefaultEndpointsProtocol=https;AccountName=a;AccountKey=k;EndpointSuffix=core.windows.net");
      config.when(() -> Config.getString(eq(DefaultHeapDumpService.P_CONTAINER), any()))
          .thenReturn("jmap");

      assertTrue(new DefaultHeapDumpService().isConfigured());
    }
  }

  @Test
  void fallsBackToHotfolderConnectionStringWhenOwnIsBlank() {
    try (MockedStatic<Config> config = Mockito.mockStatic(Config.class)) {
      // Own heap-dump connection string is blank, but the hotfolder one is set.
      config.when(() -> Config.getString(DefaultHeapDumpService.P_CONNECTION_STRING, ""))
          .thenReturn("");
      config.when(() -> Config.getString(DefaultHeapDumpService.P_HOTFOLDER_CONNECTION_STRING, ""))
          .thenReturn("DefaultEndpointsProtocol=https;AccountName=hf;AccountKey=k;EndpointSuffix=core.windows.net");
      config.when(() -> Config.getString(eq(DefaultHeapDumpService.P_CONTAINER), any()))
          .thenReturn("jmap");

      assertTrue(new DefaultHeapDumpService().isConfigured(),
          "blank own connection string must fall back to the hotfolder account");
    }
  }

  @Test
  void dumpWithoutConfigFailsFastWithoutTouchingTheJvm() {
    try (MockedStatic<Config> config = Mockito.mockStatic(Config.class)) {
      config.when(() -> Config.getString(any(), any())).thenReturn("");

      DefaultHeapDumpService service = new DefaultHeapDumpService();
      service.dumpToAzure("alice");

      HeapDumpState state = service.getState();
      assertEquals(Status.FAILED, state.getStatus());
      assertTrue(state.getMessage().contains("not configured"));
    }
  }

  private static byte[] gzip(byte[] data) throws Exception {
    var bos = new java.io.ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
      gz.write(data);
    }
    return bos.toByteArray();
  }
}
