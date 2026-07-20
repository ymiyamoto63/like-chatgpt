package com.example.chatbackend.adapter.out.suggest;

import com.example.chatbackend.application.port.out.SuggestionGenerationPort;
import com.example.chatbackend.domain.suggest.SuggestResponse;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CannedPhraseSuggestionAdapter implements SuggestionGenerationPort {

	// 補完対象の定型文。値は frontend/src/constants/reportButtons.ts の
	// 同名定数と一致させること（言語をまたぐため自動同期はできない）
	private static final List<String> CANNED_PHRASES = List.of(
			"当月担当者別問い合わせ",
			"当月カテゴリ別問い合わせ",
			"直近14日間の日別問い合わせ");

	@Override
	public SuggestResponse generateSuggestion(String text) {
		if (text == null || text.isEmpty()) {
			return SuggestResponse.empty();
		}
		for (String phrase : CANNED_PHRASES) {
			// 完全一致は残りが空になるため候補なし扱い
			if (phrase.startsWith(text) && phrase.length() > text.length()) {
				return new SuggestResponse(phrase.substring(text.length()));
			}
		}
		return SuggestResponse.empty();
	}
}
