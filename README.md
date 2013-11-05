titan-loadtest
==============

Distributed and peer-based load test for [Titan](http://thinkaurelius.github.io/titan/ "Distributed Graph Database").  Each node loads independently without seperate load test servers.  Currently supporting embedded [Apache Cassandra](http://cassandra.apache.org/ "Peer-to-peer Shared Nothing Database"), and will most likely expand to include other persistency soon.

This project builds an executable jar using [Maven Shade](http://maven.apache.org/plugins/maven-shade-plugin/shade-mojo.html) plugin.  Execute the following command to build the jar: `mvn package`

If you want to run with different versions of Titan or Cassandra, just change the [Maven pom.xml](http://maven.apache.org/guides/introduction/introduction-to-the-pom.html "Project Object Model") dependencies and the new versions will be packaged in a new jar.  This makes it easy to test multiple versions of Titan and Cassandra on the same hardware without risk of dependency collisions polluting the results.

To run the load test, execute with the `java -jar` command:

	$ java -javaagent:loadtest-0.0.1-SNAPSHOT.jar -Xmx4g -Xms4g -jar loadtest-0.0.1-SNAPSHOT.jar cassandra-disk.yaml 1000000000 10 0 12 64

**Please note:** using the `-javaagent` is mandatory, as Cassandra requires [Jamm](https://github.com/jbellis/jamm "Java Agent for Memory Measurements") to montior heap usage while determining when to flush memtables.  Without Jamm, under heavy load Cassandra will likely cause the JVM to spiral into endless GC cycles.

The parameters to the jar:

1. Location of the cassandra.yaml
2. Vertex count to create across the entire cluster
3. Node count (starting from 1)
4. Node id (must be between 0 and node count - 1)
5. Worker thread count per node
6. Commit size
 
When run, the jar will test various read ratios and will report results for each ratio mix.  Currently, the following read / write mixes are supported:

<table>
<tr>
	<th>Phase name</th>
	<th>Description</th>
</tr>
<tr>
    <td>TL_RR_00</td>
    <td>100% writes</td>
</tr>
<tr>
    <td>TL_RR_10</td>
    <td>90% writes, 10% reads</td>
</tr>
<tr>
    <td>TL_RR_25</td>
    <td>75% writes, 25% reads</td>
</tr>
<tr>
    <td>TL_RR_50</td>
    <td>50 / 50</td>
</tr>
<tr>
    <td>TL_RR_75</td>
    <td>25% writes, 75% reads</td>
</tr>
<tr>
    <td>TL_RR_90</td>
    <td>10% writes, 90% reads</td>
</tr>
<tr>
    <td>TL_RR_95</td>
    <td>5% writes, 95% reads</td>
</tr>
</table>

For example, the results of the command on a single machine will look like:

	$ java -javaagent:target/loadtest-0.0.1-SNAPSHOT.jar -Xmx2g -Xms2g -jar target/loadtest-0.0.1-SNAPSHOT.jar src/test/resources/cassandra-local.yaml 10000000 1 0 3 64 > results.out
	cat results.out | grep TL_RR
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

Please see the Wiki for additional information and resources for running the load test on cluster deployments.
