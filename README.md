# like_chatgpt

ChatGPT風のチャットUIを試作するプロジェクトです。バックエンドは`POST /api/chat`で構造化JSON応答（`{reply, components[]}`）を返すキーワードマッチのモック実装で、メッセージ内容に応じて表や棒グラフを含む応答を生成します。応答JSONのスキーマ詳細は`docs/chat-response-schema.md`を参照してください。

## 前提ツール

- Node.js
- JDK 21以上が動作するJDK

Maven CLIをローカルにインストールする必要はありません。Maven Wrapper（`mvnw` / `mvnw.cmd`）を同梱しています。

## バックエンド起動手順

```bash
cd backend
./mvnw.cmd spring-boot:run
```

（Windows/Git Bash想定です。macOS/Linuxの場合は `./mvnw spring-boot:run` を使用してください。）

起動後、`http://localhost:8080` で待受けます。

## フロントエンド起動手順

```bash
cd frontend
npm install
npm run dev
```

起動後、`http://localhost:5173` にアクセスします。

## 疎通確認方法

バックエンドとフロントエンドを両方起動した状態でブラウザから `http://localhost:5173` を開き、メッセージを送信します。例えば「今月の問い合わせ件数を担当者別にまとめて」と送信すると、担当者別の集計結果が表と棒グラフで吹き出し内に表示されます（「カテゴリ」「種別」を含む文言ではカテゴリ別の集計、それ以外の文言では使い方の案内テキストが表示されます）。想定通りの応答が表示されれば疎通確認は完了です。

## スコープ外

認証機能、会話の永続化（リロードすると会話は消えます）、実際のLLMとの連携は本プロジェクトのスコープ外です。
