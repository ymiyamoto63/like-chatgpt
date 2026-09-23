package com.example.chatbackend.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生成される OpenAPI 仕様（{@code api/openapi.yml}）のメタ情報。
 *
 * <p>servers を固定するのは、springdoc の既定では起動ポートが URL に入り、
 * 再生成のたびに差分が出てしまうため。
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI chatBackendOpenApi() {
		return new OpenAPI()
				.info(new Info().title("like-chatgpt API").version("v1"))
				.servers(List.of(new Server().url("/").description("同一オリジン")));
	}

}
