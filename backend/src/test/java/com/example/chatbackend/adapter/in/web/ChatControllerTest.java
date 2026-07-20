package com.example.chatbackend.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void chatReturnsAssigneeScenarioForAssigneeKeyword() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"今月の問い合わせ件数を担当者別にまとめて\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("今月の担当者別の問い合わせ件数をまとめました。")))
				.andExpect(jsonPath("$.components[0].type", is("table")))
				.andExpect(jsonPath("$.components[0].columns", is(List.of("担当者", "件数"))))
				.andExpect(jsonPath("$.components[0].rows[0]", is(List.of("佐藤", "12"))))
				.andExpect(jsonPath("$.components[1].type", is("bar_chart")))
				.andExpect(jsonPath("$.components[1].title", is("担当者別 問い合わせ件数")))
				.andExpect(jsonPath("$.components[1].labels", is(List.of("佐藤", "鈴木", "田中", "高橋"))))
				.andExpect(jsonPath("$.components[1].values[0]", is(12.0)));
	}

	@Test
	void chatReturnsCategoryScenarioForCategoryKeyword() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"カテゴリ別の問い合わせ件数を見せて\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("カテゴリ別の問い合わせ件数をまとめました。")))
				.andExpect(jsonPath("$.components[0].type", is("table")))
				.andExpect(jsonPath("$.components[0].columns", is(List.of("カテゴリ", "件数"))))
				.andExpect(jsonPath("$.components[0].rows[0]", is(List.of("請求", "15"))))
				.andExpect(jsonPath("$.components[1].type", is("bar_chart")))
				.andExpect(jsonPath("$.components[1].title", is("カテゴリ別 問い合わせ件数")))
				.andExpect(jsonPath("$.components[1].values[0]", is(15.0)));
	}

	@Test
	void chatReturnsDailyTrendScenarioForDailyKeyword() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"直近14日間の日別問い合わせ\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", containsString("直近14日間の日別問い合わせ件数をまとめました。")))
				.andExpect(jsonPath("$.components[0].type", is("trend_chart")))
				.andExpect(jsonPath("$.components[0].title", is("日別問い合わせ件数（直近14日間）")))
				.andExpect(jsonPath("$.components[0].labels", hasSize(14)))
				.andExpect(jsonPath("$.components[0].values", hasSize(14)))
				.andExpect(jsonPath("$.components[0].average", is(6.4)));
	}

	@Test
	void chatReturnsDashboardScenarioForSummaryKeyword() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"今月のサマリーを見せて\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components[0].type", is("stat_cards")))
				.andExpect(jsonPath("$.components[0].cards[0].delta", is("+12%")))
				.andExpect(jsonPath("$.components[0].cards[3].delta").doesNotExist())
				.andExpect(jsonPath("$.components[1].type", is("donut_chart")))
				.andExpect(jsonPath("$.components[1].labels", hasSize(4)))
				.andExpect(jsonPath("$.components[1].values", hasSize(4)))
				.andExpect(jsonPath("$.components[2].type", is("trend_chart")));
	}

	@Test
	void chatReturnsDashboardScenarioForDashboardKeyword() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"ダッシュボードを表示して\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components[0].type", is("stat_cards")))
				.andExpect(jsonPath("$.components[0].cards[0].delta", is("+12%")))
				.andExpect(jsonPath("$.components[0].cards[3].delta").doesNotExist())
				.andExpect(jsonPath("$.components[1].type", is("donut_chart")))
				.andExpect(jsonPath("$.components[1].labels", hasSize(4)))
				.andExpect(jsonPath("$.components[1].values", hasSize(4)))
				.andExpect(jsonPath("$.components[2].type", is("trend_chart")));
	}

	@Test
	void chatReturnsGuidanceTextWithoutComponentsForNonMatchingMessage() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"こんにちは\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", not(containsString("エコー"))))
				.andExpect(jsonPath("$.components", empty()));
	}

	@Test
	void chatReturnsChoicesForInquiryTrigger() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"新規問い合わせ\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("問い合わせのカテゴリを選んでください。")))
				.andExpect(jsonPath("$.components[0].type", is("choices")))
				.andExpect(jsonPath("$.components[0].options",
						is(List.of("カテゴリ: 請求", "カテゴリ: 技術", "カテゴリ: アカウント", "カテゴリ: その他", "キャンセル"))));
	}

	@Test
	void chatReturnsUrgencyChoicesNotCategoryReportForCategoryAnswer() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"カテゴリ: 請求\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("緊急度を選んでください。")))
				.andExpect(jsonPath("$.components[0].type", is("choices")))
				.andExpect(jsonPath("$.components[0].options",
						is(List.of("緊急度: 高", "緊急度: 中", "緊急度: 低", "キャンセル"))));
	}

	@Test
	void chatReturnsFaqPresentationForInquirySummary() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("ご登録の前に、関連するFAQをご確認ください。")))
				.andExpect(jsonPath("$.components[0].type", is("faq_list")))
				.andExpect(jsonPath("$.components[0].titles",
						is(List.of("請求額が二重に表示される場合", "請求書の再発行方法", "支払い方法の変更手順"))))
				.andExpect(jsonPath("$.components[1].type", is("choices")))
				.andExpect(jsonPath("$.components[1].options", is(List.of("解決した", "解決しないので問い合わせる"))));
	}

	@Test
	void chatReturnsFaqDetailWithFaqListAndChoicesForFaqAnswer() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"FAQ: 請求額が二重に表示される場合\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", containsString("決済処理の反映タイミングのずれ")))
				.andExpect(jsonPath("$.components[0].type", is("faq_list")))
				.andExpect(jsonPath("$.components[0].titles",
						is(List.of("請求額が二重に表示される場合", "請求書の再発行方法", "支払い方法の変更手順"))))
				.andExpect(jsonPath("$.components[1].type", is("choices")))
				.andExpect(jsonPath("$.components[1].options", is(List.of("解決した", "解決しないので問い合わせる"))));
	}

	@Test
	void chatReturnsCompletionMessageForFaqResolved() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"解決した\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("解決してよかったです。またご不明な点があればお気軽にお尋ねください。")))
				.andExpect(jsonPath("$.components", empty()));
	}

	@Test
	void chatReturnsConfirmationForFaqUnresolvedWithSummary() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"解決しないので問い合わせる / カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("以下の内容で登録してよろしいですか？")))
				.andExpect(jsonPath("$.components[0].type", is("table")))
				.andExpect(jsonPath("$.components[0].columns", is(List.of("項目", "内容"))))
				.andExpect(jsonPath("$.components[0].rows[0]", is(List.of("カテゴリ", "請求"))))
				.andExpect(jsonPath("$.components[0].rows[2]", is(List.of("内容", "請求額が二重になっている"))))
				.andExpect(jsonPath("$.components[1].type", is("choices")))
				.andExpect(jsonPath("$.components[1].options", is(List.of("登録する", "やり直す", "キャンセル"))));
	}

	@Test
	void chatReturnsCompletionForSubmit() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"登録する\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply", is("問い合わせを受け付けました。受付番号: INQ-0001")))
				.andExpect(jsonPath("$.components", empty()));
	}

	@Test
	void chatReturnsBadRequestForBlankMessage() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"   \"}"))
				.andExpect(status().isBadRequest());
	}

}
