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
	void inquiryTriggerReturnsCategoryQuestion() {
		ChatResponse response = mockChatService.generateResponse("新規問い合わせ");

		assertThat(response.reply()).isEqualTo("問い合わせのカテゴリを選んでください。");
		assertThat(response.components()).hasSize(1);
		ChoicesComponent choices = (ChoicesComponent) response.components().get(0);
		assertThat(choices.options()).containsExactly(
				"カテゴリ: 請求", "カテゴリ: 技術", "カテゴリ: アカウント", "カテゴリ: その他", "キャンセル");
	}

	@Test
	void categoryAnswerReturnsUrgencyQuestionNotCategoryReport() {
		ChatResponse response = mockChatService.generateResponse("カテゴリ: 請求");

		assertThat(response.reply()).isEqualTo("緊急度を選んでください。");
		assertThat(response.components()).hasSize(1);
		ChoicesComponent choices = (ChoicesComponent) response.components().get(0);
		assertThat(choices.options()).containsExactly("緊急度: 高", "緊急度: 中", "緊急度: 低", "キャンセル");
	}

	@Test
	void urgencyAnswerReturnsContentPrompt() {
		ChatResponse response = mockChatService.generateResponse("緊急度: 高");

		assertThat(response.reply()).isEqualTo("問い合わせ内容を入力してください。");
		assertThat(response.components()).isEmpty();
	}

	@Test
	void inquirySummaryReturnsConfirmationWithTableAndChoices() {
		ChatResponse response = mockChatService
				.generateResponse("カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている");

		assertThat(response.reply()).isEqualTo("以下の内容で登録してよろしいですか？");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("項目", "内容");
		assertThat(table.rows()).containsExactly(
				List.of("カテゴリ", "請求"),
				List.of("緊急度", "高"),
				List.of("内容", "請求額が二重になっている"));

		ChoicesComponent choices = (ChoicesComponent) response.components().get(1);
		assertThat(choices.options()).containsExactly("登録する", "やり直す", "キャンセル");
	}

	@Test
	void submitReturnsCompletionWithReceiptNumber() {
		ChatResponse response = mockChatService.generateResponse("登録する");

		assertThat(response.reply()).isEqualTo("問い合わせを受け付けました。受付番号: INQ-0001");
		assertThat(response.components()).isEmpty();
	}

	@Test
	void retryReturnsCategoryQuestionAgain() {
		ChatResponse response = mockChatService.generateResponse("やり直す");

		assertThat(response.reply()).isEqualTo("問い合わせのカテゴリを選んでください。");
		assertThat(response.components()).hasSize(1);
		assertThat(response.components().get(0)).isInstanceOf(ChoicesComponent.class);
	}

	@Test
	void cancelReturnsCancellationMessage() {
		ChatResponse response = mockChatService.generateResponse("キャンセル");

		assertThat(response.reply()).isEqualTo("問い合わせの登録を中止しました。");
		assertThat(response.components()).isEmpty();
	}

	@Test
	void nonMatchingMessageReturnsFallbackScenario() {
		ChatResponse response = mockChatService.generateResponse("こんにちは");

		assertThat(response.reply())
				.isEqualTo("『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。");
		assertThat(response.components()).isEmpty();
	}

}
