package com.mpouttuclarke.titan.loadtest;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import com.esotericsoftware.minlog.Log;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.graphdb.configuration.GraphDatabaseConfiguration;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;

public class TitanCassandraPeer {

	static enum ReadRatio {
		TL_RR_00(0, true), TL_RR_10(10, false), TL_RR_25(4, false), TL_RR_50(1,
				true), TL_RR_75(3, true), TL_RR_90(9, true), TL_RR_95(19, true);

		public int offset;
		public boolean forLoop;

		private ReadRatio(int offset, boolean forLoop) {
			this.offset = offset;
			this.forLoop = forLoop;
		}
	}

	static final Logger LOG = Logger.getLogger(TitanCassandraPeer.class);

	public static void main(String[] args) throws Exception {
		BaseConfiguration conf = new BaseConfiguration();
		Configuration storage = conf
				.subset(GraphDatabaseConfiguration.STORAGE_NAMESPACE);
		storage.setProperty("cassandra-config-dir", new File(args[0]).toURI()
				.toURL().toString());
		storage.setProperty(GraphDatabaseConfiguration.STORAGE_BACKEND_KEY,
				"embeddedcassandra");
		conf.setProperty("ids.block-size", 1024 * 1024 * 10);
		int error = 0;

		final long vertexCount = Long.valueOf(args[1]);
		final int instanceCount = Integer.valueOf(args[2]);
		final int instanceId = Integer.valueOf(args[3]);
		final int threads = Integer.valueOf(args[4]);
		final int commitSize = Integer.valueOf(args[5]);
		storage.setProperty("batch-loading", true);

		try {
			final TitanGraph graph = TitanFactory.open(conf);

			if (instanceCount < 2 || instanceId == 0) {
				graph.makeType().name("vid").dataType(Long.class)
						.indexed(Vertex.class).unique(Direction.BOTH)
						.makePropertyKey();
				graph.makeType().name("linkTo").unique(Direction.OUT)
						.makeEdgeLabel();
				graph.commit();
			}
			if (instanceId == 0 && instanceCount > 1) {
				LOG.info("Seed waiting for cluster startup before generating load");
				Thread.sleep(60000);
			}
			
			long offset = 0L;
			long count = vertexCount;
			int length = ReadRatio.values().length;
			for (int x = 0; x < length; x++) {
				if(x < length - 1) {
					count /= 2;
				} else {
					count = vertexCount - offset;
				}
				runTest(ReadRatio.values()[x], offset, count, instanceCount, instanceId, threads,
						commitSize, graph);
				offset = offset + count;
			}

			graph.shutdown();
		} catch (Exception e) {
			error = -1;
			e.printStackTrace();
		} finally {
			if (instanceCount > 1) {
				System.out
						.println("Waiting forever so other nodes don't fail, Ctrl-C to terminate");
				Thread.sleep(Long.MAX_VALUE);
			}
			System.exit(error);
		}
	}

	private static void runTest(final ReadRatio ratio, final long vertexOffset,
			final long vertexCount, final int instanceCount,
			final int instanceId, final int threads, final int commitSize,
			final TitanGraph graph) throws InterruptedException {
		final AtomicLong writes = new AtomicLong();
		final AtomicLong reads = new AtomicLong();
		final AtomicLong hits = new AtomicLong();

		LOG.info("Starting worker threads");
		final long start = System.nanoTime();
		Thread[] workers = new Thread[threads];
		for (int thread = 0; thread < threads; thread++) {
			final int threadId = thread;
			workers[thread] = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						LOG.info(String.format("Starting thread %s", threadId));
						final long divisor = instanceCount * threads;
						final long modulus = instanceId * threads + threadId;
						Vertex prev = null;
						int changed = 0;
						TitanTransaction tx = graph.newTransaction();
						for (long vertexId = vertexOffset; vertexId < vertexCount + vertexOffset; vertexId++) {
							if (vertexId % divisor == modulus) {
								Vertex curr = tx.addVertex(null);
								curr.setProperty("vid", vertexId);
								if (prev != null) {
									prev.addEdge("linkTo", curr);
									prev = curr;
								}
								changed++;
								if (changed % commitSize == 0) {
									tx.commit();
									tx = graph.newTransaction();
									writes.addAndGet(changed);
									changed = 0;
									prev = null;
								}
								if (ratio.forLoop) {
									for (int x = 0; x < ratio.offset; x++) {
										query(vertexCount, tx, reads, hits);
									}
								} else if (changed % ratio.offset == 0) {
									query(vertexCount, tx, reads, hits);
								}
							}
						}
						tx.commit();
						writes.addAndGet(changed);
					} catch (Exception e) {
						LOG.error(Thread.currentThread().getName(), e);
					} finally {
						LOG.info(String.format("Thread %s done", threadId));
					}
				}
			});
			workers[thread].start();
		}

		LOG.info("Waiting for worker threads to complete");
		for (Thread worker : workers) {
			worker.join();
		}
		
		stats(ratio, writes, reads, hits, System.nanoTime() - start);

	}

	private static void query(final long vertexCount,
			final TitanTransaction tx, final AtomicLong reads,
			final AtomicLong hits) {
		Long vid = (long) Math.floor(Math.random() * vertexCount);
		reads.incrementAndGet();
		for (Vertex vertex : tx.query().limit(1).has("vid", vid).vertices()) {
			if (vid.equals(vertex.getProperty("vid"))) {
				hits.incrementAndGet();
			}
		}
	}

	private static void stats(final ReadRatio ratio, final AtomicLong writes,
			final AtomicLong reads, final AtomicLong hits, final long nanos) {
		long writesSoFar = writes.get();
		long readsSoFar = reads.get();
		long hitsSoFar = hits.get();
		double secs = Math.abs(nanos) / 1000d / 1000 / 1000;
		System.out.println(String.format(
				"%s\t%.3f\t%,d\t%,.0f\t%,d\t%,.0f\t%,d", ratio, secs,
				writesSoFar, writesSoFar / secs, readsSoFar, readsSoFar / secs,
				hitsSoFar));
	}

}
