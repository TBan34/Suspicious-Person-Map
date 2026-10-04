#!/usr/bin/env bash

# 指定した住所を Google Geocoding API へ直接送信し、API 単体のレスポンスを確認する。
# Spring Boot の住所正規化や候補選択、データベース保存は実行しない。

# コマンド失敗、未定義変数、パイプ途中の失敗を見逃さずに終了する。
set -euo pipefail

readonly GEOCODING_API_URL="https://maps.googleapis.com/maps/api/geocode/json"
# 実行時のカレントディレクトリに依存せず、リポジトリ内の開発用設定を参照する。
readonly SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIRECTORY}/.." && pwd)"
readonly DEVELOPMENT_PROPERTIES="${REPOSITORY_ROOT}/backend/src/main/resources/application-dev.properties"

# 引数の指定方法と実行例を標準エラー出力へ表示する。
print_usage() {
  printf 'Usage: %s ADDRESS\n' "$(basename -- "$0")" >&2
  printf 'Example: %s "福岡県福岡市中央区天神1丁目1-1"\n' "$(basename -- "$0")" >&2
}

# API キーは環境変数を優先し、未設定の場合だけローカル開発用設定から取得する。
# 開発用設定ファイルは Git 管理外のため、取得した値を画面へ出力しない。
read_api_key() {
  if [[ -n "${GOOGLE_GEOCODING_API_KEY:-}" ]]; then
    printf '%s' "${GOOGLE_GEOCODING_API_KEY}"
    return
  fi

  if [[ ! -r "${DEVELOPMENT_PROPERTIES}" ]]; then
    return
  fi

  # `google.api.key = VALUE` のような前後の空白を許容し、最初の設定値だけを取得する。
  awk '
    /^[[:space:]]*google\.api\.key[[:space:]]*=/ {
      sub(/^[^=]*=[[:space:]]*/, "")
      sub(/[[:space:]\r]+$/, "")
      print
      exit
    }
  ' "${DEVELOPMENT_PROPERTIES}"
}

# 住所は空白を含む可能性があるため、引用符で囲んだ1つの引数として受け取る。
if [[ "$#" -ne 1 || -z "$1" ]]; then
  print_usage
  exit 2
fi

# HTTP 通信に必要な curl が利用可能かをAPI呼び出し前に確認する。
if ! command -v curl >/dev/null 2>&1; then
  printf 'Error: curl is required.\n' >&2
  exit 1
fi

# API へ送る住所と認証情報を確定し、キーが見つからなければ通信前に終了する。
readonly ADDRESS="$1"
readonly API_KEY="$(read_api_key)"

if [[ -z "${API_KEY}" ]]; then
  printf 'Error: Set GOOGLE_GEOCODING_API_KEY or google.api.key in %s.\n' \
    "${DEVELOPMENT_PROPERTIES}" >&2
  exit 1
fi

# バックエンドの Geocoding リクエストと同じエンドポイント・検索条件を組み立てる。
# --data-urlencode により、日本語や住所中の記号を curl 側で安全にURLエンコードする。
curl_arguments=(
  --silent
  --show-error
  --fail-with-body
  --get
  "${GEOCODING_API_URL}"
  --data-urlencode "address=${ADDRESS}"
  --data-urlencode "language=ja"
  --data-urlencode "region=jp"
)

# curl の設定形式を壊さないようにAPIキーをエスケープしてからリクエストする。
call_geocoding_api() {
  local escapedApiKey="${API_KEY//\\/\\\\}"
  escapedApiKey="${escapedApiKey//\"/\\\"}"

  # API キーをプロセスのコマンドラインへ露出させないため、curl の設定を標準入力で渡す。
  printf 'data-urlencode = "key=%s"\n' "${escapedApiKey}" \
    | curl "${curl_arguments[@]}" --config -
}

# jq が利用できればJSONを整形し、なければAPIレスポンスをそのまま表示する。
if command -v jq >/dev/null 2>&1; then
  call_geocoding_api | jq .
else
  call_geocoding_api
  printf '\n'
fi
