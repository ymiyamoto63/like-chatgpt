package com.example.chatbackend;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MockChatService {

	private static final String ASSIGNEE_KEYWORD = "担当";
	private static final String CATEGORY_KEYWORD = "カテゴリ";
	private static final String TYPE_KEYWORD = "種別";
	private static final String DAILY_KEYWORD = "日別";

	private static final int DAILY_TREND_DAYS = 14;
	private static final List<Double> DAILY_TREND_VALUES = List.of(
			8.0, 5.0, 3.0, 9.0, 6.0, 4.0, 7.0, 10.0, 6.0, 5.0, 8.0, 3.0, 6.0, 9.0);
	private static final DateTimeFormatter DAILY_TREND_LABEL_FORMAT = DateTimeFormatter.ofPattern("M/d");

	private static final String INQUIRY_TRIGGER = "新規問い合わせ";
	private static final String CATEGORY_ANSWER_PREFIX = "カテゴリ: ";
	private static final String URGENCY_ANSWER_PREFIX = "緊急度: ";
	private static final String SUMMARY_URGENCY_MARKER = " / 緊急度: ";
	private static final String SUMMARY_CONTENT_MARKER = " / 内容: ";
	private static final String SUBMIT_LABEL = "登録する";
	private static final String RETRY_LABEL = "やり直す";
	private static final String CANCEL_LABEL = "キャンセル";
	private static final String RECEIPT_NUMBER = "INQ-0001";

	// モニタリング連携アラートのキーワード。値は frontend/src/constants/monitoringAlert.ts の
	// 同名定数と一致させること（言語をまたぐため自動同期はできない）
	private static final String CAUSE_ANALYSIS_KEYWORD = "使用率が高い原因を教えて";
	private static final String CPU_METRIC_KEYWORD = "CPU";
	private static final String MEMORY_METRIC_KEYWORD = "メモリ";
	private static final String TRAFFIC_METRIC_KEYWORD = "トラフィック";
	private static final String CLOSE_ALERT_LABEL = "閉じる";

	private static final String FAQ_ANSWER_PREFIX = "FAQ: ";
	private static final String FAQ_RESOLVED_LABEL = "解決した";
	private static final String FAQ_UNRESOLVED_LABEL = "解決しないので問い合わせる";
	private static final String FAQ_UNRESOLVED_PREFIX = FAQ_UNRESOLVED_LABEL + " / ";

	private record FaqEntry(String category, String title, String body) {
	}

	private static final List<FaqEntry> FAQ_ENTRIES = List.of(
			// 請求
			new FaqEntry("請求", "請求額が二重に表示される場合",
					"決済処理の反映タイミングのずれにより、請求額が一時的に二重表示されることがあります。"
							+ "通常は3営業日以内に自動で修正されますが、修正後も解消しない場合はお手数ですが問い合わせください。"),
			new FaqEntry("請求", "請求書の再発行方法",
					"マイページの「請求・お支払い」メニューから対象月を選び、「請求書を再発行」ボタンを押すとPDFがダウンロードできます。"
							+ "過去12か月分の請求書を再発行可能です。"),
			new FaqEntry("請求", "支払い方法の変更手順",
					"マイページの「お支払い方法の変更」から新しいクレジットカードまたは銀行口座を登録できます。"
							+ "変更内容は次回の請求サイクルから適用されます。"),
			// 技術
			new FaqEntry("技術", "ログインできない場合の対処法",
					"パスワードの再設定をお試しください。それでもログインできない場合は、ブラウザのキャッシュ削除、"
							+ "別ブラウザでの再試行をお願いします。改善しない場合はアカウントロックの可能性がありますので問い合わせください。"),
			new FaqEntry("技術", "アプリが起動しない・強制終了する場合",
					"アプリを最新バージョンに更新し、端末を再起動してください。改善しない場合は一度アプリをアンインストールし、"
							+ "再インストールをお試しください。"),
			new FaqEntry("技術", "APIエラーコードの意味一覧",
					"400番台はリクエスト内容の誤り、401はAPIキーの認証エラー、429はレート制限超過、"
							+ "500番台はサーバー側の一時的な障害を示します。詳細なエラーコード一覧は開発者ドキュメントをご確認ください。"),
			// アカウント
			new FaqEntry("アカウント", "メールアドレスの変更方法",
					"マイページの「アカウント設定」から新しいメールアドレスを入力し、届いた確認メールのリンクを開くと変更が完了します。"),
			new FaqEntry("アカウント", "退会・アカウント削除の手続き",
					"マイページの「アカウント設定」内「退会手続き」から申請できます。申請後、保存データは"
							+ "所定の保持期間を経て完全に削除されます。"),
			new FaqEntry("アカウント", "複数端末でのログイン可否",
					"同一アカウントでスマートフォン・PCなど複数端末に同時ログインできます。ただし契約プランによっては"
							+ "同時セッション数に上限がある場合があります。"),
			// その他
			new FaqEntry("その他", "営業時間・問い合わせ窓口について",
					"サポート窓口の受付時間は平日9:00〜18:00です。土日祝日および年末年始は休業となります。"
							+ "緊急の障害に関しては障害情報ページをご確認ください。"),
			new FaqEntry("その他", "利用規約・プライバシーポリシーの確認方法",
					"最新の利用規約・プライバシーポリシーはWebサイトのフッターにある「規約」リンクからいつでもご確認いただけます。"),
			new FaqEntry("その他", "サービスの障害情報の確認方法",
					"現在発生中の障害や復旧予定は、サービスステータスページで随時更新しています。"
							+ "重大な障害の際はメールでも通知いたします。"));

	private static final Map<String, FaqEntry> FAQ_BY_TITLE =
			FAQ_ENTRIES.stream().collect(Collectors.toMap(FaqEntry::title, e -> e));

	private static final Map<String, List<String>> FAQ_TITLES_BY_CATEGORY =
			FAQ_ENTRIES.stream().collect(Collectors.groupingBy(
					FaqEntry::category, LinkedHashMap::new,
					Collectors.mapping(FaqEntry::title, Collectors.toList())));

	public ChatResponse generateResponse(String message) {
		// 新規問い合わせフローの定型文は「カテゴリ」等の既存キーワードを含むため、
		// レポートシナリオより先に判定する
		ChatResponse inquiryResponse = inquiryFlowResponse(message);
		if (inquiryResponse != null) {
			return inquiryResponse;
		}
		ChatResponse alertResponse = alertFlowResponse(message);
		if (alertResponse != null) {
			return alertResponse;
		}
		if (message.contains(DAILY_KEYWORD)) {
			return dailyTrendScenario();
		}
		if (message.contains(ASSIGNEE_KEYWORD)) {
			return assigneeScenario();
		}
		if (message.contains(CATEGORY_KEYWORD) || message.contains(TYPE_KEYWORD)) {
			return categoryScenario();
		}
		return fallbackScenario();
	}

	private ChatResponse inquiryFlowResponse(String message) {
		if (message.equals(INQUIRY_TRIGGER) || message.equals(RETRY_LABEL)) {
			return inquiryCategoryQuestion();
		}
		if (message.startsWith(FAQ_ANSWER_PREFIX)) {
			return faqDetailResponse(message);
		}
		if (message.equals(FAQ_RESOLVED_LABEL)) {
			return faqResolvedCompletion();
		}
		if (message.startsWith(FAQ_UNRESOLVED_PREFIX)) {
			InquirySummary summary = parseInquirySummary(message.substring(FAQ_UNRESOLVED_PREFIX.length()));
			return summary != null ? inquiryConfirmation(summary) : null;
		}
		if (message.startsWith(CATEGORY_ANSWER_PREFIX)) {
			InquirySummary summary = parseInquirySummary(message);
			if (summary != null) {
				return faqPresentation(summary);
			}
			return inquiryUrgencyQuestion();
		}
		if (message.startsWith(URGENCY_ANSWER_PREFIX)) {
			return inquiryContentPrompt();
		}
		if (message.equals(SUBMIT_LABEL)) {
			return inquiryCompletion();
		}
		if (message.equals(CANCEL_LABEL)) {
			return inquiryCancellation();
		}
		return null;
	}

	private ChatResponse inquiryCategoryQuestion() {
		return new ChatResponse(
				"問い合わせのカテゴリを選んでください。",
				List.of(new ChoicesComponent(List.of(
						CATEGORY_ANSWER_PREFIX + "請求",
						CATEGORY_ANSWER_PREFIX + "技術",
						CATEGORY_ANSWER_PREFIX + "アカウント",
						CATEGORY_ANSWER_PREFIX + "その他",
						CANCEL_LABEL))));
	}

	private ChatResponse inquiryUrgencyQuestion() {
		return new ChatResponse(
				"緊急度を選んでください。",
				List.of(new ChoicesComponent(List.of(
						URGENCY_ANSWER_PREFIX + "高",
						URGENCY_ANSWER_PREFIX + "中",
						URGENCY_ANSWER_PREFIX + "低",
						CANCEL_LABEL))));
	}

	private ChatResponse inquiryContentPrompt() {
		return new ChatResponse("問い合わせ内容を入力してください。", List.of());
	}

	private record InquirySummary(String category, String urgency, String content) {
	}

	private InquirySummary parseInquirySummary(String message) {
		if (!message.startsWith(CATEGORY_ANSWER_PREFIX)) {
			return null;
		}
		int urgencyIndex = message.indexOf(SUMMARY_URGENCY_MARKER);
		int contentIndex = message.indexOf(SUMMARY_CONTENT_MARKER);
		if (urgencyIndex < 0 || contentIndex < 0 || urgencyIndex >= contentIndex) {
			return null;
		}
		String category = message.substring(CATEGORY_ANSWER_PREFIX.length(), urgencyIndex);
		String urgency = message.substring(urgencyIndex + SUMMARY_URGENCY_MARKER.length(), contentIndex);
		String content = message.substring(contentIndex + SUMMARY_CONTENT_MARKER.length());
		return new InquirySummary(category, urgency, content);
	}

	private ChatResponse inquiryConfirmation(InquirySummary summary) {
		TableComponent table = new TableComponent(
				List.of("項目", "内容"),
				List.of(
						List.of("カテゴリ", summary.category()),
						List.of("緊急度", summary.urgency()),
						List.of("内容", summary.content())));
		ChoicesComponent choices = new ChoicesComponent(
				List.of(SUBMIT_LABEL, RETRY_LABEL, CANCEL_LABEL));
		return new ChatResponse("以下の内容で登録してよろしいですか？", List.of(table, choices));
	}

	private ChatResponse faqPresentation(InquirySummary summary) {
		List<String> titles = FAQ_TITLES_BY_CATEGORY.getOrDefault(summary.category(), List.of());
		return new ChatResponse(
				"ご登録の前に、関連するFAQをご確認ください。",
				List.of(new FaqListComponent(titles),
						new ChoicesComponent(List.of(FAQ_RESOLVED_LABEL, FAQ_UNRESOLVED_LABEL))));
	}

	private ChatResponse faqDetailResponse(String message) {
		String title = message.substring(FAQ_ANSWER_PREFIX.length());
		FaqEntry entry = FAQ_BY_TITLE.get(title);
		if (entry == null) {
			return null;
		}
		List<String> titles = FAQ_TITLES_BY_CATEGORY.getOrDefault(entry.category(), List.of());
		return new ChatResponse(
				entry.body(),
				List.of(new FaqListComponent(titles),
						new ChoicesComponent(List.of(FAQ_RESOLVED_LABEL, FAQ_UNRESOLVED_LABEL))));
	}

	private ChatResponse faqResolvedCompletion() {
		return new ChatResponse(
				"解決してよかったです。またご不明な点があればお気軽にお尋ねください。", List.of());
	}

	private ChatResponse inquiryCompletion() {
		return new ChatResponse(
				"問い合わせを受け付けました。受付番号: " + RECEIPT_NUMBER,
				List.of());
	}

	private ChatResponse inquiryCancellation() {
		return new ChatResponse("問い合わせの登録を中止しました。", List.of());
	}

	private ChatResponse assigneeScenario() {
		TableComponent table = new TableComponent(
				List.of("担当者", "件数"),
				List.of(
						List.of("佐藤", "12"),
						List.of("鈴木", "9"),
						List.of("田中", "7"),
						List.of("高橋", "5")));
		BarChartComponent barChart = new BarChartComponent(
				"担当者別 問い合わせ件数",
				List.of("佐藤", "鈴木", "田中", "高橋"),
				List.of(12.0, 9.0, 7.0, 5.0));
		return new ChatResponse("今月の担当者別の問い合わせ件数をまとめました。", List.of(table, barChart));
	}

	private ChatResponse categoryScenario() {
		TableComponent table = new TableComponent(
				List.of("カテゴリ", "件数"),
				List.of(
						List.of("請求", "15"),
						List.of("技術", "11"),
						List.of("アカウント", "6"),
						List.of("その他", "4")));
		BarChartComponent barChart = new BarChartComponent(
				"カテゴリ別 問い合わせ件数",
				List.of("請求", "技術", "アカウント", "その他"),
				List.of(15.0, 11.0, 6.0, 4.0));
		return new ChatResponse("カテゴリ別の問い合わせ件数をまとめました。", List.of(table, barChart));
	}

	private ChatResponse dailyTrendScenario() {
		List<String> labels = new ArrayList<>();
		LocalDate today = LocalDate.now();
		for (int i = DAILY_TREND_DAYS - 1; i >= 0; i--) {
			labels.add(today.minusDays(i).format(DAILY_TREND_LABEL_FORMAT));
		}
		double average = DAILY_TREND_VALUES.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double roundedAverage = Math.round(average * 10) / 10.0;
		TrendChartComponent trendChart = new TrendChartComponent(
				"日別問い合わせ件数（直近" + DAILY_TREND_DAYS + "日間）",
				labels,
				DAILY_TREND_VALUES,
				roundedAverage);
		return new ChatResponse(
				"直近" + DAILY_TREND_DAYS + "日間の日別問い合わせ件数をまとめました。平均は1日あたり約" + roundedAverage + "件です。",
				List.of(trendChart));
	}

	private ChatResponse fallbackScenario() {
		return new ChatResponse(
				"『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。",
				List.of());
	}

	// モニタリング画面での異常検知アラート（フロントエンド生成）に対する選択肢クリックへの応答。
	// アラート文・選択肢自体はフロントエンドがローカルで生成するため、ここではキーワードのみで
	// 判定する（このサービスは監視データを一切知らない）
	private ChatResponse alertFlowResponse(String message) {
		if (message.contains(CAUSE_ANALYSIS_KEYWORD)) {
			if (message.contains(CPU_METRIC_KEYWORD)) {
				return cpuCauseAnalysis();
			}
			if (message.contains(MEMORY_METRIC_KEYWORD)) {
				return memoryCauseAnalysis();
			}
			if (message.contains(TRAFFIC_METRIC_KEYWORD)) {
				return trafficCauseAnalysis();
			}
		}
		if (message.equals(CLOSE_ALERT_LABEL)) {
			return alertClosed();
		}
		return null;
	}

	private ChatResponse cpuCauseAnalysis() {
		TableComponent table = new TableComponent(
				List.of("プロセス", "CPU使用率"),
				List.of(
						List.of("batch-worker", "42%"),
						List.of("api-server", "28%"),
						List.of("log-agent", "15%"),
						List.of("other", "7%")));
		BarChartComponent barChart = new BarChartComponent(
				"プロセス別 CPU使用率",
				List.of("batch-worker", "api-server", "log-agent", "other"),
				List.of(42.0, 28.0, 15.0, 7.0));
		return new ChatResponse(
				"直近のCPU使用率上昇の要因を分析しました。バッチ処理プロセスの負荷が主な要因です。",
				List.of(table, barChart));
	}

	private ChatResponse memoryCauseAnalysis() {
		TableComponent table = new TableComponent(
				List.of("プロセス", "メモリ使用率"),
				List.of(
						List.of("cache-layer", "38%"),
						List.of("api-server", "25%"),
						List.of("session-store", "19%"),
						List.of("other", "10%")));
		BarChartComponent barChart = new BarChartComponent(
				"プロセス別 メモリ使用率",
				List.of("cache-layer", "api-server", "session-store", "other"),
				List.of(38.0, 25.0, 19.0, 10.0));
		return new ChatResponse(
				"直近のメモリ使用率上昇の要因を分析しました。キャッシュ層のメモリ消費増加が主な要因です。",
				List.of(table, barChart));
	}

	private ChatResponse trafficCauseAnalysis() {
		TableComponent table = new TableComponent(
				List.of("通信種別", "帯域占有率"),
				List.of(
						List.of("file-transfer", "45%"),
						List.of("api-calls", "27%"),
						List.of("db-replication", "18%"),
						List.of("other", "10%")));
		BarChartComponent barChart = new BarChartComponent(
				"通信種別別 帯域占有率",
				List.of("file-transfer", "api-calls", "db-replication", "other"),
				List.of(45.0, 27.0, 18.0, 10.0));
		return new ChatResponse(
				"直近のトラフィック増加の要因を分析しました。大容量ファイル転送による帯域占有が主な要因です。",
				List.of(table, barChart));
	}

	private ChatResponse alertClosed() {
		return new ChatResponse("承知しました。", List.of());
	}

}
