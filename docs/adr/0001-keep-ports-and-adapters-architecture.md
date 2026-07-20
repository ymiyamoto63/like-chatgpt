# ADR 0001: バックエンドは ports & adapters 構成を維持する

- ステータス: 承認済み
- 日付: 2026-07-20

## このADRを読むための予備知識

### ports & adapters(ヘキサゴナルアーキテクチャ)とは

アプリの中心となるロジック(「メッセージを受け取って返答を作る」など)を、外部とのやりとり(Web、DB、外部API)から切り離す設計スタイル。

- **port(ポート)** … 「こういう機能が欲しい」という*インターフェース(約束事)*。例: `ReplyGenerationPort` は「メッセージを渡すと返答を返してくれる何か」という約束だけを定義し、中身がキーワードマッチなのか LLM なのかは知らない。
- **adapter(アダプタ)** … その約束を実際に実現する*実装クラス*。例: `KeywordMatchReplyGenerationAdapter` はキーワードマッチで返答を作る実装。

コンセントの差し込み口(port)と、そこに挿すプラグ(adapter)の関係をイメージすると分かりやすい。差し込み口の形さえ合っていれば、プラグの先につながる機器は自由に交換できる。

### Spring Boot 標準の DI(`@Service` 方式)とは

クラスに `@Service` や `@Component` を付けておくと、Spring が起動時に自動で見つけて(コンポーネントスキャン)、必要な場所に注入してくれる仕組み。手軽で、Spring のチュートリアルや多くの現場で使われる標準的な書き方。

### このプロジェクトの現状

- `application/port/in` … ユースケースのインターフェース(「返答を生成する」など)
- `application/port/out` … 外部機能への port(「返答を作ってくれる何か」など)
- `application/service` … ユースケースの実装。**Spring のアノテーションを一切使わないただの Java クラス**で、`ApplicationConfiguration` というクラスに `@Bean` で「このインターフェースにはこの実装を使う」と明示的に書いて配線している
- `adapter/in/web` … Controller(HTTP を受ける側のアダプタ)
- `adapter/out/...` … port/out の実装(返答生成・サジェスト・メトリクスなど)
- `ArchitectureTest` … 「domain と application は Spring に依存してはいけない」「application は adapter に依存してはいけない」というルールを**テストとして自動チェック**している(ArchUnit というライブラリを使用)。うっかりルール違反のコードを書くと CI で落ちる。

## 検討した問い

「Spring Boot 標準の `@Service` 方式に変えたほうがシンプルでは?」

### まず整理: この2つは実は対立していない

- `@Service` を使うかどうか … Bean の**登録方法**の話(自動スキャン vs 手動配線)
- ports & adapters にするかどうか … **パッケージ構造と依存の向き**の話(中心ロジックが外側を知らない)

つまり「hexagonal 構造のまま `@Service` を付ける」という中間もありえる。実際の選択肢は3つ:

1. **現状維持**: hexagonal 構造 + application/domain 層は Spring 非依存(`@Bean` で手動配線)
2. **折衷**: 構造はそのまま、service に `@Service` を付けて `ApplicationConfiguration` を廃止
3. **全面転換**: port インターフェースをやめて Controller → `@Service` → 実装クラスの素直な構成へ

## 決定

**現状の ports & adapters 構成を維持する**(選択肢1)。

## 理由

### 1. 今ある adapter は全部「仮実装」= 差し替えが確実に起きる

`KeywordMatchReplyGenerationAdapter`(キーワードマッチ応答)、`CannedPhraseSuggestionAdapter`(定型文サジェスト)、`RandomWalkMetricsGenerationAdapter`(乱数によるダミーメトリクス)、`InMemoryFaqQueryAdapter`(メモリ上のFAQ)は、いずれ本物の LLM API や DB に置き換える前提のダミー実装。

「実装を後から差し替える」は、port という仕組みが価値を発揮する最大の場面。port(約束事)さえ守れば、新しい adapter を作って配線を1行変えるだけで差し替えが完了し、**中心ロジックと Controller には一切手を入れなくて済む**。これがこのプロジェクトの近い将来に確実に起きる。

### 2. ルールを守る仕組みがすでに動いている

ArchUnit のテストが依存関係のルール違反を自動検出してくれる状態が整備済み。壊して得るものがほとんどない。

### 3. `@Service` 方式に変えても、得られるものが小さい

`@Service` 方式の利点は「ファイルが減る」「新サービス追加時に配線の追記が不要」「見慣れた形」。しかし現状ユースケースは3つだけで、配線を書いている `ApplicationConfiguration` は30行程度。**削れる手間がごくわずか**な一方、失うものは大きい:

- application 層が Spring 非依存でなくなる(= ArchUnit のルールを1本削除することになる)
- service クラスを Spring なしの普通の単体テストで気軽にテストできる利点が薄れる

折衷案(構造はそのまま `@Service` だけ付ける)も、config クラス1つの節約のためにルールを緩めるのは割に合わない。

## 帰結(この決定で何が起きるか)

**良いこと:**

- ダミー実装を本実装(LLM API・DB 等)に差し替えるとき、adapter の追加と配線変更だけで済み、中心ロジックは無変更。

**受け入れるコスト:**

- ユースケースを1つ追加するたびに触るファイルが多い(port/in + port/out + service + config の配線 + adapter で最大5ファイル)。

**将来の見直しポイント:**

- 「実装が1つしかなく、差し替える予定もない」機能が増えてきたら、このコストが苦痛になってくる。そのときも全体を作り替えるのではなく、**その機能だけ** port/out を省いて service から実装を直接呼ぶ、といった局所的な簡略化で対応する。
