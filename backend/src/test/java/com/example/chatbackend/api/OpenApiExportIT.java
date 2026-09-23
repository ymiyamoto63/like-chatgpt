package com.example.chatbackend.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 実行中のアプリから OpenAPI 仕様を書き出す（{@code api/openapi.yml}）。
 *
 * <p>書き出した仕様は quality-gate の M-09（API の破壊的変更）の入力になる。
 * CI は比較元コミットの {@code api/openapi.yml} と oasdiff で比べるため、
 * 生成物はコミットし、再生成して差分が無いことを CI で検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiExportIT {

	private static final Path OUTPUT = Path.of("..", "api", "openapi.yml");

	@LocalServerPort
	int port;

	@Test
	void exportsOpenApiSpec() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:%d/v3/api-docs.yaml".formatted(port)))
				.GET()
				.build();
		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		String yaml = response.body();
		assertThat(yaml).contains("openapi:");
		assertThat(yaml).contains("/api/chat");
		assertThat(yaml).contains("/api/suggest");
		assertThat(yaml).contains("/api/monitoring");

		Files.createDirectories(OUTPUT.getParent());
		Files.writeString(OUTPUT, yaml, StandardCharsets.UTF_8);
	}
}
