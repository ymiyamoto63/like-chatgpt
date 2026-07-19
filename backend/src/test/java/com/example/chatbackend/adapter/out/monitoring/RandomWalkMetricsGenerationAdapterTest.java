package com.example.chatbackend.adapter.out.monitoring;

import com.example.chatbackend.domain.monitoring.MonitoringEdge;
import com.example.chatbackend.domain.monitoring.MonitoringNode;
import com.example.chatbackend.domain.monitoring.MonitoringSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomWalkMetricsGenerationAdapterTest {

	private final RandomWalkMetricsGenerationAdapter monitoringMetricsService = new RandomWalkMetricsGenerationAdapter();

	@Test
	void firstSnapshotReturnsFixedTopologyWithAllMetricsInRange() {
		MonitoringSnapshot snapshot = monitoringMetricsService.generateSnapshot();

		assertThat(snapshot.nodes()).hasSize(9);
		assertThat(snapshot.edges()).hasSize(12);

		Set<String> nodeIds = new HashSet<>();
		for (MonitoringNode node : snapshot.nodes()) {
			nodeIds.add(node.id());
			assertThat(node.cpuPercent()).isBetween(0.0, 100.0);
			assertThat(node.memoryPercent()).isBetween(0.0, 100.0);
		}
		assertThat(nodeIds).hasSize(9);

		for (MonitoringEdge edge : snapshot.edges()) {
			assertThat(nodeIds).contains(edge.sourceId());
			assertThat(nodeIds).contains(edge.targetId());
			assertThat(edge.bandwidthPercent()).isBetween(0.0, 100.0);
		}
	}

	@Test
	void firstSnapshotMatchesFixedTopologyIdsLabelsAndConnections() {
		Map<String, String> expectedNodeLabels = new LinkedHashMap<>();
		expectedNodeLabels.put("internet", "Internet");
		expectedNodeLabels.put("lb", "LB");
		expectedNodeLabels.put("web-1", "Web-1");
		expectedNodeLabels.put("web-2", "Web-2");
		expectedNodeLabels.put("app-1", "App-1");
		expectedNodeLabels.put("app-2", "App-2");
		expectedNodeLabels.put("cache", "Cache");
		expectedNodeLabels.put("db-primary", "DB Primary");
		expectedNodeLabels.put("db-replica", "DB Replica");

		Map<String, String[]> expectedEdgeConnections = new LinkedHashMap<>();
		expectedEdgeConnections.put("internet-lb", new String[] {"internet", "lb"});
		expectedEdgeConnections.put("lb-web1", new String[] {"lb", "web-1"});
		expectedEdgeConnections.put("lb-web2", new String[] {"lb", "web-2"});
		expectedEdgeConnections.put("web1-app1", new String[] {"web-1", "app-1"});
		expectedEdgeConnections.put("web1-app2", new String[] {"web-1", "app-2"});
		expectedEdgeConnections.put("web2-app1", new String[] {"web-2", "app-1"});
		expectedEdgeConnections.put("web2-app2", new String[] {"web-2", "app-2"});
		expectedEdgeConnections.put("app1-cache", new String[] {"app-1", "cache"});
		expectedEdgeConnections.put("app2-cache", new String[] {"app-2", "cache"});
		expectedEdgeConnections.put("app1-dbprimary", new String[] {"app-1", "db-primary"});
		expectedEdgeConnections.put("app2-dbprimary", new String[] {"app-2", "db-primary"});
		expectedEdgeConnections.put("dbprimary-dbreplica", new String[] {"db-primary", "db-replica"});

		MonitoringSnapshot snapshot = monitoringMetricsService.generateSnapshot();

		assertThat(snapshot.nodes()).hasSize(expectedNodeLabels.size());
		for (MonitoringNode node : snapshot.nodes()) {
			assertThat(expectedNodeLabels).containsKey(node.id());
			assertThat(node.label()).isEqualTo(expectedNodeLabels.get(node.id()));
		}
		assertThat(snapshot.nodes().stream().map(MonitoringNode::id))
				.containsExactlyInAnyOrderElementsOf(expectedNodeLabels.keySet());

		assertThat(snapshot.edges()).hasSize(expectedEdgeConnections.size());
		for (MonitoringEdge edge : snapshot.edges()) {
			assertThat(expectedEdgeConnections).containsKey(edge.id());
			String[] expected = expectedEdgeConnections.get(edge.id());
			assertThat(edge.sourceId()).isEqualTo(expected[0]);
			assertThat(edge.targetId()).isEqualTo(expected[1]);
		}
		assertThat(snapshot.edges().stream().map(MonitoringEdge::id))
				.containsExactlyInAnyOrderElementsOf(expectedEdgeConnections.keySet());
	}

	@Test
	void repeatedCallsVaryWhileStayingWithinRange() {
		List<MonitoringSnapshot> snapshots = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			MonitoringSnapshot snapshot = monitoringMetricsService.generateSnapshot();
			for (MonitoringNode node : snapshot.nodes()) {
				assertThat(node.cpuPercent()).isBetween(0.0, 100.0);
				assertThat(node.memoryPercent()).isBetween(0.0, 100.0);
			}
			for (MonitoringEdge edge : snapshot.edges()) {
				assertThat(edge.bandwidthPercent()).isBetween(0.0, 100.0);
			}
			snapshots.add(snapshot);
		}

		MonitoringSnapshot first = snapshots.get(0);
		MonitoringSnapshot last = snapshots.get(snapshots.size() - 1);
		boolean anySeriesChanged = false;
		for (int i = 0; i < first.nodes().size(); i++) {
			if (first.nodes().get(i).cpuPercent() != last.nodes().get(i).cpuPercent()
					|| first.nodes().get(i).memoryPercent() != last.nodes().get(i).memoryPercent()) {
				anySeriesChanged = true;
			}
		}
		for (int i = 0; i < first.edges().size(); i++) {
			if (first.edges().get(i).bandwidthPercent() != last.edges().get(i).bandwidthPercent()) {
				anySeriesChanged = true;
			}
		}
		assertThat(anySeriesChanged).isTrue();
	}

}
