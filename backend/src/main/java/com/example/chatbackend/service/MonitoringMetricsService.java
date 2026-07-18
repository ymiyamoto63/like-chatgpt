package com.example.chatbackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Service;

/**
 * 固定トポロジーのメトリクスをランダムウォーク＋周期スパイクで生成する。
 *
 * このクラスはSpringのシングルトンBeanとしてミュータブルな前回値状態をインスタンスフィールドに保持する。
 * これは本プロジェクトの「モックデータ生成はステートレス」という既存方針（{@link MockChatService} 参照）の
 * 明示的な例外である。状態はサーバ再起動でリセットされてよい（永続化しない）。
 *
 * 「1回の {@link #getSnapshot()} 呼び出し＝1ティック」という単純化を採用しており、実時刻ベースのスケジューラは
 * 持たない。スパイクの間隔・持続時間はすべてティック数で管理する（フロントエンドが5秒間隔でポーリングし続ける
 * 前提で、設計書記載の秒数感覚（平均60秒に1回・15秒持続）に一致する）。
 */
@Service
public class MonitoringMetricsService {

	private static final double RANDOM_WALK_STEP = 3.0;
	private static final double MEAN_REVERSION_FACTOR = 0.05;
	private static final int SPIKE_MEAN_INTERVAL_TICKS = 1;
	private static final int SPIKE_INTERVAL_JITTER_TICKS = 0;
	private static final int SPIKE_DURATION_TICKS = 3;
	private static final double SPIKE_PEAK_MIN = 90.0;
	private static final double SPIKE_PEAK_MAX = 98.0;
	private static final double SPIKE_CONVERGENCE_FACTOR = 0.9;
	private static final double SPIKE_DECAY_FACTOR = 0.4;
	private static final int SPIKE_END_DECAY_TICKS = 2;

	private record NodeDefinition(String id, String label, double baselineCpu, double baselineMemory) {
	}

	private record EdgeDefinition(String id, String sourceId, String targetId, double baselineBandwidth) {
	}

	private enum SeriesType {
		NODE_CPU, NODE_MEMORY, EDGE_BANDWIDTH
	}

	private record SeriesRef(SeriesType type, String id) {
	}

	private static final List<NodeDefinition> NODE_DEFINITIONS = List.of(
			new NodeDefinition("internet", "Internet", 20.0, 15.0),
			new NodeDefinition("lb", "LB", 35.0, 30.0),
			new NodeDefinition("web-1", "Web-1", 40.0, 45.0),
			new NodeDefinition("web-2", "Web-2", 40.0, 45.0),
			new NodeDefinition("app-1", "App-1", 45.0, 50.0),
			new NodeDefinition("app-2", "App-2", 45.0, 50.0),
			new NodeDefinition("cache", "Cache", 30.0, 55.0),
			new NodeDefinition("db-primary", "DB Primary", 50.0, 60.0),
			new NodeDefinition("db-replica", "DB Replica", 35.0, 55.0));

	private static final List<EdgeDefinition> EDGE_DEFINITIONS = List.of(
			new EdgeDefinition("internet-lb", "internet", "lb", 30.0),
			new EdgeDefinition("lb-web1", "lb", "web-1", 35.0),
			new EdgeDefinition("lb-web2", "lb", "web-2", 35.0),
			new EdgeDefinition("web1-app1", "web-1", "app-1", 30.0),
			new EdgeDefinition("web1-app2", "web-1", "app-2", 20.0),
			new EdgeDefinition("web2-app1", "web-2", "app-1", 20.0),
			new EdgeDefinition("web2-app2", "web-2", "app-2", 30.0),
			new EdgeDefinition("app1-cache", "app-1", "cache", 25.0),
			new EdgeDefinition("app2-cache", "app-2", "cache", 25.0),
			new EdgeDefinition("app1-dbprimary", "app-1", "db-primary", 30.0),
			new EdgeDefinition("app2-dbprimary", "app-2", "db-primary", 30.0),
			new EdgeDefinition("dbprimary-dbreplica", "db-primary", "db-replica", 40.0));

	private static final class MetricState {
		private final double baseline;
		private double current;
		private int decayTicksRemaining;

		private MetricState(double baseline) {
			this.baseline = baseline;
			this.current = baseline;
		}
	}

	private final Random random = new Random();
	private final Map<String, MetricState> nodeCpuStates = new LinkedHashMap<>();
	private final Map<String, MetricState> nodeMemoryStates = new LinkedHashMap<>();
	private final Map<String, MetricState> edgeBandwidthStates = new LinkedHashMap<>();
	private final List<SeriesRef> allSeriesRefs = new ArrayList<>();

	private long tickCount;
	private String spikeNodeId;
	private String spikeEdgeId;
	private double spikePeak;
	private long spikeEndsAtTick = -1;
	private long nextSpikeEligibleAtTick;

	public MonitoringMetricsService() {
		for (NodeDefinition definition : NODE_DEFINITIONS) {
			nodeCpuStates.put(definition.id(), new MetricState(definition.baselineCpu()));
			nodeMemoryStates.put(definition.id(), new MetricState(definition.baselineMemory()));
			allSeriesRefs.add(new SeriesRef(SeriesType.NODE_CPU, definition.id()));
			allSeriesRefs.add(new SeriesRef(SeriesType.NODE_MEMORY, definition.id()));
		}
		for (EdgeDefinition definition : EDGE_DEFINITIONS) {
			edgeBandwidthStates.put(definition.id(), new MetricState(definition.baselineBandwidth()));
			allSeriesRefs.add(new SeriesRef(SeriesType.EDGE_BANDWIDTH, definition.id()));
		}
		nextSpikeEligibleAtTick = randomSpikeIntervalTicks();
	}

	public synchronized MonitoringSnapshot getSnapshot() {
		tickCount++;
		processSpikeLifecycle();
		updateAllSeries();
		return buildSnapshot();
	}

	private void processSpikeLifecycle() {
		if (spikeEndsAtTick >= 0 && tickCount >= spikeEndsAtTick) {
			endActiveSpike();
		}
		if (spikeEndsAtTick < 0 && tickCount >= nextSpikeEligibleAtTick) {
			startNewSpike();
		}
	}

	private void startNewSpike() {
		SeriesRef selected = allSeriesRefs.get(random.nextInt(allSeriesRefs.size()));
		if (selected.type() == SeriesType.EDGE_BANDWIDTH) {
			spikeEdgeId = selected.id();
		} else {
			spikeNodeId = selected.id();
		}
		spikePeak = SPIKE_PEAK_MIN + random.nextDouble() * (SPIKE_PEAK_MAX - SPIKE_PEAK_MIN);
		spikeEndsAtTick = tickCount + SPIKE_DURATION_TICKS;
		nextSpikeEligibleAtTick = tickCount + randomSpikeIntervalTicks();
	}

	private void endActiveSpike() {
		if (spikeNodeId != null) {
			nodeCpuStates.get(spikeNodeId).decayTicksRemaining = SPIKE_END_DECAY_TICKS;
			nodeMemoryStates.get(spikeNodeId).decayTicksRemaining = SPIKE_END_DECAY_TICKS;
		}
		if (spikeEdgeId != null) {
			edgeBandwidthStates.get(spikeEdgeId).decayTicksRemaining = SPIKE_END_DECAY_TICKS;
		}
		spikeNodeId = null;
		spikeEdgeId = null;
		spikeEndsAtTick = -1;
	}

	private int randomSpikeIntervalTicks() {
		int jitter = random.nextInt(2 * SPIKE_INTERVAL_JITTER_TICKS + 1) - SPIKE_INTERVAL_JITTER_TICKS;
		return SPIKE_MEAN_INTERVAL_TICKS + jitter;
	}

	private void updateAllSeries() {
		for (Map.Entry<String, MetricState> entry : nodeCpuStates.entrySet()) {
			updateState(entry.getValue(), entry.getKey().equals(spikeNodeId));
		}
		for (Map.Entry<String, MetricState> entry : nodeMemoryStates.entrySet()) {
			updateState(entry.getValue(), entry.getKey().equals(spikeNodeId));
		}
		for (Map.Entry<String, MetricState> entry : edgeBandwidthStates.entrySet()) {
			updateState(entry.getValue(), entry.getKey().equals(spikeEdgeId));
		}
	}

	private void updateState(MetricState state, boolean isSpikeTarget) {
		if (isSpikeTarget) {
			double noise = (random.nextDouble() * 2 - 1);
			state.current += (spikePeak - state.current) * SPIKE_CONVERGENCE_FACTOR + noise;
		} else if (state.decayTicksRemaining > 0) {
			state.current -= (state.current - state.baseline) * SPIKE_DECAY_FACTOR;
			state.decayTicksRemaining--;
		} else {
			double step = (random.nextDouble() * 2 - 1) * RANDOM_WALK_STEP;
			state.current += step + (state.baseline - state.current) * MEAN_REVERSION_FACTOR;
		}
		state.current = clamp(state.current);
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(100.0, value));
	}

	private MonitoringSnapshot buildSnapshot() {
		List<MonitoringNode> nodes = new ArrayList<>();
		for (NodeDefinition definition : NODE_DEFINITIONS) {
			double cpu = nodeCpuStates.get(definition.id()).current;
			double memory = nodeMemoryStates.get(definition.id()).current;
			nodes.add(new MonitoringNode(definition.id(), definition.label(), cpu, memory));
		}
		List<MonitoringEdge> edges = new ArrayList<>();
		for (EdgeDefinition definition : EDGE_DEFINITIONS) {
			double bandwidth = edgeBandwidthStates.get(definition.id()).current;
			edges.add(new MonitoringEdge(definition.id(), definition.sourceId(), definition.targetId(), bandwidth));
		}
		return new MonitoringSnapshot(nodes, edges);
	}

}
