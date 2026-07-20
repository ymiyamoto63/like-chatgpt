# テストレポート: バックエンドへの port & adapter リファクタリング（Issue #4）

対象要件: `docs/pipeline/4/requirements.md`（AC-1〜AC-5）
対象設計: `docs/pipeline/4/design.md`
実装記録: `docs/pipeline/4/implementation-notes.md`
検証日: 2026-07-19
検証範囲: **バックエンドのみ**（フロントエンド無変更のためフロントのテストは実行していない）

## 総合判定: PASS（AC-4 は手動確認未実施、実施手順を下記に記載）

自動テストで検証可能な AC-1・AC-2・AC-3・AC-5、および FR-7 の API 互換性はすべて実際にコマンドを実行して確認し、問題は検出されなかった。AC-4（フロントエンドからの疎通確認）はユーザー側の手動実施事項として明記する。

---

## Commands run

```bash
# AC-1: 全件テスト
cd /home/miyam/like-chatgpt/backend && ./mvnw test

# AC-2: ArchitectureTest の RED 再確認（一時的違反混入→復元）
#   ChatService.java に adapter.out.reply.KeywordMatchReplyGenerationAdapter 型の
#   未使用フィールドを一時追加し、以下を実行後、Edit で元に戻し git diff で差分ゼロを確認
cd /home/miyam/like-chatgpt/backend && ./mvnw test -Dtest=ArchitectureTest
git diff --stat   # 復元後、差分ゼロを確認
cd /home/miyam/like-chatgpt/backend && ./mvnw test   # 復元後の再全件確認

# AC-3: コントローラテスト3件の内容不変性（git 履歴・diff）
git log --follow --oneline -- backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java
git log --follow --oneline -- backend/src/test/java/com/example/chatbackend/adapter/in/web/SuggestControllerTest.java
git log --follow --oneline -- backend/src/test/java/com/example/chatbackend/adapter/in/web/MonitoringControllerTest.java
diff <(git show 64459a8:backend/src/test/java/com/example/chatbackend/controller/ChatControllerTest.java | sed '1s/^package com.example.chatbackend.controller;/PKG/') \
     <(git show HEAD:backend/src/test/java/com/example/chatbackend/adapter/in/web/ChatControllerTest.java | sed '1s/^package com.example.chatbackend.adapter.in.web;/PKG/')
# 同様に SuggestControllerTest / MonitoringControllerTest でも実施

# AC-5: application/service の adapter import 有無（grep 二重確認、ArchUnit は AC-1 に含む）
grep -rn "import com.example.chatbackend.adapter" backend/src/main/java/com/example/chatbackend/application/service/
grep -rn "import com.example.chatbackend.adapter" backend/src/main/java/com/example/chatbackend/application/

# FR-7: 既存プロセス（ユーザーIDEデバッガ起動、PID 275581, port 8080）に対して curl 比較
curl -s -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"message":"今月の問い合わせ件数を担当者別にまとめて"}'
curl -s -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"message":"直近14日間の日別問い合わせ"}'
curl -s -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"message":"新規問い合わせ"}'
curl -s -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"message":"カテゴリ: 請求 / 緊急度: 高 / 内容: 請求額が二重になっている"}'
curl -s -X POST http://localhost:8080/api/suggest -H 'Content-Type: application/json' -d '{"text":"当月担"}'
curl -s -X POST http://localhost:8080/api/suggest -H 'Content-Type: application/json' -d '{"text":""}'
curl -s http://localhost:8080/api/monitoring/snapshot
```

## Result

| 項目 | 結果 |
|---|---|
| `./mvnw test`（全件） | **PASS**: `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS |
| `ArchitectureTest`（4ルール） | **PASS**（GREEN）。一時的違反混入による RED 再現も成功（後述） |
| コントローラテスト3件の内容不変性 | **PASS**: パッケージ宣言行を除き旧ファイルと byte-for-byte 完全一致 |
| `application/service` の adapter import 有無（grep） | **PASS**: マッチなし（ArchUnit ルールと結果一致） |
| FR-7 API 互換性（curl 実測 vs `docs/api/*.md`） | **PASS**: chat 4シナリオ・suggest 2ケース・monitoring 構造すべて仕様と一致 |
| AC-4（フロントエンドからの手動疎通確認） | **未実施**（手動確認・ユーザー実施事項。手順は後述） |

失敗はゼロ件。

---

## 詳細

### AC-1: `./mvnw test` が全件成功する

```
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

内訳（実行ログより）:
- `ChatBackendApplicationTests`: 1件（Spring コンテキストロード、`ApplicationConfiguration` の3 Bean 配線を含め正常起動を確認）
- `KeywordMatchReplyGenerationAdapterTest`: 22件
- `RandomWalkMetricsGenerationAdapterTest`: 3件
- `CannedPhraseSuggestionAdapterTest`: 7件
- `SuggestControllerTest`: 3件
- `ChatControllerTest`: 12件
- `MonitoringControllerTest`: 1件
- `ArchitectureTest`: 4件

実装記録（implementation-notes.md）記載の最終値（`Tests run: 53`）と一致。

### AC-2: ArchUnit テストが存在し、FR-6 の依存方向ルール違反を検出できる状態でパスする

`backend/src/test/java/com/example/chatbackend/architecture/ArchitectureTest.java` に design.md 記載の4ルールが実装されていることをソース確認済み:
1. `domainMustNotDependOnApplicationOrAdapter`
2. `applicationMustNotDependOnAdapter`
3. `applicationServiceMustNotDependOnAdapter`（AC-5 対応）
4. `onlyAdapterAndConfigMayDependOnSpring`

全ルールが `./mvnw test` で GREEN であることを確認済み（上記 AC-1）。

**違反検出能力の再確認（実施）**: 実装記録に記載の RED 確認手順を自分でも再現した。

1. `ChatService.java` に `adapter.out.reply.KeywordMatchReplyGenerationAdapter` 型の未使用フィールドを一時追加。
2. `./mvnw test -Dtest=ArchitectureTest` を実行した結果、期待どおり **RED**（`Tests run: 4, Failures: 2, Errors: 0`, BUILD FAILURE）:
   - `applicationMustNotDependOnAdapter` → FAILURE（`ChatService.testEngineerTempViolation` フィールドが `adapter.out.reply.KeywordMatchReplyGenerationAdapter` 型を持つことを検出）
   - `applicationServiceMustNotDependOnAdapter` → FAILURE（同一違反）
   - `domainMustNotDependOnApplicationOrAdapter` / `onlyAdapterAndConfigMayDependOnSpring` は影響なし（PASS のまま）
   - この結果は implementation-notes.md の step 7-8 に記載された RED 確認結果と完全に一致する。
3. Edit で `ChatService.java` を元の内容に復元し、`git diff --stat` / `git diff <ファイル>` で差分ゼロ（コミット済み状態と完全一致）を確認。
4. 復元後 `./mvnw test`（全件）を再実行し、`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS を再確認。違反コードは最終状態に一切残っていない。

### AC-3: 既存コントローラテスト3件が同じリクエスト/レスポンスの組で成功する

`git log --follow` で3ファイルとも移動前（`controller/` パッケージ）からのファイル履歴が連続して追跡できることを確認。さらに、パッケージ変更直前のコミット（`64459a8`、設計フェーズ完了時点＝移動前の最終状態）の内容と、現在の `adapter/in/web/` 配下の内容を、パッケージ宣言行のみを除いて `diff` した結果、**3ファイルとも差分ゼロ（完全一致）**であることを確認した。

- `ChatControllerTest.java`: `diff` exit code 0（差分なし）
- `SuggestControllerTest.java`: `diff` exit code 0（差分なし）
- `MonitoringControllerTest.java`: `diff` exit code 0（差分なし）

これにより「同じリクエスト/レスポンスの組で成功する」ことが、アサーション文字列レベルで裏付けられた。加えて、AC-1 の全件テスト結果でこれら3クラス（計16件: Chat 12 + Suggest 3 + Monitoring 1）が GREEN であることを確認済み。`@SpringBootTest` + `@AutoConfigureMockMvc` によるフルコンテキスト起動テストのため、`ApplicationConfiguration` の Bean 配線を含めたエンドツーエンド検証になっている。

### AC-5: `application/service` のクラスが `adapter` パッケージのクラスを import していない

二重確認を実施:
1. **ArchUnit（機械検証）**: `applicationServiceMustNotDependOnAdapter` ルールが GREEN（AC-1/AC-2 で確認済み）。RED 再現テストでもこのルールが正しく違反を検出することを確認済み（上記 AC-2）。
2. **grep（独立確認）**:
   ```
   $ grep -rn "import com.example.chatbackend.adapter" backend/src/main/java/com/example/chatbackend/application/service/
   NO MATCHES (good)
   $ grep -rn "import com.example.chatbackend.adapter" backend/src/main/java/com/example/chatbackend/application/
   NO MATCHES (good)
   ```
   `application/service/{ChatService,SuggestService,MonitoringService}.java` のいずれにも `adapter` パッケージへの import がないことを確認。`application` 配下全体（`port/in`, `port/out` 含む）でも同様にゼロ件。

### FR-7 API 互換性（AC-3 の裏付けおよび design.md Test strategy 記載の curl 比較）

バックエンドは既にユーザーの IDE（VSCode Java デバッガ、PID 275581、`suspend=n` でリッスン中）によってポート8080で起動済みだった（自分で `./mvnw spring-boot:run` を起動しようとしたところポート競合で失敗したため、このプロセスに対して読み取り専用の curl リクエストのみ実施。プロセスは自分が起動したものではないため停止せず、検証終了時もそのまま残した）。

`docs/api/chat-response-schema.md` / `docs/api/suggest-api.md` / `docs/api/monitoring-response-schema.md` に記載のサンプルJSONと実際のレスポンスを比較した結果、すべて一致した:

- `POST /api/chat`「担当」キーワード → シナリオA（表＋棒グラフ）: ドキュメントの reply 文言・columns・rows・labels・values と完全一致
- `POST /api/chat`「日別」キーワード → シナリオC（trend_chart）: reply・title・average(6.4)・values 完全一致（labels の日付は実行日 2026-07-19 基準の直近14日分であり、ドキュメントのサンプル日付とは異なるが、これは仕様どおりの実行時可変値）
- `POST /api/chat`「新規問い合わせ」トリガー → choices の5選択肢が完全一致
- `POST /api/chat` FAQ提示フロー（`カテゴリ: 請求 / 緊急度: 高 / 内容: ...`）→ `faq_list`(3件のタイトル) + `choices`(2件) が `docs/api/chat-response-schema.md` のサンプルと完全一致
- `POST /api/suggest`（`"当月担"` → `"当者別問い合わせ"`、`""` → `null`）: ドキュメント記載どおり
- `GET /api/monitoring/snapshot`: `nodes` 9件・`edges` 12件、各要素のキー（`id`/`label`/`cpuPercent`/`memoryPercent`、`id`/`sourceId`/`targetId`/`bandwidthPercent`）がスキーマと完全一致

以上により、リファクタリング前後でAPIのレスポンスJSON形式・内容が変わっていないことを実測で確認した。

---

## Failures

なし。

---

## 受け入れ基準カバレッジ

| AC | 内容 | 検証方法（要件） | 結果 | 根拠 |
|---|---|---|---|---|
| AC-1 | `./mvnw test` が全件成功する | 自動テスト | **PASS** | `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS |
| AC-2 | ArchUnit テストが存在し、FR-6 の依存方向ルール違反を検出できる状態でパスする | 自動テスト | **PASS** | `ArchitectureTest` 4ルール GREEN（AC-1に含む）。一時的違反混入による RED（`Tests run: 4, Failures: 2`）を実際に再現し、復元後 `git diff` 差分ゼロ・全件再GREENを確認 |
| AC-3 | 既存コントローラテスト3件が同じリクエスト/レスポンスの組で成功する | 自動テスト | **PASS** | 3クラス計16件 GREEN。かつ移動前後のテストファイル内容がパッケージ宣言行を除き byte-for-byte 完全一致（`diff` exit 0）であることを確認 |
| AC-4 | フロントエンドから疎通確認（担当者別レポート／新規問い合わせフロー／モニタリングアラート応答が従来どおり動作） | 手動確認 | **未実施** | 下記「AC-4 手動確認手順」を参照。今回はバックエンドのみの検証スコープであり、フロントエンドとの実疎通はユーザー側での実施を推奨 |
| AC-5 | `application/service` のクラスが `adapter` パッケージのクラスを import していない | 自動テスト | **PASS** | ArchUnit `applicationServiceMustNotDependOnAdapter` ルール GREEN（RED再現でも正しく違反検出）＋ `grep` でも import ゼロ件を独立確認 |

### AC-4 手動確認手順（未実施・ユーザー実施事項）

README 記載の手順でバックエンド・フロントエンドを起動した上で、以下3シナリオを目視確認する:

1. **担当者別レポート**: チャット画面で「今月の問い合わせ件数を担当者別にまとめて」等を送信し、表＋棒グラフが従来どおり表示されることを確認する。
2. **新規問い合わせフロー**: 「新規問い合わせ」ボタン押下 → カテゴリ選択 → 緊急度選択 → 内容入力 → FAQ提示（`faq_list` 表示）→「解決しないので問い合わせる」選択 → 確認画面（表＋選択肢）→「登録する」で受付番号が表示されるまでの一連の流れが崩れていないことを確認する。
3. **モニタリングアラート応答**: システム構成図モニタリング画面でノード/エッジのメトリクスが閾値（80%/90%）を超えるまで待つ（または閾値超過を再現しやすい操作を行う）とアラートがチャットに生成されることを確認し、「原因を調べる」相当の選択肢クリックで原因分析（表＋棒グラフ）が表示され、「閉じる」で応答が閉じることを確認する。

いずれも今回のバックエンド curl 検証（FR-7 API互換性、上記参照）でレスポンス形式が変わっていないことは既に自動的に裏付けられているため、上記手動確認は主に「フロントエンド側の描画・遷移が壊れていないこと」の最終確認として位置づけられる。

---

## 補足: 検証時の環境メモ

- ポート8080は検証実施前から既存の Java プロセス（PID 275581、VSCode Java デバッガ経由で起動されたと見られる `com.example.chatbackend.ChatBackendApplication`）がリッスンしていた。自分で `./mvnw spring-boot:run` の起動を試みたが `Port 8080 was already in use` で失敗し、代わりにこの既存プロセスへ読み取り専用の curl リクエストを送信して FR-7 の確認を行った。このプロセスは検証終了後もそのまま稼働中であり、停止していない（自分が起動したプロセスではないため）。
