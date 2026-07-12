# テストレポート: 動的UI生成チャット応答（モック版）

> 本書は今回の機能（動的UI生成チャット応答・モック版、`docs/requirements.md` AC-1〜AC-9）に対する検証結果である。前回パイプライン実行分（別機能: ChatGPT風UI試作アプリ）の `docs/test-report.md` の内容を置き換える。
>
> 検証実施日: 2026-07-11

## 検証方法の分類

- **自動テスト**: 実際にコマンドを実行し、出力を確認した（バックエンドJUnit、フロントエンド `vue-tsc -b` / `vite build`、grep）
- **実機統合確認**: バックエンドを実際に起動し、`curl` で3シナリオ＋空白メッセージを送信して生JSONを確認した
- **コードレビューによる机上確認＋要手動確認**: ブラウザ操作が必要なACについて、実装コードを読み実装の妥当性を確認したが、ブラウザでの実描画確認はテスト担当（本エージェント）では実施不可能なため未実施。手動確認チェックリストを本書末尾に記載

## 1. コマンド実行ログ

### 1-1. バックエンド自動テスト

```
cd backend
./mvnw.cmd test
```

結果:
```
[INFO] Running com.example.chatbackend.ChatBackendApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.chatbackend.ChatBackendApplicationTests
[INFO] Running com.example.chatbackend.ChatControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.chatbackend.ChatControllerTest
[INFO] Running com.example.chatbackend.MockChatServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.chatbackend.MockChatServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

既存テスト内容のレビュー（コードを実読して確認）:
- `ChatControllerTest.chatReturnsAssigneeScenarioForAssigneeKeyword` — 「今月の問い合わせ件数を担当者別にまとめて」で200、`components[0].type=="table"`・`columns`・`rows[0]`、`components[1].type=="bar_chart"`・`title`・`labels`・`values[0]` まで `jsonPath` で値検証している。**AC-4・AC-1関連データの実在確認として十分**
- `ChatControllerTest.chatReturnsCategoryScenarioForCategoryKeyword` — カテゴリキーワードで担当者シナリオとは異なる `reply`／`columns`／`rows`／`title`／`values` を検証。**AC-2のバックエンド側データ差異を担保**
- `ChatControllerTest.chatReturnsGuidanceTextWithoutComponentsForNonMatchingMessage` — 「こんにちは」で `components` が空配列（`empty()`）、`reply` に「エコー」を含まない（`not(containsString("エコー"))`）ことを検証。**AC-3の自動テスト部分を過不足なく満たす**
- `ChatControllerTest.chatReturnsBadRequestForBlankMessage` — 空白のみメッセージで400。**AC-9のバックエンド側を担保**
- `MockChatServiceTest` — サービス単体でも同様の3+1パターン（担当者／カテゴリ／種別キーワード／非マッチ）を検証しており、コントローラー層とサービス層の二重の担保がある

上記は要件が求める「担当者→table+bar_chart」「カテゴリ→別データ」「非マッチ→components空・エコーなし」「空白→400」を全て実データ値まで検証しており、**追加・強化の必要はないと判断した**（テストコードの変更・追加は行っていない）。

### 1-2. フロントエンド静的検証

```
cd frontend
npm run build
```

結果:
```
> vue-tsc -b && vite build
✓ 40 modules transformed.
dist/index.html                 0.46 kB
dist/assets/index-BgbAkIDF.css   7.69 kB
dist/assets/index-CMdyGd-r.js   74.07 kB
✓ built in 625ms
```
Exit code 0（グリーン）。`docs/lessons-learned.md` の指示通り、bare `vue-tsc --noEmit` ではなく `npm run build`（内部で `vue-tsc -b`）を使用した。

```
grep -r "v-html" frontend/src
```
結果: 0件（マッチなし）。XSS防止の非機能要件（応答文字列をHTMLとして解釈しない）をコード上で確認した。

フロントエンドには自動テストフレームワーク（vitest等）が導入されておらず、`package.json` にもテストスクリプトは存在しない。既存方針（「動くプロトタイプ優先」）と整合するため、本フェーズで新規にテストフレームワークを導入することはせず、既存の検証手段（型チェック・ビルド・grep）の範囲で実施した。

### 1-3. 実機統合確認（バックエンド起動＋curl）

```
cd backend
./mvnw.cmd spring-boot:run   # バックグラウンド起動、ポート8080でLISTENを確認後に実施
```

3シナリオ＋空白メッセージの実レスポンス（UTF-8のファイル経由でPOST。日本語を直接シェル引数に渡すと文字化けし400になる問題があったため、JSONファイルを作成し `curl --data-binary @file` で送信）:

**シナリオA（担当者系キーワード「今月の問い合わせ件数を担当者別にまとめて」）**
```json
{"reply":"今月の担当者別の問い合わせ件数をまとめました。","components":[{"type":"table","columns":["担当者","件数"],"rows":[["佐藤","12"],["鈴木","9"],["田中","7"],["高橋","5"]]},{"type":"bar_chart","title":"担当者別 問い合わせ件数","labels":["佐藤","鈴木","田中","高橋"],"values":[12.0,9.0,7.0,5.0]}]}
```
`docs/chat-response-schema.md` のシナリオA サンプルと**フィールド・値ともに完全一致**。

**シナリオB（カテゴリ系キーワード「カテゴリ別の問い合わせ件数を見せて」）**
```json
{"reply":"カテゴリ別の問い合わせ件数をまとめました。","components":[{"type":"table","columns":["カテゴリ","件数"],"rows":[["請求","15"],["技術","11"],["アカウント","6"],["その他","4"]]},{"type":"bar_chart","title":"カテゴリ別 問い合わせ件数","labels":["請求","技術","アカウント","その他"],"values":[15.0,11.0,6.0,4.0]}]}
```
`docs/chat-response-schema.md` のシナリオB サンプルと**完全一致**。

**シナリオC（非マッチ「こんにちは」）**
```json
{"reply":"『今月の問い合わせ件数を担当者別にまとめて』のように聞いてください。表やグラフでお答えします。","components":[]}
```
`docs/chat-response-schema.md` のシナリオC サンプルと**完全一致**。「エコー」を含まない。

**空白メッセージ（`{"message":"   "}`）**
```
HTTP_STATUS:400
```

確認後、バックエンドプロセス（`spring-boot:run` およびその子javaプロセス）を明示的に停止し（`Stop-Process -Force`）、ポート8080への接続不可（`HTTP_STATUS:000`）を確認した。停止漏れなし。

## 2. 受け入れ基準（AC）別結果

| AC | 内容(要約) | 検証方法(要件書指定) | 実施した検証 | 結果 |
|---|---|---|---|---|
| AC-1 | 担当者別キーワードで要約テキスト＋表＋棒グラフが1吹き出しに表示 | 手動確認 | バックエンド応答データの実在は実機curl・自動テストで確認済み。フロント描画側は `MessageBubble.vue`／`TableView.vue`／`BarChartView.vue` のコードレビューで、`message.components` を順にv-forし `table`→TableView、`bar_chart`→BarChartViewを同一吹き出し内に描画するロジックを確認。**ブラウザでの実描画は未実施** | コードレビューによる机上確認＋要手動確認（pending） |
| AC-2 | カテゴリ別キーワードでAC-1と異なるデータの表＋棒グラフ | 手動確認 | バックエンド側データ差異（列名・値とも別データ）は自動テスト・curlで確認済み（シナリオA・Bで `reply`/`columns`/`values` が明確に異なる）。フロント描画は同上のコードレビューのみ | コードレビューによる机上確認＋要手動確認（pending） |
| AC-3 | 非マッチ入力（「こんにちは」）で表・グラフなし、案内テキストのみ、「エコー」応答なし | 自動テスト＋手動確認 | 自動テスト: `ChatControllerTest.chatReturnsGuidanceTextWithoutComponentsForNonMatchingMessage` が green（`components`空・`reply`に「エコー」含まず）。実機curlでも同一結果を確認。フロント側「表・グラフなし」の描画（`components`が`undefined`/空配列ならv-forが0回で何も描画されない）は`MessageBubble.vue`のロジック上妥当と確認 | 自動テスト: Pass／フロント目視は要手動確認 |
| AC-4 | 統合テスト(MockMvc)で担当者系→table+bar_chart、非マッチ→components0件を確認 | 自動テスト(MockMvc) | `ChatControllerTest`の該当3テストが green。`jsonPath`で`type`・データ値まで検証済み | **Pass** |
| AC-5 | 未知typeの応答でエラー吹き出し表示、画面操作可能維持 | 手動確認(モック差し替え等) | `chatApi.ts`の`isValidUiComponentSpec`が`type`が`table`/`bar_chart`以外なら不正と判定し`ChatResponseFormatError`をthrow、`conversationStore.ts`の`sendMessage`が`catch`で`ChatResponseFormatError`を判定し「応答を表示できませんでした」という新規アシスタント吹き出しを追加する実装をコードレビューで確認。例外は`try/catch`内で完結し外部に漏れないため、画面がクラッシュしない設計になっていることも確認。**実際にブラウザで未知typeデータを注入して確認する手順は未実施** | コードレビューによる机上確認＋要手動確認（pending） |
| AC-6 | バックエンド停止状態で送信するとエラー吹き出し表示 | 手動確認 | `conversationStore.ts`の`sendMessage`は`ChatResponseFormatError`以外の例外（`fetch`失敗・non-2xxによる`Error`）を`catch`し「エラー: 応答を取得できませんでした」という既存文言の吹き出しを追加するパスが実装されていることをコードレビューで確認（`chatApi.ts`の`!response.ok`時の`throw new Error(...)`、および`fetch`自体が失敗した場合の素通しの例外の両方がこのフォールバックに合流する） | コードレビューによる机上確認＋要手動確認（pending） |
| AC-7 | 表・グラフを含む会話から別会話へ切替→戻ると再描画 | 手動確認 | `conversationStore.ts`は`conversations`配列を状態として保持し、`selectConversation`は`activeConversationId`を変更するのみで`messages`（`components`を含む`Message`オブジェクト）を削除・変更しない。`ChatWindow.vue`の`messages`は`store.activeConversation?.messages`から算出されるcomputedであり、会話切替のたびに再評価されて同一の`components`データを持つ`Message`が再度`MessageBubble`にpropsとして渡る構造になっていることをコードレビューで確認。Vueの通常のリアクティブ再レンダリングの範囲内であり、追加のキャッシュ破棄・データ欠落のロジックは存在しない | コードレビューによる机上確認＋要手動確認（pending） |
| AC-8 | 応答JSONスキーマ契約文書が実際のレスポンスと一致 | 手動確認 | 実機curlで得た3シナリオの生JSONを`docs/chat-response-schema.md`記載のサンプルJSONとフィールド単位で突き合わせ、`reply`／`components[].type`／`columns`／`rows`／`title`／`labels`／`values`（`12.0`のような小数点表記を含む）まで**完全一致**を確認した | **Pass** |
| AC-9 | 空文字・空白のみ送信は拒否（バックエンド400／フロント送信抑止） | 自動テスト | バックエンド: `ChatControllerTest.chatReturnsBadRequestForBlankMessage`が green、実機curlでも400を確認。フロント: `MessageInput.vue`の`canSend`（`text.value.trim().length > 0`）により空白のみでは送信ボタンが`disabled`になり、`submit()`内でも`!canSend.value`なら`return`する二重の抑止をコードレビューで確認（既存実装の退行なし） | **Pass**（バックエンドは自動テストで確認、フロント送信抑止はコードレビューで退行なしを確認） |

## 3. 見つかった問題

自動テスト・実機curl・コードレビューの範囲で、プロダクトコードの不具合は見つからなかった。

参考情報（不具合ではなく申し送り事項）: `implementation-notes.md`に記載の通り、ステップ8実装時に`chatApi.ts`の型ガード関数シグネチャ（`Record<string, unknown>` → `unknown`）を実装者自身が修正済みであることを確認した。現在の`npm run build`はこの修正を含めてgreenであり、本テストフェーズで新たな型エラーは検出されなかった。

## 4. 手動確認チェックリスト（ブラウザでの実施が必要。本エージェントでは実施不可）

以下は`docs/design.md`ステップ9に準拠した手順。フロント（`npm run dev`）・バックエンド（`./mvnw.cmd spring-boot:run`）を両方起動した状態で実施すること。

1. **AC-1**: 「今月の問い合わせ件数を担当者別にまとめて」と送信し、アシスタント吹き出し内に要約テキスト・担当者別の表・棒グラフが縦に並んで表示されることを目視確認する
2. **AC-2**: 続けて「カテゴリ別の問い合わせ件数を見せて」と送信し、AC-1とは異なる列名・値（カテゴリ別）の表・棒グラフが表示されることを確認する
3. **AC-3**: 「こんにちは」と送信し、表・グラフが表示されず案内テキストのみが表示されること、「エコー: 」という文言が出ないことを確認する
4. **AC-5**: 一時的に`MockChatService`の1シナリオの`type`相当（例: バックエンドの`@JsonSubTypes`に含まれない値をテスト用に差し込む、または`chatApi.ts`の検証関数呼び出し箇所に一時的な不正データを注入する）を未知の値に書き換えて再起動し、「応答を表示できませんでした」の吹き出しが表示されること、ブラウザのDevToolsコンソールに未処理例外が出ていないこと、他のUI操作（入力・送信・会話切替）が引き続き可能であることを確認したのち、変更を元に戻す
5. **AC-6**: バックエンドプロセスを停止した状態でメッセージを送信し、「エラー: 応答を取得できませんでした」の吹き出しが表示されることを確認する
6. **AC-7**: 表・グラフを含む会話（AC-1やAC-2で送信した会話）から、Sidebarで別の新規会話へ切り替え、その後元の会話に戻り、表・グラフが再描画されていることを確認する
7. （上記全体を通して）ブラウザDevToolsのConsoleにVueの警告・エラーが出ていないことを確認する

## 5. 最終判定

- **自動テスト対象（AC-3, AC-4, AC-9）**: 全てPass（`./mvnw.cmd test` 9/9 green、フロント送信抑止もコードレビューで確認）
- **実機統合確認（AC-8）**: Pass（3シナリオ+空白メッセージの実レスポンスが契約文書・自動テストの期待値と完全一致）
- **フロントエンド静的検証**: Pass（`npm run build` green、`v-html` 不使用でXSS要件を満たす）
- **ブラウザ操作が必要なAC（AC-1, AC-2, AC-5, AC-6, AC-7）**: いずれもコードレビュー上は実装が要件を満たす構造になっていることを確認したが、**実際のブラウザ描画確認は未実施（pending manual）**。本エージェントはブラウザを操作できないため、上記チェックリストに沿った手動確認をユーザー側で実施する必要がある
- **プロダクトコードの不具合**: 発見なし

**総合結論**: 自動化可能な範囲（バックエンドテスト・フロントビルド・API契約整合性）は全てPassであり、ブロッカーとなる不具合は見つからなかった。パイプラインを進める上での技術的な阻害要因はない。ただし、AC-1・AC-2・AC-5・AC-6・AC-7の最終確認（ブラウザでの実描画）は要件書が指定する「手動確認」であり自動化対象外のため、上記チェックリストに従った人手でのブラウザ確認が完了するまでは、これら5件のACは正式には「未検証（pending manual）」の状態である。
