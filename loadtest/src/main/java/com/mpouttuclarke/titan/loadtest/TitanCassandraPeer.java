package com.mpouttuclarke.titan.loadtest;

import java.io.File;
import java.lang.Thread.State;
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

	static final Logger LOG = Logger.getLogger(TitanCassandraPeer.class);
	
	public static void main(String[] args) throws Exception {
		BaseConfiguration conf = new BaseConfiguration();
		Configuration storage = conf
				.subset(GraphDatabaseConfiguration.STORAGE_NAMESPACE);
		storage.setProperty("cassandra-config-dir", new File(args[0]).toURI()
				.toURL().toString());
		storage.setProperty(GraphDatabaseConfiguration.STORAGE_BACKEND_KEY,
				"embeddedcassandra");
		storage.setProperty("batch-loading", "true");
		conf.setProperty("ids.block-size", 1024 * 1024 * 10);
		int error = 0;

		final long vertexCount = Long.valueOf(args[1]);
		final int instanceCount = Integer.valueOf(args[2]);
		final int instanceId = Integer.valueOf(args[3]);
		final int threads = Integer.valueOf(args[4]);
		final int commitSize = Integer.valueOf(args[5]);
		final AtomicLong done = new AtomicLong();
		final AtomicLong nanos = new AtomicLong();
		try {
			final TitanGraph graph = TitanFactory.open(conf);
			graph.makeType().name("vid").dataType(Long.class)
					.indexed(Vertex.class).unique(Direction.BOTH)
					.makePropertyKey();
			graph.makeType().name("linkTo").unique(Direction.OUT)
					.makeEdgeLabel();

			Thread[] workers = new Thread[threads];
			for (int thread = 0; thread < threads; thread++) {
				final int threadId = thread;
				workers[thread] = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							long start = System.nanoTime();
							final long divisor = instanceCount * threads;
							final long modulus = instanceId * threads + threadId;
							Vertex prev = null;
							int changed = 0;
							TitanTransaction tx = graph.newTransaction();
							for (long vertexId = 0L; vertexId < vertexCount; vertexId++) {
								if (vertexId % divisor == modulus) {
									// System.out.println(String.format("thread %02d adding vertex %s",
									// threadId, vertexId));
									Vertex curr = tx.addVertex(null);
									curr.setProperty("vid", vertexId);
									if (prev != null) {
										prev.addEdge("linkTo", curr);
										prev = curr;
									}
								}
								changed++;
								if (changed % commitSize == 0) {
									tx.commit();
									done.addAndGet(changed);
									long since = System.nanoTime();
									nanos.addAndGet(Math.abs(since - start));
									tx = graph.newTransaction();
									start = since;
									changed = 0;
									prev = null;
								}
							}
							tx.commit();
						} catch(Exception e) {
							LOG.error(Thread.currentThread().getName(), e);
						}
					}
				});
				workers[thread].start();
			}
			
			boolean allDone = true;
			do {
				Thread.sleep(10000);
				stats(done, nanos);
				for (Thread worker : workers) {
					State state = worker.getState();
					allDone = allDone && state == State.TERMINATED;
				}
			} while(!allDone);

			graph.shutdown();

			System.out.print("\n\n\nFinal results: ");
			stats(done, nanos);
			System.out.print("\n\n\n");
		} catch (Exception e) {
			error = -1;
			throw e;
		} finally {
			System.exit(error);
		}
	}

	private static void stats(final AtomicLong done, final AtomicLong nanos) {
		long soFar = done.get();
		Log.info(String.format("%,d vertices at %,.0f / sec", soFar, soFar
				/ (nanos.get() / 1000d / 1000 / 1000)));
	}

}
