package com.example.chatbackend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockChatServiceTest {

	private final MockChatService mockChatService = new MockChatService();

	@Test
	void assigneeKeywordReturnsAssigneeScenario() {
		ChatResponse response = mockChatService.generateResponse("今月の問い合わせ件数を担当者別にまとめて");

		assertThat(response.reply()).isEqualTo("今月の担当者別の問い合わせ件数をまとめました。");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("担当者", "件数");
		assertThat(table.rows()).containsExactly(
				List.of("佐藤", "12"),
				List.of("鈴木", "9"),
				List.of("田中", "7"),
				List.of("高橋", "5"));

		BarChartComponent barChart = (BarChartComponent) response.components().get(1);
		assertThat(barChart.title()).isEqualTo("担当者別 問い合わせ件数");
		assertThat(barChart.labels()).containsExactly("佐藤", "鈴木", "田中", "高橋");
		assertThat(barChart.values()).containsExactly(12.0, 9.0, 7.0, 5.0);
	}

	@Test
	void categoryKeywordReturnsCategoryScenario() {
		ChatResponse response = mockChatService.generateResponse("カテゴリ別の問い合わせ件数を見せて");

		assertThat(response.reply()).isEqualTo("カテゴリ別の問い合わせ件数をまとめました。");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("カテゴリ", "件数");
		assertThat(table.rows()).containsExactly(
				List.of("請求", "15"),
				List.of("技術", "11"),
				List.of("アカウント", "6"),
				List.of("その他", "4"));

		BarChartComponent barChart = (BarChartComponent) response.components().get(1);
		assertThat(barChart.title()).isEqualTo("カテゴリ別 問い合わせ件数");
		assertThat(barChart.labels()).containsExactly("請求", "技術", "アカウント", "その他");
		assertThat(barChart.values()).containsExactly(15.0, 11.0, 6.0, 4.0);
	}

	@Test
	void typeKeywordAlsoReturnsCategoryScenario() {
		ChatResponse response = mockChatService.generateResponse("種別ごとの件数を教えて");

		assertThat(response.reply()).isEqualTo("カテゴリ別の問い合わせ件数をまとめました。");
		assertThat(response.components()).hasSize(2);
	}

	@Test
	void nonMatchingMessageReturnsFallbackScenario() {
		ChatResponse response = mockChatService.generateResponse("こんにちは");

		assertThat(response.reply())
				.isEqualTo("『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。");
		assertThat(response.components()).isEmpty();
	}

}
