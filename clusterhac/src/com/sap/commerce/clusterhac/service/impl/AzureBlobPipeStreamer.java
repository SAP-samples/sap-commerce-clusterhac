package com.sap.commerce.clusterhac.service.impl;

import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.AppendBlobClient;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a heap dump from an {@link InputStream} (a named pipe in production) and streams it to an
 * Azure Blob Storage append blob, in bounded chunks so nothing large is held on local disk. Ported
 * from the reference {@code azure-jmap} App.groovy ({@code processPipe}/{@code appendBlock}/
 * {@code isGzip}).
 *
 * <p>The Azure calls go through the {@link BlobSink} seam (and {@link #ensureContainerExists()}) so
 * the chunking/naming/byte-accounting logic can be unit-tested with a fake sink — the real
 * {@link AppendBlobClient} is a {@code final} class and cannot be mocked directly.
 */
public class AzureBlobPipeStreamer {

  private static final Logger LOG = LoggerFactory.getLogger(AzureBlobPipeStreamer.class);

  // 256 KB read buffer: balances syscall overhead against memory for large sequential dumps.
  static final int BUFFER_SIZE = 262144;
  // Azure AppendBlob caps a single appendBlock at 4 MB; keep a safety margin of a few buffers.
  static final int BLOCK_SIZE = 4 * 1024 * 1024 - 4 * BUFFER_SIZE;

  private final String connectionString;
  private final String containerName;

  public AzureBlobPipeStreamer(String connectionString, String containerName) {
    this.connectionString = connectionString;
    this.containerName = containerName;
  }

  /** Marker line the parent {@code DefaultHeapDumpService} parses from this process's stdout. */
  public static final String RESULT_PREFIX = "CLUSTERHAC_HEAPDUMP_RESULT ";
  /** Periodic progress line the parent parses to update live byte counts. */
  public static final String PROGRESS_PREFIX = "CLUSTERHAC_HEAPDUMP_PROGRESS ";

  /**
   * Entry point for the separate uploader JVM. An in-process reader cannot drain a FIFO that the
   * SAME JVM is heap-dumping into (the dump's safepoint blocks the reader thread → deadlock), so the
   * reader/uploader runs here, in a child process, while the parent triggers {@code jcmd GC.heap_dump}
   * into the FIFO. Mirrors the reference {@code azure-jmap} App.groovy.
   *
   * <p>Args: {@code <connectionString> <container> <fifoPath> <host> <triggeredBy>}. It prints
   * {@code CLUSTERHAC_HEAPDUMP_PROGRESS <bytes>} as it streams, then
   * {@code CLUSTERHAC_HEAPDUMP_RESULT OK <blobName> <bytes> <blobUrl>} on success, or
   * {@code CLUSTERHAC_HEAPDUMP_RESULT ERR <message>} (exit non-zero) on failure.
   */
  public static void main(String[] args) {
    if (args.length < 5) {
      System.out.println(RESULT_PREFIX + "ERR missing args");
      System.exit(2);
      return;
    }
    String connectionString = args[0];
    String container = args[1];
    String fifoPath = args[2];
    String host = args[3];
    String triggeredBy = args[4];
    // Emit a progress line at most ~every 2 MB so the parent can show a live, growing byte count.
    final long[] lastReported = {0L};
    LongConsumer progress = total -> {
      if (total - lastReported[0] >= 2L * 1024 * 1024 || lastReported[0] == 0L) {
        lastReported[0] = total;
        System.out.println(PROGRESS_PREFIX + total);
        System.out.flush();
      }
    };
    // Use FileInputStream (not Files.newInputStream): the latter returns a seekable ChannelInputStream
    // whose available() calls FileChannel.position()/seek(), which throws "Illegal seek" on a FIFO.
    try (InputStream in = new java.io.FileInputStream(fifoPath)) {
      Result result = new AzureBlobPipeStreamer(connectionString, container)
          .stream(in, host, triggeredBy, progress);
      System.out.println(RESULT_PREFIX + "OK " + result.getBlobName() + " "
          + result.getBytesStreamed() + " " + result.getBlobUrl());
    } catch (Throwable t) {
      LOG.error("Heap-dump uploader failed", t);
      System.out.println(RESULT_PREFIX + "ERR "
          + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
      System.exit(1);
    }
  }

  static boolean isGzip(byte[] signature, int len) {
    return len >= 3 && signature[0] == (byte) 0x1F && signature[1] == (byte) 0x8B
        && signature[2] == (byte) 0x08;
  }

  /**
   * Streams {@code in} to a newly created append blob and returns the result. The blob is placed at
   * {@code <host>/<host>_<triggeredBy>_<timestamp>.hprof[.gz]} (the {@code <host>/} prefix is the
   * container's virtual "folder"). {@code progress} (nullable) is notified with the running byte
   * total.
   */
  public Result stream(InputStream in, String hostHint, String triggeredBy, LongConsumer progress)
      throws IOException {
    ensureContainerExists();
    BlobSink sink = null;
    String blobName = null;
    long totalStreamed = 0L;

    try (BufferedInputStream input = new BufferedInputStream(in, BUFFER_SIZE)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;
      int pending = 0;
      List<byte[]> blockChunks = new ArrayList<>();

      while ((bytesRead = input.read(buffer)) != -1) {
        if (pending >= BLOCK_SIZE) {
          sink = appendBlock(sink, blockChunks, pending, blobName);
          totalStreamed += pending;
          if (progress != null) {
            progress.accept(totalStreamed);
          }
          blockChunks.clear();
          pending = 0;
        }
        // Lazily name the blob from the first chunk's gzip magic.
        if (blobName == null) {
          blobName = blobName(buffer, bytesRead, hostHint, triggeredBy);
        }
        blockChunks.add(java.util.Arrays.copyOf(buffer, bytesRead));
        pending += bytesRead;
      }
      if (pending > 0 || sink == null) {
        if (blobName == null) {
          blobName = blobName(new byte[0], 0, hostHint, triggeredBy);
        }
        sink = appendBlock(sink, blockChunks, pending, blobName);
        totalStreamed += pending;
        if (progress != null) {
          progress.accept(totalStreamed);
        }
      }
    }

    String url = sink != null ? sink.getBlobUrl() : null;
    String name = sink != null ? sink.getBlobName() : blobName;
    LOG.info("Heap dump streamed to blob {} in container {} ({} bytes)", name, containerName,
        totalStreamed);
    return new Result(name, url, totalStreamed);
  }

  private static String blobName(byte[] firstChunk, int len, String hostHint, String triggeredBy) {
    String host = hostHint == null || hostHint.isBlank() ? "node" : hostHint;
    String user = triggeredBy == null || triggeredBy.isBlank() ? "unknown" : triggeredBy;
    String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    // <host>/<host>_<user>_<timestamp>.hprof[.gz] — the "<host>/" prefix is the virtual folder.
    String name = host + "/" + host + "_" + user + "_" + ts + ".hprof";
    if (isGzip(firstChunk, len)) {
      name += ".gz";
    }
    return name;
  }

  private BlobSink appendBlock(BlobSink sink, List<byte[]> arrays, int total, String blobName) {
    if (arrays.isEmpty()) {
      return sink;
    }
    if (sink == null) {
      sink = createSink(blobName);
      sink.create();
    }

    byte[] result = new byte[total];
    int pos = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, pos, array.length);
      pos += array.length;
    }
    sink.appendBlock(new ByteArrayInputStream(result), total);
    return sink;
  }

  /** Builds the blob sink for {@code name}. Overridable so tests supply a fake without real Azure. */
  protected BlobSink createSink(String name) {
    AppendBlobClient client = new BlobClientBuilder()
        .connectionString(connectionString)
        .containerName(containerName)
        .blobName(name)
        .buildClient()
        .getAppendBlobClient();
    return new AppendBlobSink(client);
  }

  /** Creates the container if missing. Overridable so tests can skip the real Azure call. */
  protected void ensureContainerExists() {
    var containerClient = new BlobServiceClientBuilder()
        .connectionString(connectionString)
        .buildClient()
        .getBlobContainerClient(containerName);
    if (!containerClient.exists()) {
      LOG.info("Azure container '{}' does not exist; creating.", containerName);
      containerClient.create();
    }
  }

  /**
   * Minimal append-blob abstraction over the (final, unmockable) {@link AppendBlobClient}, so the
   * streaming loop can be exercised with a fake.
   */
  public interface BlobSink {

    void create();

    void appendBlock(InputStream data, long length);

    String getBlobName();

    String getBlobUrl();
  }

  /** Production {@link BlobSink} backed by a real Azure {@link AppendBlobClient}. */
  static final class AppendBlobSink implements BlobSink {

    private final AppendBlobClient client;

    AppendBlobSink(AppendBlobClient client) {
      this.client = client;
    }

    @Override
    public void create() {
      client.create(true);
    }

    @Override
    public void appendBlock(InputStream data, long length) {
      client.appendBlock(data, length);
    }

    @Override
    public String getBlobName() {
      return client.getBlobName();
    }

    @Override
    public String getBlobUrl() {
      return client.getBlobUrl();
    }
  }

  /** Outcome of a completed stream. */
  public static class Result {

    private final String blobName;
    private final String blobUrl;
    private final long bytesStreamed;

    public Result(String blobName, String blobUrl, long bytesStreamed) {
      this.blobName = blobName;
      this.blobUrl = blobUrl;
      this.bytesStreamed = bytesStreamed;
    }

    public String getBlobName() {
      return blobName;
    }

    public String getBlobUrl() {
      return blobUrl;
    }

    public long getBytesStreamed() {
      return bytesStreamed;
    }
  }
}
