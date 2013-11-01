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
		try {
			final Semaphore doneLock = new Semaphore(threads);
			final TitanGraph graph = TitanFactory.open(conf);
			graph.makeType().name("vid").dataType(Long.class)
					.indexed(Vertex.class).unique(Direction.BOTH)
					.makePropertyKey();
			graph.makeType().name("linkTo").unique(Direction.OUT)
					.makeEdgeLabel();
			graph.commit();
			
			final long start = System.nanoTime();
			Thread[] workers = new Thread[threads];
			for (int thread = 0; thread < threads; thread++) {
				final int threadId = thread;
				workers[thread] = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							doneLock.acquire();
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
									changed++;
									if (changed % commitSize == 0) {
										tx.commit();
										done.addAndGet(changed);
										tx = graph.newTransaction();
										changed = 0;
										prev = null;
									}
								}
							}
							tx.commit();
							done.addAndGet(changed);
						} catch(Exception e) {
							LOG.error(Thread.currentThread().getName(), e);
						} finally {
							doneLock.release();
						}
					}
				});
				workers[thread].start();
			}
			
			while(!doneLock.tryAcquire(threads, 10, TimeUnit.SECONDS)) {
				stats(done, System.nanoTime() - start);
			}

			graph.shutdown();

			System.out.print("\nFinal results: ");
			stats(done, System.nanoTime() - start );
			System.out.print("\n");
			
			if(instanceCount > 1) {
				System.out.println("Waiting forever so other nodes don't fail");
				Thread.sleep(Long.MAX_VALUE);
			}
		} catch (Exception e) {
			error = -1;
			throw e;
		} finally {
			System.exit(error);
		}
	}

	private static void stats(final AtomicLong done, final long nanos) {
		long soFar = done.get();
		Log.info(String.format("%,d vertices at %,.0f / sec", soFar, soFar
				/ (nanos / 1000d / 1000 / 1000)));
	}

}
