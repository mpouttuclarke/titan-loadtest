titan-loadtest
==============

Distributed and peer-based load test for Titan.  Each node loads independently without seperate load test servers.

Builds an executable jar using Maven Shade plugin.  Execute the following command to build the jar:
mvn package

If you want to run with different versions of Titan or Cassandra, just change the Maven pom.xml dependencies and the new versions will be packaged in a new jar.  This makes it easy to test multiple versions of Titan and Cassandra without risk of dependency collisions polluting the results.

To run the load test, execute with the java -jar command:
java -javaagent:loadtest-0.0.1-SNAPSHOT.jar -Xmx4g -Xms4g -jar loadtest-0.0.1-SNAPSHOT.jar cassandra-disk.yaml 1000000000 10 0 12 64

Please note that using the javaagent is mandatory, as Cassandra requires Jamm to montior heap usage to determine when to flush memtables.  Without the javaagent, the JVM will spiral into endless GC cycles.

The parameters to the jar:
1.  Location of the cassandra.yaml
2.  Vertex count to create
3.  Node count
4.  Node id (must be 0 to node count - 1)
5.  Worker thread count
6.  Commit size

When run, the jar will test various read ratios and will report results for each ratio mix.  Currently, the following read / write mixes are supported:
TL_RR_00  100% writes 
TL_RR_10  90% writes, 10% reads
TL_RR_25  75% writes, 25% reads
TL_RR_50  50 / 50
TL_RR_75  25% writes, 75% reads
TL_RR_90  10% writes, 90% reads
TL_RR_95  5% writes, 95% reads

For example, the results of the command on a single machine will look like:
$ java -javaagent:target/loadtest-0.0.1-SNAPSHOT.jar -Xmx2g -Xms2g -jar target/loadtest-0.0.1-SNAPSHOT.jar src/test/resources/cassandra-local.yaml 10000000 1 0 3 64 | grep TL_RR
TL_RR_00	170.648	5,000,000	29,300	0	0	0
TL_RR_10	196.941	2,500,000	12,694	273,435	1,388	273,435
TL_RR_25	362.605	1,250,000	3,447	312,498	862	312,498
TL_RR_50	199.218	625,000	3,137	625,000	3,137	625,000
TL_RR_75	198.198	312,500	1,577	937,500	4,730	937,500
TL_RR_90	260.970	156,250	599	1,406,250	5,389	1,406,250
TL_RR_95	531.199	156,250	294	2,968,750	5,589	2,968,750

The columns are tab delimited with the following data:
1.  Read ratio test phase
2.  Duration of phase (seconds)
3.  Vertex and edge write count during phase
4.  Write rate per second
5.  Vertex and edge read count during phase
6.  Read rate per second
7.  Hit rate: number of reads returning data

Please see the Wiki for addtional information and resources for running the load test on cluster deployments.
