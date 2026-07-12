package com.example.chatbackend;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockChatService {

	private static final String ASSIGNEE_KEYWORD = "担当";
	private static final String CATEGORY_KEYWORD = "カテゴリ";
	private static final String TYPE_KEYWORD = "種別";

	public ChatResponse generateResponse(String message) {
		if (message.contains(ASSIGNEE_KEYWORD)) {
			return assigneeScenario();
		}
		if (message.contains(CATEGORY_KEYWORD) || message.contains(TYPE_KEYWORD)) {
			return categoryScenario();
		}
		return fallbackScenario();
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

	private ChatResponse fallbackScenario() {
		return new ChatResponse(
				"『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。",
				List.of());
	}

}
