/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller.threaddump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.hac.controller.threaddump.ThreadDumpAnalyzer.Analysis;
import de.hybris.platform.hac.controller.threaddump.ThreadDumpAnalyzer.NamedStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;


/**
 * Unit tests for {@link ThreadDumpAnalyzer}. The analyzer is pure Java over a streamed zip, so every
 * scenario is exercised by building an in-memory zip of crafted HotSpot/jstack text — no platform,
 * no DB, no temp files.
 */
@UnitTest
class ThreadDumpAnalyzerTest
{
	/** A jstack thread header line the analyzer's HEADER regex recognises (needs "- stats: ... cpu="). */
	private static String thread(final String name, final String state, final long cpu, final String... frames)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append('"').append(name).append("\" prio=5 tid=0x1 nid=0x1 ").append(state)
				.append(" - stats: cpu=").append(cpu).append(" blk=-1 wait=-1\n");
		sb.append("   java.lang.Thread.State: ").append(state).append('\n');
		for (final String f : frames)
		{
			sb.append("\tat ").append(f).append('\n');
		}
		sb.append('\n');
		return sb.toString();
	}

	/** Wrap one or more snapshot texts into a zip (one entry per snapshot). */
	private static InputStream zipOf(final String... snapshots)
	{
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(bos))
		{
			int i = 0;
			for (final String snap : snapshots)
			{
				zos.putNextEntry(new ZipEntry("snapshot-" + (i++) + ".txt"));
				zos.write(snap.getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return new ByteArrayInputStream(bos.toByteArray());
	}

	private static NamedStream named(final String label, final String... snapshots)
	{
		return new NamedStream(label, zipOf(snapshots));
	}

	@Test
	void detectsJvmDeadlock()
	{
		final String snap = "Found one Java-level deadlock:\n"
				+ "=============================\n"
				+ "\"Thread-A\":\n"
				+ "  waiting to lock monitor 0x1 (object 0x2, a java.lang.Object),\n"
				+ "  which is held by \"Thread-B\"\n"
				+ "\"Thread-B\":\n"
				+ "  waiting to lock monitor 0x3 (object 0x4, a java.lang.Object),\n"
				+ "  which is held by \"Thread-A\"\n\n\n"
				+ thread("Thread-A", "BLOCKED", 100, "com.acme.Foo.a(Foo.java:1)")
				+ thread("Thread-B", "BLOCKED", 100, "com.acme.Foo.b(Foo.java:2)");

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap));

		assertTrue(a.deadlockFound, "deadlock should be flagged");
		assertEquals(1, a.deadlocks.size());
		assertTrue(a.findings.stream().anyMatch(f -> "red".equals(f.get("severity")) && "confirmed".equals(f.get("kind"))),
				"a confirmed red finding should be present");
	}

	@Test
	void singleSnapshotUsesCumulativeCpuSortedDescending()
	{
		final String snap = thread("low", "RUNNABLE", 100, "com.acme.A.run(A.java:1)")
				+ thread("high", "RUNNABLE", 900, "com.acme.B.run(B.java:1)")
				+ thread("mid", "RUNNABLE", 500, "com.acme.C.run(C.java:1)");

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap));

		assertEquals("cumulative", a.cpuMode);
		assertEquals(1, a.snapshotCount);
		assertEquals(3, a.threadCount);
		assertFalse(a.topByCpu.isEmpty());
		assertEquals("high", a.topByCpu.get(0).get("name"));
		assertEquals(900L, a.topByCpu.get(0).get("cpu"));
	}

	@Test
	void multipleSnapshotsUseCpuDeltaRanking()
	{
		// Same threads across two snapshots. "burner" barely moves cumulatively but consumes the most
		// CPU *during* the window; delta ranking must surface it above the long-lived "steady" thread.
		final String s1 = thread("steady", "RUNNABLE", 1000, "com.acme.A.run(A.java:1)")
				+ thread("burner", "RUNNABLE", 50, "com.acme.B.run(B.java:1)");
		final String s2 = thread("steady", "RUNNABLE", 1010, "com.acme.A.run(A.java:1)")
				+ thread("burner", "RUNNABLE", 950, "com.acme.B.run(B.java:1)");

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", s1, s2)));

		assertEquals("delta", a.cpuMode);
		assertEquals(2, a.snapshotCount);
		assertEquals("burner", a.topByCpu.get(0).get("name"));
		assertEquals(900L, a.topByCpu.get(0).get("cpu")); // 950 - 50
	}

	@Test
	void buildsStateHistogramAndTrendRows()
	{
		final String s1 = thread("t1", "RUNNABLE", 10, "com.acme.A.run(A.java:1)")
				+ thread("t2", "BLOCKED", 10, "com.acme.B.run(B.java:1)")
				+ thread("t3", "WAITING", 10, "com.acme.C.run(C.java:1)");

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("2026-01-01 10:00", s1)));

		assertEquals(1, a.stateHistogram.size());
		final Map<?, ?> counts = (Map<?, ?>) a.stateHistogram.get(0).get("counts");
		assertEquals(1, counts.get("RUNNABLE"));
		assertEquals(1, counts.get("BLOCKED"));
		assertEquals(1, counts.get("WAITING"));
		assertEquals(1, a.trend.size());
		assertEquals("2026-01-01 10:00", a.trend.get(0).get("label"));
		assertEquals(3, a.trend.get(0).get("total"));
	}

	@Test
	void classifiesThreadsIntoHybrisPools()
	{
		final String snap = thread("hybrisHTTP-1", "RUNNABLE", 10, "com.acme.A.run(A.java:1)")
				+ thread("hybrisHTTP-2", "RUNNABLE", 10, "com.acme.A.run(A.java:1)")
				+ thread("PooledThread-5", "WAITING", 10, "sun.misc.Unsafe.park(Native Method)");

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap));

		final Map<String, Object> http = a.pools.stream()
				.filter(p -> "HTTP workers".equals(p.get("pool"))).findFirst().orElse(null);
		assertNotNull(http, "HTTP workers pool should be present");
		assertEquals(2, http.get("total"));
		assertEquals(2, http.get("active")); // both RUNNABLE
	}

	@Test
	void flagsWorkPoolClusterOnSharedFrame()
	{
		// 10 HTTP workers all on the same FlexibleSearch frame => pointer finding (>=8 and >=40% of pool).
		final StringBuilder snap = new StringBuilder();
		for (int i = 0; i < 10; i++)
		{
			snap.append(thread("hybrisHTTP-" + i, "RUNNABLE", 100,
					"de.hybris.platform.jdbcwrapper.PreparedStatementImpl.execute(PreparedStatementImpl.java:1)"));
		}

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap.toString()));

		assertTrue(a.findings.stream().anyMatch(f -> "pointer".equals(f.get("kind"))
						&& String.valueOf(f.get("title")).contains("HTTP workers")),
				"a work-pool cluster pointer finding should fire");
	}

	@Test
	void flagsDatabaseFrameThreads()
	{
		final StringBuilder snap = new StringBuilder();
		for (int i = 0; i < 5; i++)
		{
			snap.append(thread("worker-" + i, "RUNNABLE", 100, "java.sql.PreparedStatement.execute(PreparedStatement.java:1)"));
		}

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap.toString()));

		assertTrue(a.findings.stream().anyMatch(f -> String.valueOf(f.get("title")).contains("JDBC / database frames")),
				"a JDBC/database pointer finding should fire");
	}

	@Test
	void detectsStuckThreadAcrossSnapshots()
	{
		// An application thread wedged in the same business frame in both snapshots (not idle).
		final String stuckFrame = "com.acme.report.HugeReport.build(HugeReport.java:42)";
		final String s1 = thread("report-worker", "RUNNABLE", 10, stuckFrame);
		final String s2 = thread("report-worker", "RUNNABLE", 20, stuckFrame);

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", s1, s2)));

		assertFalse(a.stuck.isEmpty(), "stuck thread should be detected");
		assertEquals("report-worker", a.stuck.get(0).get("name"));
	}

	@Test
	void idlePoolThreadsAreNotReportedAsStuck()
	{
		// A parked pool thread present in both snapshots must NOT be flagged as stuck.
		final String idle = thread("pool-1-thread-1", "WAITING", 0, "sun.misc.Unsafe.park(Native Method)");

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", idle, idle)));

		assertTrue(a.stuck.isEmpty(), "idle parked thread must not be stuck");
	}

	@Test
	void networkReceiverThreadsAreNotReportedAsStuck()
	{
		// Cluster-comms threads (UDP broadcast receiver, connection receiver) sit in a native
		// receive/poll waiting for inbound data across every snapshot — that is idle, not a stall.
		final String udp = thread("UdpDiagHandler-8,hybris-broadcast", "RUNNABLE", 5,
				"sun.nio.ch.DatagramChannelImpl.receive0(Native Method)");
		final String recv = thread("Connection.Receiver,hybris-broadcast", "RUNNABLE", 5,
				"sun.nio.ch.Net.poll(Native Method)");

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", udp + recv, udp + recv)));

		assertTrue(a.stuck.isEmpty(), "idle network-receiver threads must not be flagged as stuck");
	}

	@Test
	void wedgedWorkerPoolThreadIsReportedAsStuck()
	{
		// A Hybris worker-pool thread stuck in a business frame across all snapshots MUST be flagged:
		// work-pool names are no longer blanket-excluded by name (idle is judged by frame instead).
		final String biz = "de.hybris.platform.order.impl.DefaultCartService.getCart(X.java:1)";
		final String s1 = thread("PooledThread-7", "RUNNABLE", 10, biz);
		final String s2 = thread("PooledThread-7", "RUNNABLE", 20, biz);

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", s1, s2)));

		assertFalse(a.stuck.isEmpty(), "a wedged PooledThread should be detected as stuck");
		assertEquals("PooledThread-7", a.stuck.get(0).get("name"));
	}

	@Test
	void threadPresentOnlyInLastSnapshotIsNotRankedByFullCumulativeCpu()
	{
		// A thread absent from the first snapshot has no in-window baseline; its large cumulative CPU
		// must NOT rank it above a thread that genuinely burned CPU during the recording.
		final String s1 = thread("hot", "RUNNABLE", 1000, "com.acme.A.run(A.java:1)");
		final String s2 = thread("hot", "RUNNABLE", 1300, "com.acme.A.run(A.java:1)")
				+ thread("newcomer", "RUNNABLE", 999999, "com.acme.B.run(B.java:1)");

		final Analysis a = ThreadDumpAnalyzer.analyze(List.of(named("dump", s1, s2)));

		assertEquals("delta", a.cpuMode);
		assertEquals("hot", a.topByCpu.get(0).get("name"));  // 300 delta, not newcomer's 999999
		assertTrue(a.topByCpu.stream().noneMatch(m -> "newcomer".equals(m.get("name"))),
				"a last-only thread must not appear in the CPU-delta ranking");
	}

	@Test
	void deadlockTextIsCapturedToTheSectionBoundaryNotTruncatedEarly()
	{
		final String deadlock = "Found one Java-level deadlock:\n"
				+ "=============================\n"
				+ "\"Thread-A\": waiting to lock monitor, held by \"Thread-B\"\n"
				+ "\"Thread-B\": waiting to lock monitor, held by \"Thread-A\"\n\n"
				+ "Java stack information for the threads listed above:\n";
		final String snap = deadlock
				+ thread("Thread-A", "BLOCKED", 1, "com.acme.Foo.a(Foo.java:1)")
				+ thread("Thread-B", "BLOCKED", 1, "com.acme.Foo.b(Foo.java:2)");

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap));

		assertTrue(a.deadlockFound);
		assertEquals(1, a.deadlocks.size());
		final String text = String.valueOf(a.deadlocks.get(0).get("text"));
		assertTrue(text.contains("Thread-A") && text.contains("Thread-B"),
				"the deadlock chain should be captured");
		assertFalse(text.contains("Java stack information"),
				"capture should stop at the blank line ending the deadlock section");
	}

	@Test
	void emptyZipYieldsError()
	{
		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf());

		assertNotNull(a.error, "error should be set for an empty dump");
	}

	@Test
	void garbageContentYieldsNoThreadsAndInfoFinding()
	{
		// A zip whose single entry is not a thread dump parses to one empty snapshot (not an error):
		// no threads, and the "nothing evident" info finding rather than any bottleneck claim.
		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf("this is not a thread dump at all"));

		assertEquals(0, a.threadCount);
		assertFalse(a.deadlockFound);
		assertEquals("info", a.findings.get(0).get("severity"));
	}

	@Test
	void healthyDumpProducesInfoFinding()
	{
		final String snap = thread("hybrisHTTP-1", "RUNNABLE", 10, "com.acme.A.serve(A.java:1)")
				+ thread("hybrisHTTP-2", "RUNNABLE", 10, "com.acme.A.serve(A.java:1)");

		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(snap));

		assertFalse(a.deadlockFound);
		assertEquals(1, a.findings.size());
		assertEquals("info", a.findings.get(0).get("severity"));
	}

	// --- hybrisComponent(): package-private static, resolvable directly from this package ---

	@Test
	void hybrisComponentResolvesKnownFrames()
	{
		assertEquals("FlexibleSearch (DB query)",
				ThreadDumpAnalyzer.hybrisComponent("de.hybris.platform.flexiblesearch.FlexibleSearchQuery.execute(X.java:1)"));
		assertEquals("JDBC / database",
				ThreadDumpAnalyzer.hybrisComponent("java.sql.PreparedStatement.execute(PreparedStatement.java:1)"));
		assertEquals("Solr", ThreadDumpAnalyzer.hybrisComponent("org.apache.solr.client.Http.request(Http.java:1)"));
		assertEquals("CronJob / job",
				ThreadDumpAnalyzer.hybrisComponent("de.hybris.platform.servicelayer.internal.jalo.ServicelayerJob.run(X.java:1)"));
	}

	@Test
	void hybrisComponentExtractsPlatformPackageSegment()
	{
		assertEquals("hybris:order",
				ThreadDumpAnalyzer.hybrisComponent("de.hybris.platform.order.impl.DefaultCartService.getCart(X.java:1)"));
	}

	@Test
	void hybrisComponentFallsBackToRawFrameAndHandlesEmpty()
	{
		assertEquals("unknown", ThreadDumpAnalyzer.hybrisComponent(""));
		assertEquals("unknown", ThreadDumpAnalyzer.hybrisComponent(null));
		final String raw = "com.acme.custom.Widget.paint(Widget.java:99)";
		assertEquals(raw, ThreadDumpAnalyzer.hybrisComponent(raw));
	}

	@Test
	void singleDumpCountsAsOneDump()
	{
		final Analysis a = ThreadDumpAnalyzer.analyze(zipOf(thread("t1", "RUNNABLE", 1, "com.acme.A.run(A.java:1)")));
		assertEquals(1, a.dumpCount);
	}

	@Test
	void multipleNamedDumpsArePooled()
	{
		final List<NamedStream> dumps = new ArrayList<>();
		dumps.add(named("d1", thread("t1", "RUNNABLE", 1, "com.acme.A.run(A.java:1)")));
		dumps.add(named("d2", thread("t1", "RUNNABLE", 2, "com.acme.A.run(A.java:1)")));

		final Analysis a = ThreadDumpAnalyzer.analyze(dumps);

		assertEquals(2, a.dumpCount);
		assertEquals(2, a.snapshotCount);
		assertNull(a.error);
	}
}
