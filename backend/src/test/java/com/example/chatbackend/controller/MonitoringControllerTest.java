package com.example.chatbackend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MonitoringControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void snapshotReturnsFixedTopologyWithMetricsInRange() throws Exception {
		mockMvc.perform(get("/api/monitoring/snapshot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nodes.length()", is(9)))
				.andExpect(jsonPath("$.edges.length()", is(12)))
				.andExpect(jsonPath("$.nodes[0].id", instanceOf(String.class)))
				.andExpect(jsonPath("$.nodes[0].label", instanceOf(String.class)))
				.andExpect(jsonPath("$.nodes[0].cpuPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.nodes[0].cpuPercent", lessThanOrEqualTo(100.0)))
				.andExpect(jsonPath("$.nodes[0].memoryPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.nodes[0].memoryPercent", lessThanOrEqualTo(100.0)))
				.andExpect(jsonPath("$.edges[0].id", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[0].sourceId", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[0].targetId", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[0].bandwidthPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.edges[0].bandwidthPercent", lessThanOrEqualTo(100.0)))
				// 末尾要素（nodes[8]/edges[11]）も検証し、先頭要素だけでなく配列全体でフィールドが網羅されていることを確認する
				.andExpect(jsonPath("$.nodes[8].id", instanceOf(String.class)))
				.andExpect(jsonPath("$.nodes[8].label", instanceOf(String.class)))
				.andExpect(jsonPath("$.nodes[8].cpuPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.nodes[8].cpuPercent", lessThanOrEqualTo(100.0)))
				.andExpect(jsonPath("$.nodes[8].memoryPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.nodes[8].memoryPercent", lessThanOrEqualTo(100.0)))
				.andExpect(jsonPath("$.edges[11].id", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[11].sourceId", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[11].targetId", instanceOf(String.class)))
				.andExpect(jsonPath("$.edges[11].bandwidthPercent", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.edges[11].bandwidthPercent", lessThanOrEqualTo(100.0)));
	}

}
