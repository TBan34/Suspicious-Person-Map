# トラブルシューティング

## 目的

ローカル開発や外部サービスとの連携で問題が発生したときに、原因を切り分けるための確認順序と対処方法をまとめる。

この文書には、開発時に確認した事象と、現在の実装から判定できるエラー状態を記載する。各サービス固有の設定や仕様は関連する連携文書を参照する。

## 基本的な確認順序

エラーメッセージだけで原因を決めず、リクエストの送信元から依存先まで順に状態を確認する。

1. リクエスト先の URL、パス、ポートが正しいか確認する。
2. リクエストを受け取るアプリケーションが起動しているか確認する。
3. データベースなど、アプリケーションの依存サービスが起動しているか確認する。
4. 各サービスのログから、リクエストがどこまで到達したか確認する。
5. 設定を変更した場合は、対象サービスを再起動して再度確認する。

認証情報、Webhook のリクエスト本文、署名、LINE ユーザー ID はログや共有資料へ出力しない。完全な住所は、開発中の Geocoding 調査用 `DEBUG` と例外発生時の `ERROR` に限定し、共有資料へは転載しない。公開前に住所の `DEBUG` 出力は削除する。

## LINE Webhook

`LineController` は JSON 解析と保存処理の前に LINE Webhook の署名を検証する。署名がない、または一致しないリクエストは `400 Bad Request` で拒否する。

### 構成

ローカル環境では、LINE Platform から ngrok を経由して Spring Boot が Webhook を受信する。React の開発サーバーは Webhook の受信先ではない。

```text
LINE Platform
    ↓ HTTPS
ngrok
    ↓ localhost:8080
Spring Boot
    ↓
H2 または PostgreSQL
```

通常のローカル確認では `local-h2` Profileを使用するため、Spring Bootより前にデータベースを起動する必要はない。

```text
Spring Boot（local-h2）→ ngrok
```

`local-postgres` Profileを使う場合だけ、PostgreSQLコンテナをSpring Bootより先に起動する。

### 症状別の確認方法

| 症状                                              | 想定される原因                                            | 確認方法                                                                             | 対処                                                                       |
| ------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| LINE Developers の検証が `404 Not Found` になる   | Webhook のパスが Controller と一致していない              | ngrok のリクエスト履歴と Spring Boot のマッピングを確認する                          | Webhook URL を `https://YOUR_NGROK_HOST/line/callback` に修正する          |
| LINE Developers の検証が `502 Bad Gateway` になる | ngrok の転送先で Spring Boot が応答していない             | Spring Boot が `8080` 番ポートで起動しているか、起動時のログにエラーがないか確認する | Spring Boot が正常に起動してから ngrok を起動する                           |
| LINE Developers の検証が `400 Bad Request` になる | チャネルシークレットが対象チャネルと異なる、再発行後の値を反映していない、または本文が到達前に変更された | LINE Developers の対象チャネル、ローカル設定、プロキシの転送設定を確認する | 現在のチャネルシークレットを設定して Spring Boot を再起動し、本文を変更する処理を外す |
| Webhook がアプリケーションへ届かない              | ngrok が React の `5173` 番ポートへ転送している           | `ngrok http` の対象ポートを確認する                                                  | Webhook を受信する Spring Boot の `8080` 番ポートを公開する                |
| Spring Boot が起動しない                          | Profile未指定、ローカル実値の不足、または選択したDBへ接続できない | `SPRING_PROFILES_ACTIVE`、`config/application-local-secrets.properties`、起動ログを確認する | 通常は `local-h2` を指定し、PostgreSQL確認時だけコンテナを起動して `local-postgres` を指定する |

### 切り分けのポイント

- `404` は転送先へ到達していても、URL のパスに対応する受信口がない場合に発生する。
- `502` は ngrok 自体の問題とは限らず、転送先の Spring Boot が停止または起動失敗している場合にも発生する。
- `400` は Webhook が Controller へ到達していても、署名検証に失敗した場合に発生する。署名やチャネルシークレットの実値はログや共有資料へ出力せずに設定元を確認する。
- `5173` は React、`8080` は Spring Boot の標準的なローカルポートとして、このプロジェクトでは役割を分けている。
- LINE Developers の検証、ngrok のリクエスト履歴、Spring Boot のログ、選択したデータベースの状態を送信経路に沿って確認する。

LINE Webhook の設定と現在の実装は [`../integrations/line-messaging-api.md`](../integrations/line-messaging-api.md) を参照する。

Profileの使い分け、H2の保存先、PostgreSQLコンテナの状態確認は [`../development/database.md`](../development/database.md) を参照する。

## Google Geocoding API

### 症状別の確認方法

| 症状                                                        | 想定される原因                                                    | 確認方法                                                                                           | 対処                                                                                               |
| ----------------------------------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `ZERO_RESULTS` になる                                       | 入力した住所を API が特定できない、または住所を過剰に変換している | 開発環境の `DEBUG` ログで入力住所とフォールバック候補を比較し、認証情報を含む URI は出力しない | 日本語として自然な住所表現を優先し、必須住所部分を残して番地以降だけを段階的に短縮する             |
| 意図しない地点が返る                                        | 一部だけが一致している、または道路として解釈されている            | `partial_match`、`types`、`formatted_address`、`geometry.location_type` を確認する                 | 現在の実装では `partial_match=true` と `types` に `route` を含む候補を除外する                     |
| URI の生成時に `Invalid character` になる                   | 生の日本語住所をエンコード済みとして扱っている                    | `UriComponentsBuilder` の `build` と `encode` の呼び出し順を確認する                               | 生の住所を `queryParam` に渡し、`build().encode(StandardCharsets.UTF_8).toUri()` で URI を生成する |
| `REQUEST_DENIED` など `ZERO_RESULTS` 以外のステータスになる | API キー、API の有効化、割り当て、リクエスト仕様に問題がある      | Google Cloud の API 設定と、秘密値を除いたステータスを確認する                                     | キーの API 制限とアプリケーション制限、Geocoding API の有効化状態を見直す                          |

### 切り分けのポイント

- `ZERO_RESULTS` は JSON の解析失敗ではなく、Geocoding API が住所を特定できなかった状態である。
- `status=OK` かつ結果が存在しても、アプリケーションの用途に適した地点とは限らない。
- 完全なリクエスト URI には API キーが含まれるためログへ出力しない。レスポンスと住所の開発用 `DEBUG` 出力は公開前に削除し、住所の `ERROR` 出力は Geocoding の例外発生時に限定する。
- 現在の実装は `ZERO_RESULTS` とその他の API エラーを同じフォールバック対象として扱うため、ステータス別の処理は今後の改善対象である。

Geocoding の設計、実装済みの候補選択、精度上の制約は [`../integrations/google-maps.md`](../integrations/google-maps.md) を参照する。

## 関連文書

- [`../integrations/line-messaging-api.md`](../integrations/line-messaging-api.md)
- [`../integrations/google-maps.md`](../integrations/google-maps.md)
- [`../../README.md`](../../README.md)
