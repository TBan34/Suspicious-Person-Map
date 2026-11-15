# バックエンドのログ運用

## 出力先と対象環境

設定の正本は [`../../backend/src/main/resources/logback-spring.xml`](../../backend/src/main/resources/logback-spring.xml) である。Spring Boot起動時に読み込み、標準出力へのログを維持したまま、INFO・WARN・ERRORをそれぞれ別ファイルへUTF-8で保存する。

`local-h2`・`local-postgres`・`prod` を含む、`test` Profileが有効でない起動でファイル出力する。`test` が有効な場合は標準出力だけを使用し、通常運用のログファイルの作成、ローテーション、過去ログの削除は行わない。

既定の保存先は、プロセスの起動ディレクトリを基準とする `./logs/` である。セットアップ手順どおり `backend/` から起動すると、`backend/logs/` に保存される。保存先ディレクトリはLogbackが作成するため、実行ユーザーに作成・書き込み権限が必要である。

| レベル | 現行ファイル | 保存内容 | 過去ログの保持期間 | 過去ログの容量上限 |
| --- | --- | --- | --- | --- |
| INFO | `info.log` | INFOのみ | 30日 | 1GB |
| WARN | `warn.log` | WARNのみ | 30日 | 1GB |
| ERROR | `error.log` | ERRORのみ | 90日 | 1GB |

`LevelFilter` によりレベルを完全一致で振り分けるため、ERRORが `info.log` や `warn.log` に重複して入ることはない。例外のスタックトレースがある場合は、その例外を出力したレベルのファイルに記録される。

標準のログレベルはINFOである。調査のためにDEBUGを有効にした場合も、DEBUG・TRACEはこれら3ファイルへ保存せず、標準出力で確認する。フロントエンドや外部サービスのログは、この設定の対象ではない。

## ローテーションと圧縮

各レベルで、日付の切り替わり、または現行ファイルが10MBに到達した際にファイルを切り替え、過去ログをgzip圧縮する。保存先は現行ファイルと同じディレクトリとし、`archive/` は作らない。

```text
logs/
  info.log
  warn.log
  error.log
  info.2026-11-14.0.log.gz
  info.2026-11-14.1.log.gz
  warn.2026-11-14.0.log.gz
  error.2026-11-14.0.log.gz
```

過去ログ名は `{レベル}.{yyyy-MM-dd}.{連番}.log.gz` とする。日付はJVMのタイムゾーンに従い、連番は各レベル・各日で0から始まる。10MB到達による切り替えでは当日分も圧縮される。

日付による切り替えは、そのレベルのログが翌日になって最初に到着した時点で行われる。午前0時ちょうどに切り替えるタイマー処理ではない。サイズもログ到着時に判定するため、大きな1件のログなどにより現行ファイルが10MBを超えることがある。

## 保持と削除

INFO・WARNは30日、ERRORは90日を上限に過去ログを保持する。ただし、各レベルの圧縮済み過去ログが合計1GBを超えると、保持期間内でも古いファイルから削除する。指定日数の保存を保証する容量予約ではない。

過去ログの削除は、起動時とローテーション時に非同期で行う。プロセス停止中や新しいログが到着しない間に、日数だけを契機として削除する処理はない。

容量上限は各レベルの過去ログが対象であり、書き込み中の現行ファイルを含まない。過去ログ3種類の設定上の合計は3GBだが、圧縮・削除の処理中の一時ファイルや現行ファイルもあるため、ディスク全体の厳密な3GB上限ではない。運用開始後は各レベルの1日あたりの圧縮後サイズを確認し、保持日数に必要な容量を見直す。

## 保存先の変更

`logging.file.path` をSpringの設定として読み込む。環境変数を使う場合は `LOGGING_FILE_PATH` で指定できる。起動時に読み込まれるため、変更後は再起動する。認証情報を含む設定ではない。

```bash
cd backend
LOGGING_FILE_PATH=/tmp/suspicious-person-map-logs SPRING_PROFILES_ACTIVE=local-h2 ./mvnw spring-boot:run
```

この例の `/tmp/` は一時的な動作確認用である。本番の保存先は、デプロイ先の書き込み権限と永続化方式に合わせて指定する。コンテナを使う場合は再作成後も残る領域を割り当て、複数プロセスで起動する場合は保存先をプロセスごとに分ける。本番の具体的な運用基盤・保存先は、この設定追加によって決定するものではない。

独自のLogback設定で各ファイル名を定義しているため、`logging.file.name` による単一ファイルへの出力先変更は使用しない。

## 確認方法とGit管理

既定の保存先で起動した後、リポジトリルートから確認する。

```bash
tail -n 100 backend/logs/error.log
tail -n 100 backend/logs/warn.log
tail -n 100 backend/logs/info.log
```

過去ログはgzipを展開して確認できる。日付と連番は実際に存在するファイルに置き換える。

```bash
gzip -cd backend/logs/error.2026-11-14.0.log.gz
```

`backend/logs/` は [`../../backend/.gitignore`](../../backend/.gitignore) で管理対象外とする。保存先を変更する場合も、生成したログをステージング・コミットしない。設定ファイルの管理区分は [`../development/configuration-management.md`](../development/configuration-management.md) を参照する。

ファイル保存の有無によって出力可能な情報は変わらない。認証情報、Webhook本文・署名、LINEユーザーIDなどを出力しない規約は [`../security/security-and-privacy.md`](../security/security-and-privacy.md#ログとエラー) を正本とする。
