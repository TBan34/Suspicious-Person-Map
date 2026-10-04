# データベース環境と運用

## 目的

ローカル開発、自動テスト、PostgreSQL互換性確認、本番用設定の現状を整理し、起動方法、データの保存場所、Dockerコンテナ、認証情報の管理方法を一つの文書で確認できるようにする。

READMEには初回起動に必要な最短手順だけを記載し、データベースに関する詳細は本書を正本とする。設定ファイルのGit管理区分は [`configuration-management.md`](configuration-management.md) を参照する。

## PostgreSQLを選定した理由

開発・検証時にライセンス費用なく利用でき、Spring Data JPAとの組み合わせが一般的で、住所、日時、タグなどの構造化データを標準的なRDBとして扱いやすいことからPostgreSQLを採用した。また、特定の公開基盤に依存せず利用できる選択肢が多く、ローカル検証から公開環境へ移行する際にアプリケーションのデータ設計を大きく変えずに済む点も考慮した。現時点でPostgreSQL固有機能を必須としているわけではなく、開発コスト、扱いやすさ、将来の運用先の選びやすさを総合して選択している。

## 環境ごとの使い分け

| Profile | データベース | 用途 | Docker |
| --- | --- | --- | --- |
| `local-h2` | ファイル型H2 | 日常的なLINE送信から画面反映までの確認 | 不要 |
| `local-postgres` | ローカルPostgreSQL 15 | PostgreSQLとの互換性確認 | 必要 |
| `test` | インメモリH2 | Mavenによる自動テスト | 不要 |
| `prod` | 未決定 | 未使用の本番用設定 | ― |

通常は `local-h2` を使用する。Entity、Repository、カラム、制約、SQLなどデータベースに関係する実装を変更した場合と、リリース前の結合確認では `local-postgres` も使用する。H2のPostgreSQL互換モードは完全互換ではないため、PostgreSQL固有の動作はH2だけで保証しない。

## ローカル共通設定

`local-h2` と `local-postgres` は、Git管理対象外の `backend/config/application-local-secrets.properties` を読み込む。既存環境では旧 `application-dev.properties` の実値をこのファイルへ移行済みである。

```properties
local.google.geocoding-api-key=YOUR_GOOGLE_GEOCODING_API_KEY
local.line.channel-secret=YOUR_LINE_CHANNEL_SECRET
local.cors.allowed-origins=http://localhost:5173

local.postgres.datasource.url=jdbc:postgresql://localhost:5432/personal_develop_db
local.postgres.datasource.username=postgres
local.postgres.datasource.password=YOUR_DATABASE_PASSWORD
```

パスワード、APIキー、チャネルシークレットの実値は、README、本書、ソースコード、コミット対象ファイル、メモの共有先へ記載しない。PostgreSQLのパスワードは、コンテナ作成時に設定した値と `local.postgres.datasource.password` を一致させる。

同じ項目を環境変数で渡した場合は、環境変数を優先する。対応関係は次のとおりである。

| 用途 | ローカル設定キー | 環境変数 |
| --- | --- | --- |
| PostgreSQL URL | `local.postgres.datasource.url` | `LOCAL_DB_URL` |
| PostgreSQLユーザー | `local.postgres.datasource.username` | `LOCAL_DB_USERNAME` |
| PostgreSQLパスワード | `local.postgres.datasource.password` | `LOCAL_DB_PASSWORD` |
| Geocoding APIキー | `local.google.geocoding-api-key` | `GOOGLE_GEOCODING_API_KEY` |
| LINEチャネルシークレット | `local.line.channel-secret` | `LINE_CHANNEL_SECRET` |
| CORS許可元 | `local.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` |

## local-h2

### 起動

```bash
cd backend
SPRING_PROFILES_ACTIVE=local-h2 ./mvnw spring-boot:run
```

Spring Bootプロセス内でH2が動作するため、Docker DesktopとPostgreSQLコンテナは不要である。LINEからのWebhookを確認する場合は、Spring Boot起動後に別のターミナルでngrokを起動する。

```bash
ngrok http 8080
```

### データの保存場所

H2はインメモリではなくファイル型で使用し、次のファイルへデータを保存する。

```text
backend/.local-data/suspicious-person-map.mv.db
```

`.local-data/` は `backend/.gitignore` の対象である。バックエンドを再起動してもデータは残る。初期化する場合はバックエンドを停止し、必要なデータがないことを確認してから対象のH2ファイルだけを削除する。

## local-postgres

### 現在のDockerコンテナ

2026年10月4日時点で、ローカル環境のPostgreSQLコンテナは次の構成である。

| 項目 | 現在値 | Git管理文書へ記載するか |
| --- | --- | --- |
| コンテナ名 | `local-postgres` | 記載する |
| Dockerイメージ | `postgres:15` | 記載する |
| ホストポート | `5432` | 記載する |
| コンテナポート | `5432` | 記載する |
| データベース名 | `personal_develop_db` | 記載する |
| ユーザー名 | `postgres` | 記載する |
| パスワード | `local.postgres.datasource.password` の実値 | 実値は記載しない |
| データ保存方式 | bind mount | 記載する |
| ホスト側保存先 | `/Users/tb/postgres-data` | 現在のローカル構成として記載する |
| コンテナ側保存先 | `/var/lib/postgresql/data` | 記載する |
| Docker再起動ポリシー | `no` | 記載する |

コンテナの定義は現在Docker Composeでコード化されていない。ホスト側保存先はこの端末固有であり、別の端末へそのまま適用しない。

### 状態確認と起動

Docker Desktopを起動してから、コンテナの状態を確認する。

```bash
docker ps -a --filter name=local-postgres
```

停止している場合は起動する。

```bash
docker start local-postgres
```

PostgreSQLが接続を受け付けられるか確認する。

```bash
docker exec local-postgres pg_isready -U postgres -d personal_develop_db
```

### バックエンド起動

```bash
cd backend
SPRING_PROFILES_ACTIVE=local-postgres ./mvnw spring-boot:run
```

接続できない場合は、コンテナの状態、ポート `5432` の競合、DB名、ユーザー名、パスワードの一致を確認する。パスワードを確認するために `docker inspect` の環境変数全体を共有資料やログへ出力しない。

### 停止

```bash
docker stop local-postgres
```

bind mount先にデータが残るため、通常のコンテナ停止ではDBデータは削除されない。コンテナや `/Users/tb/postgres-data` を削除する操作はデータ消失につながるため、実行前にバックアップ要否を確認する。

## test

自動テストは `test` ProfileとインメモリH2を使用する。テストごとに本番やローカルの認証情報へ依存せず、テスト終了後にDBは破棄される。

```bash
cd backend
./mvnw test
```

## prod

`prod` Profileは本番用の設定ファイルとして存在するが、現時点では本番環境へ接続していない。接続先のデータベース、クラウド事業者、ネットワーク構成、運用方法はいずれも未決定である。

現在確定しているのは、接続情報と外部APIの認証情報を環境変数から受け取り、`spring.jpa.hibernate.ddl-auto=validate` で既存スキーマとの一致を確認する設定だけである。採用するサービスや構成は、明示的に決定するまで本書の前提にしない。

## 現在のスキーマ管理

| Profile | `spring.jpa.hibernate.ddl-auto` | 意味 |
| --- | --- | --- |
| `local-h2` | `update` | ローカルH2を既存データを残して更新する |
| `local-postgres` | `update` | ローカルPostgreSQLを既存データを残して更新する |
| `test` | `create-drop` | テスト開始時に作成し、終了時に破棄する |
| `prod` | `validate` | Entityと既存スキーマの一致だけを確認する |

現在はFlywayやLiquibaseを導入していない。本番公開前には、再現可能な本番スキーマ作成・更新手段を導入する必要がある。

## 検証の目安

| 変更内容 | `local-h2` | `local-postgres` |
| --- | --- | --- |
| LINEメッセージ解析、画面反映 | 必須 | 通常は不要 |
| Geocoding処理 | 必須 | 通常は不要 |
| Entity、Repository、カラム、制約 | 必須 | 必須 |
| SQL、インデックス、PostgreSQL固有機能 | 参考 | 必須 |
| リリース前の結合確認 | 必須 | 必須 |

H2で成功してもPostgreSQLでの動作を完全には保証しない。一方、通常のLINE送信から画面反映までの確認では、Dockerを起動せず `local-h2` を使用してよい。
