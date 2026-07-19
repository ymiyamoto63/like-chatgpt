package com.example.chatbackend.adapter.out.faq;

import com.example.chatbackend.application.port.out.FaqQueryPort;
import com.example.chatbackend.domain.faq.FaqEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * カテゴリ別FAQのインメモリリポジトリ。
 * 要件（docs/requirements/inquiry-faq-suggestion.md FR-5）どおりデータはハードコードで、
 * 将来DB・CMS等の外部管理に差し替える際はこのクラスの実装のみを置き換える。
 * タイトルは全カテゴリを通じて一意（ステートレスなMockがタイトルだけで解決できるための前提）。
 */
@Repository
public class InMemoryFaqQueryAdapter implements FaqQueryPort {

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

	public Optional<FaqEntry> findByTitle(String title) {
		return Optional.ofNullable(FAQ_BY_TITLE.get(title));
	}

	public List<String> findTitlesByCategory(String category) {
		return FAQ_TITLES_BY_CATEGORY.getOrDefault(category, List.of());
	}
}
