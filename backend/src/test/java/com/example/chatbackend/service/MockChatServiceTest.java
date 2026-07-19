package com.example.chatbackend.service;

import com.example.chatbackend.dto.chat.ChatResponse;
import com.example.chatbackend.dto.component.BarChartComponent;
import com.example.chatbackend.dto.component.ChoicesComponent;
import com.example.chatbackend.dto.component.FaqListComponent;
import com.example.chatbackend.dto.component.TableComponent;
import com.example.chatbackend.dto.component.TrendChartComponent;
import com.example.chatbackend.repository.FaqRepository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockChatServiceTest {

	private final MockChatService mockChatService = new MockChatService(new FaqRepository());

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
	void dailyKeywordReturnsDailyTrendScenario() {
		ChatResponse response = mockChatService.generateResponse("直近14日間の日別問い合わせ");

		assertThat(response.reply()).contains("直近14日間の日別問い合わせ件数をまとめました。");
		assertThat(response.components()).hasSize(1);

		TrendChartComponent trendChart = (TrendChartComponent) response.components().get(0);
		assertThat(trendChart.title()).isEqualTo("日別問い合わせ件数（直近14日間）");
		assertThat(trendChart.labels()).hasSize(14);
		assertThat(trendChart.labels().get(13))
				.isEqualTo(LocalDate.now().format(DateTimeFormatter.ofPattern("M/d")));
		assertThat(trendChart.values()).hasSize(14);
		assertThat(trendChart.average()).isEqualTo(6.4);
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
	void inquirySummaryReturnsFaqPresentationWithFaqListAndChoices() {
		ChatResponse response = mockChatService
				.generateResponse("カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている");

		assertThat(response.reply()).isEqualTo("ご登録の前に、関連するFAQをご確認ください。");
		assertThat(response.components()).hasSize(2);

		FaqListComponent faqList = (FaqListComponent) response.components().get(0);
		assertThat(faqList.titles()).containsExactly(
				"請求額が二重に表示される場合", "請求書の再発行方法", "支払い方法の変更手順");

		ChoicesComponent choices = (ChoicesComponent) response.components().get(1);
		assertThat(choices.options()).containsExactly("解決した", "解決しないので問い合わせる");
	}

	@Test
	void inquirySummaryForTechnicalCategoryReturnsTechnicalFaqs() {
		ChatResponse response = mockChatService
				.generateResponse("カテゴリ: 技術 / 緊急度: 中 / 内容: アプリが落ちる");

		FaqListComponent faqList = (FaqListComponent) response.components().get(0);
		assertThat(faqList.titles()).containsExactly(
				"ログインできない場合の対処法", "アプリが起動しない・強制終了する場合", "APIエラーコードの意味一覧");
	}

	@Test
	void inquirySummaryForAccountCategoryReturnsAccountFaqs() {
		ChatResponse response = mockChatService
				.generateResponse("カテゴリ: アカウント / 緊急度: 低 / 内容: メールアドレスを変更したい");

		FaqListComponent faqList = (FaqListComponent) response.components().get(0);
		assertThat(faqList.titles()).containsExactly(
				"メールアドレスの変更方法", "退会・アカウント削除の手続き", "複数端末でのログイン可否");
	}

	@Test
	void inquirySummaryForOtherCategoryReturnsOtherFaqs() {
		ChatResponse response = mockChatService
				.generateResponse("カテゴリ: その他 / 緊急度: 低 / 内容: 営業時間を知りたい");

		FaqListComponent faqList = (FaqListComponent) response.components().get(0);
		assertThat(faqList.titles()).containsExactly(
				"営業時間・問い合わせ窓口について", "利用規約・プライバシーポリシーの確認方法", "サービスの障害情報の確認方法");
	}

	@Test
	void faqAnswerReturnsFaqDetailWithFaqListAndChoices() {
		ChatResponse response = mockChatService.generateResponse("FAQ: 請求額が二重に表示される場合");

		assertThat(response.reply()).contains("決済処理の反映タイミングのずれ");
		assertThat(response.components()).hasSize(2);

		FaqListComponent faqList = (FaqListComponent) response.components().get(0);
		assertThat(faqList.titles()).containsExactly(
				"請求額が二重に表示される場合", "請求書の再発行方法", "支払い方法の変更手順");

		ChoicesComponent choices = (ChoicesComponent) response.components().get(1);
		assertThat(choices.options()).containsExactly("解決した", "解決しないので問い合わせる");
	}

	@Test
	void faqResolvedReturnsCompletionMessageWithNoComponents() {
		ChatResponse response = mockChatService.generateResponse("解決した");

		assertThat(response.reply()).isEqualTo("解決してよかったです。またご不明な点があればお気軽にお尋ねください。");
		assertThat(response.components()).isEmpty();
	}

	@Test
	void faqUnresolvedWithSummaryReturnsConfirmationWithTableAndChoices() {
		ChatResponse response = mockChatService.generateResponse(
				"解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている");

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

	@Test
	void cpuAlertCauseQuestionReturnsCpuCauseAnalysis() {
		ChatResponse response = mockChatService.generateResponse("Web-1のCPU使用率が高い原因を教えて");

		assertThat(response.reply()).isEqualTo("直近のCPU使用率上昇の要因を分析しました。バッチ処理プロセスの負荷が主な要因です。");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("プロセス", "CPU使用率");

		BarChartComponent barChart = (BarChartComponent) response.components().get(1);
		assertThat(barChart.title()).isEqualTo("プロセス別 CPU使用率");
		assertThat(barChart.labels()).containsExactly("batch-worker", "api-server", "log-agent", "other");
		assertThat(barChart.values()).containsExactly(42.0, 28.0, 15.0, 7.0);
	}

	@Test
	void memoryAlertCauseQuestionReturnsMemoryCauseAnalysis() {
		ChatResponse response = mockChatService.generateResponse("DB Primaryのメモリ使用率が高い原因を教えて");

		assertThat(response.reply()).isEqualTo("直近のメモリ使用率上昇の要因を分析しました。キャッシュ層のメモリ消費増加が主な要因です。");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("プロセス", "メモリ使用率");

		BarChartComponent barChart = (BarChartComponent) response.components().get(1);
		assertThat(barChart.title()).isEqualTo("プロセス別 メモリ使用率");
		assertThat(barChart.labels()).containsExactly("cache-layer", "api-server", "session-store", "other");
		assertThat(barChart.values()).containsExactly(38.0, 25.0, 19.0, 10.0);
	}

	@Test
	void trafficAlertCauseQuestionReturnsTrafficCauseAnalysis() {
		ChatResponse response = mockChatService.generateResponse("LB〜Web-1間のトラフィック使用率が高い原因を教えて");

		assertThat(response.reply()).isEqualTo("直近のトラフィック増加の要因を分析しました。大容量ファイル転送による帯域占有が主な要因です。");
		assertThat(response.components()).hasSize(2);

		TableComponent table = (TableComponent) response.components().get(0);
		assertThat(table.columns()).containsExactly("通信種別", "帯域占有率");

		BarChartComponent barChart = (BarChartComponent) response.components().get(1);
		assertThat(barChart.title()).isEqualTo("通信種別別 帯域占有率");
		assertThat(barChart.labels()).containsExactly("file-transfer", "api-calls", "db-replication", "other");
		assertThat(barChart.values()).containsExactly(45.0, 27.0, 18.0, 10.0);
	}

	@Test
	void closeAlertLabelReturnsAcknowledgementWithNoComponents() {
		ChatResponse response = mockChatService.generateResponse("閉じる");

		assertThat(response.reply()).isEqualTo("承知しました。");
		assertThat(response.components()).isEmpty();
	}

}
