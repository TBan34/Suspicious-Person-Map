# 設定ファイル管理方針

## 目的

Spring Boot と Vite の設定を変更するときに、秘密情報の誤コミット、重複した設定元、不要ファイルや ignore 規則の残存を防ぐ。本書は設定ファイル構成と変更手順の正本とする。

## 基本原則

- 設定ファイルを作成する前に、利用者、読み込み時期、対象環境、秘密情報の有無、Git 管理区分を決める。
- Git 管理する設定構造と、認証情報の実値を持つローカル設定を同じファイルで兼用しない。
- 同じ環境・同じ設定項目について、理由なく複数の設定元を作らない。
- 認証情報の実値を入力する前に、対象ファイルが正しい `.gitignore` で除外されていることを確認する。
- ignore 規則は対象を所有する最も近いサブプロジェクトへ置く。
- 構成方針を変更した場合は最終的なファイル一覧を作り直し、不要になったファイル、空ディレクトリ、ignore 規則、文書を同じ変更内で削除する。
- 共通設定が存在しない場合、構成を揃える目的だけで空の共通設定ファイルを作成しない。

## 現在の構成

### バックエンド

| ファイル | 用途 | Git 管理 | 実値 |
| --- | --- | --- | --- |
| `backend/src/main/resources/application-local-h2.properties` | Docker を使わないローカル動作確認用 | 対象 | 実値は記載せず、共通シークレットまたは環境変数を参照する |
| `backend/src/main/resources/application-local-postgres.properties` | ローカル PostgreSQL 互換性確認用 | 対象 | 実値は記載せず、共通シークレットまたは環境変数を参照する |
| `backend/config/application-local-secrets.properties` | ローカル Profile 共通の実値 | 対象外 | DB 認証情報、Google API キー、LINE チャネルシークレットを記載する |
| `backend/.local-data/` | ファイル型 H2 のDBファイル | 対象外 | ローカル検証で登録したデータを保持する |
| `backend/src/main/resources/application-prod.properties` | 未使用の本番用設定 | 対象 | 実値は記載せず、デプロイ環境の環境変数を参照する |
| `backend/src/test/resources/application-test.properties` | 自動テスト用 | 対象 | 明らかなテスト用ダミー値だけを記載する |

全環境共通の設定がないため、無印の `application.properties` は置かない。共通設定が発生した場合だけ追加し、認証情報の実値は記載しない。

`application-local-secrets.properties` と `.local-data/` の ignore 規則は、バックエンドが所有する `backend/.gitignore` に置く。新しい環境では既存のローカル実値ファイルをコピーして配置し、Git 管理する Profile 本体には実値を書かない。

`local-h2` はファイル型 H2 を使用し、日常的な LINE Webhook から画面反映までの確認に用いる。`local-postgres` はローカル PostgreSQL を使用し、DB互換性を確認するときだけ用いる。両Profileは `application-local-secrets.properties` を任意インポートし、同じ設定項目の環境変数がある場合は環境変数を優先する。

| Profile | DB | 主な用途 |
| --- | --- | --- |
| `local-h2` | ファイル型 H2 | 通常のローカル動作確認 |
| `local-postgres` | PostgreSQL | PostgreSQL 互換性のローカル確認 |
| `test` | インメモリ H2 | Maven 自動テスト |
| `prod` | 未決定 | 未使用の本番用設定 |

`WebConfig` は `app.cors.allowed-origins` を参照する。自動テストは `test` Profile を有効化し、ローカルや本番の認証情報に依存しない。Profile 未指定時の暗黙的な接続先は設けず、用途に対応するProfileを明示する。

各Profileの起動方法、H2の保存先、Dockerコンテナの現在値、本番用設定の現状は [`database.md`](database.md) を正本とする。

### フロントエンド

| ファイルまたは設定元 | 用途 | Git 管理 | 実値 |
| --- | --- | --- | --- |
| `frontend/.env.development` | ローカル開発用 | 対象外 | 開発用 API URL と制限済み Maps JavaScript API キーを記載する |
| CI・ホスティング環境の環境変数 | 本番ビルド用 | リポジトリ外 | `VITE_API_BASE_URL` と `VITE_GOOGLE_MAPS_API_KEY` をビルド時に渡す |

`.env.development` の ignore 規則は、フロントエンドが所有する `frontend/.gitignore` に置く。使用しない `.env` や `.env.production` を予防目的だけで作成しない。

`frontend/src/services/api.js` は `VITE_API_BASE_URL` を参照する。値は Vite のビルド時にJavaScriptへ組み込まれるため、環境ごとにビルド時の設定を用意する。

## 設定変更前の手順

設定ファイルを作成または変更する前に、次の表を作業メモとして埋める。表自体を必ずコミットする必要はないが、判断できない欄を残したまま編集を開始しない。

| 確認項目 | 例 |
| --- | --- |
| 利用者 | Spring Boot、Vite、CI、ブラウザ |
| 読み込み時期 | Spring Boot 起動時、Vite ビルド時、ブラウザ実行時 |
| 対象環境 | local-h2、local-postgres、test、prod |
| 設定内容 | DB URL、CORS Origin、API キー |
| 秘密情報 | あり、なし、ブラウザへ公開される値 |
| Git 管理 | 対象、対象外 |
| ignore の所有者 | ルート、`backend`、`frontend` |
| 既存の設定元 | properties、環境変数、JavaScript の固定値 |

複数の構成案がある場合は、ファイルを作成する前に採用案を一つに決める。途中で案が変わった場合は、新しい案の最終ファイル一覧を作り直してから編集を続ける。

## 実装ルール

### 設定元を一つにする

同じ環境・同じ設定項目について、理由なく複数の設定元を作らない。ローカルの実値は `application-local-secrets.properties` に集約し、`local-h2` と `local-postgres` のProfile本体へ複製しない。環境変数による上書きは、CIや一時的な起動設定など設定ファイルを使わない場合に限定する。

### 秘密情報を書き込む前にignoreを設定する

認証情報の実値を入力するファイルは、実値を入力する前に正しい `.gitignore` で除外する。次を実行し、除外元も確認する。

```bash
git check-ignore -v 対象ファイル
```

対象ファイルがすでにGit管理されている場合、`.gitignore` を追加するだけでは管理対象外にならない。その場合は実値を入力せず、現在の追跡状態を報告して対応方針を確認する。

### ignore規則は所有するサブプロジェクトへ置く

- リポジトリ全体に適用する規則: ルートの `.gitignore`
- Spring Boot・Maven固有の規則: `backend/.gitignore`
- Vite・npm固有の規則: `frontend/.gitignore`

同じ規則を複数階層へ重複記載しない。

### 方針変更時は置き換え元を削除する

ファイルの追加や改名だけで完了としない。次を同じ変更内で確認する。

- 旧ファイル
- 旧ディレクトリと空ディレクトリ
- 不要になった `.gitignore` の規則
- 古い設定名やパスを参照するREADME・運用文書・TODO
- コードに残る固定値や未使用の設定項目

設定項目を先に用意し、コード変更を後続作業とする場合は、未使用であることと利用開始条件を明記する。

## 完了時チェック

設定変更の完了前に、少なくとも次を確認する。

```bash
git status --short --untracked-files=all
git diff --check
git check-ignore -v Git管理外にする各ファイル
```

加えて、次を目視確認する。

- 認証情報の実値がGit管理対象ファイルや差分に含まれていない。
- 各設定項目について、環境ごとの設定元が一意に説明できる。
- Profile未指定時を含む起動条件が意図どおりである。
- 不要なファイル、空ディレクトリ、重複したignore規則が残っていない。
- 文書に記載したファイル名、環境変数名、起動コマンドが実装と一致する。
- 実行できなかったテストや未接続の設定項目を完了報告に含めている。
