# セキュリティとプライバシー

## 目的

本アプリケーションが扱う情報について、非公開にする情報と公開する不審者情報の境界、および実装・運用で守る規則を定義する。本書をセキュリティとプライバシーに関する人間向けの正本とする。

設定ファイルと認証情報の管理方法は [`../development/configuration-management.md`](../development/configuration-management.md)、各外部サービスとの接続方法は関連する連携文書を参照する。

## 情報の区分

| 区分               | 対象                                                                        | 取り扱い                                                                           |
| ------------------ | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| 認証情報           | APIキー、LINEのチャネルシークレット・アクセストークン、DBパスワード、秘密鍵 | Git、APIレスポンス、ログへ実値を出さない                                           |
| LINE利用者情報     | LINEユーザーID                                                              | DBには保存するが、公開APIとログへ出さない                                          |
| Webhook受信情報    | 未加工のリクエスト本文、`X-Line-Signature`                                  | 処理中だけ使用し、ログへ出さない                                                   |
| 公開する不審者情報 | 報告ID、タグ、発生日時、住所、座標、概要、登録日時                          | DBへ保存し、REST APIと画面で公開する。調査に必要な場合はログや共有資料で扱ってよい |

住所、座標、発生日時、タグ、概要などの不審者情報は、このシステムが地図、一覧、詳細欄で公開する情報である。本プロジェクトでは、これらを個人情報または秘密情報として扱わない。

Webhookの未加工本文には、公開対象の不審者情報だけでなくLINEユーザーIDなども含まれるため、本文全体はログへ出力しない。

## 認証情報

- 認証情報の実値をソースコード、Git管理対象ファイル、文書、テストデータへ記載しない。
- ローカルの実値はGit管理対象外ファイル、デプロイ環境の実値は環境変数またはSecret管理機能から渡す。
- Geocoding APIキーを含み得る完全なリクエストURLをログへ出力しない。
- フロントエンドの`VITE_GOOGLE_MAPS_API_KEY`はブラウザへ配信されるため、Google Cloud側でHTTPリファラーと利用可能なAPIを制限する。

設定元、Git管理区分、環境変数の詳細は [`../development/configuration-management.md`](../development/configuration-management.md) を参照する。

## LINE Webhook

- 未加工のリクエスト本文と`X-Line-Signature`を使用し、JSON解析や保存より前に署名を検証する。
- 署名がない、形式が不正、または署名が一致しないリクエストは処理しない。
- Webhookのリクエスト本文、署名、LINEユーザーIDをログへ出力しない。
- クライアント向けエラーレスポンスに内部例外や認証情報を含めない。

現在の署名検証と処理経路は [`../integrations/line-messaging-api.md`](../integrations/line-messaging-api.md) を参照する。

## REST APIの公開範囲

`GET /api/reports`は、公開対象の不審者情報を`ReportDto`として返す。LINEユーザーIDはDTOに含めず、APIへ公開しない。

報告取得APIは公開情報を提供するため、アプリケーション独自の認証・認可を設けていない。EntityやDTOを変更するときは、新しい項目が公開対象かを確認し、LINEユーザーIDや認証情報をレスポンスへ追加しない。

## CORS

CORSは、ブラウザからバックエンドAPIを呼び出せるWebサイトのOriginを制限する仕組みである。認証やファイアウォールではなく、ブラウザ以外からの通信を防ぐものではない。

現在は`WebConfig`が`/api/**`に対して環境別の`app.cors.allowed-origins`からの`GET`、`POST`、`PUT`、`DELETE`を許可し、Cookieなどの資格情報は許可していない。LINE Webhookはサーバー間通信であるため、このCORS設定の対象外である。

- ローカルでは、標準でフロントエンドの`http://localhost:5173`を許可する。
- 本番では、`APP_CORS_ALLOWED_ORIGINS`に実際のフロントエンドOriginを設定する。
- 許可Originを理由なく増やさず、ワイルドカードと資格情報を併用しない。

## ログとエラー

ログへ出力しない情報は次のとおりである。

- 認証情報の実値
- Webhookの未加工本文と署名
- LINEユーザーID
- APIキーを含み得る完全なGeocodingリクエストURL

住所、座標、発生日時、タグ、概要とGeocodingのレスポンスは公開対象の不審者情報を調査する目的でログへ出力してよい。クライアント向けエラーには内部例外をそのまま返さず、サーバー側で処理段階を判別できる情報を記録する。

## 変更時の確認事項

- Webhookの署名検証がJSON解析や保存より前に実行されるか。
- 新しい認証情報の実値がGit管理対象やログに含まれないか。
- Entity、DTO、APIの変更でLINEユーザーIDなどの非公開情報が公開されないか。
- CORSの許可Originを必要以上に広げていないか。
- ログとクライアント向けエラーに認証情報や内部例外が含まれないか。

## 関連文書

- [`../architecture/system-overview.md`](../architecture/system-overview.md)
- [`../development/configuration-management.md`](../development/configuration-management.md)
- [`../integrations/line-messaging-api.md`](../integrations/line-messaging-api.md)
- [`../integrations/google-maps.md`](../integrations/google-maps.md)
- [`../operations/troubleshooting.md`](../operations/troubleshooting.md)
