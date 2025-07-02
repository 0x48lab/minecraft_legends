# Minecraft Legends

バトルロイヤルゲームをMinecraft上で再現するPaperプラグインです。

## 🎮 ゲーム概要

- **プレイヤー数**: 最大24名（3人×8チーム）
- **ゲーム時間**: 約20分
- **マップサイズ**: 3000×3000ブロック
- **ゲームモード**: バトルロイヤル（リング収縮あり）

## ✨ 主な機能

### 🏆 コアゲームシステム
- **バトルロイヤル**: リング収縮システム
- **チーム戦**: 3人1チームでの戦略的バトル
- **レジェンドシステム**: 特殊アビリティを持つキャラクター選択
- **リスポーンシステム**: チームメイトの復活が可能

### 👥 チーム機能
- **永続チーム**: ゲーム終了後もチームが継続
- **自動編成**: ソロ参加者の自動チーム編成
- **チーム管理**: 招待、脱退、解散などの管理機能

### 📊 統計・ランキング
- **詳細統計**: キル、デス、勝利、ダメージ等を記録
- **称号システム**: 実績に応じた称号の獲得
- **世界ランキング**: 外部API連携による世界ランキング（予定）

### 🌍 国際化対応
- **多言語サポート**: 日本語、英語対応（拡張可能）
- **設定可能**: 言語切り替えが容易

## 🛠️ 技術仕様

- **プラットフォーム**: Paper (最新版)
- **言語**: Kotlin
- **アーキテクチャ**: Clean Architecture
- **データベース**: SQLite
- **必須プラグイン**: WorldBorder

## 📋 必要環境

- **Minecraft**: Java Edition
- **サーバー**: Paper 1.20.x以上
- **Java**: 17以上
- **メモリ**: 最低4GB推奨
- **プラグイン**: WorldBorder

## 🚀 インストール

### 1. 前提条件の確認
```bash
# Javaバージョン確認
java -version

# Paperサーバーの確認
# server.jarがPaper版であることを確認
```

### 2. プラグインのビルド
```bash
# プロジェクトをクローン
git clone <repository-url>
cd minecraft_legends

# ビルド実行
./gradlew build

# 生成されたJARファイルを確認
ls build/libs/
```

### 3. サーバーへの配置
```bash
# プラグインフォルダにコピー
cp build/libs/minecraft_legends-*.jar /path/to/server/plugins/

# WorldBorderプラグインも必要
# https://www.spigotmc.org/resources/worldborder.60905/
```

### 4. サーバー起動
```bash
# サーバーを起動
java -Xmx4G -Xms4G -jar paper.jar nogui

# プラグインが正常に読み込まれることを確認
# [INFO] [minecraft_legends] Enabling minecraft_legends v1.0.0
```

## ⚙️ 設定

### 基本設定 (config.yml)
```yaml
# ワールド設定
world:
  size: 3000  # マップサイズ

# ゲーム設定  
game:
  max-players: 24
  team-size: 3
  min-players: 12

# リスポーンビーコン設定
respawn-beacon:
  count: 6
  respawn-time: 7
```

### 言語設定
```yaml
# config.yml
language:
  default: "ja"  # 日本語がデフォルト
  available: ["ja", "en"]
```

詳細な設定については [CLAUDE.md](./CLAUDE.md) を参照してください。

## 🎯 基本的な使い方

### プレイヤー向けコマンド
```bash
# ゲームに参加
/br join

# チーム作成
/br team create <チーム名>

# チームに招待
/br team invite <プレイヤー名>

# 統計確認
/br stats
```

### 管理者向けコマンド
```bash
# ゲーム開始
/bradmin start

# ゲーム停止
/bradmin stop

# 設定リロード
/bradmin reload

# 強制終了
/bradmin forceend
```

## 🎮 ゲームの流れ

1. **ロビー待機** - プレイヤーが参加を待つ
2. **チーム編成** - 自動編成またはプリメイドチーム
3. **レジェンド選択** - 各プレイヤーがレジェンドを選択
4. **ワールド生成** - 新しいマップが生成される
5. **ゲーム開始** - バトルロイヤル開始
6. **リング収縮** - 段階的に安全地帯が縮小
7. **勝敗決定** - 最後のチーム/プレイヤーが勝利
8. **結果表示** - 統計更新と結果発表
9. **次のゲーム** - 自動的に次のゲームが開始

## 🔧 開発環境

### 開発に必要なツール
- **IDE**: IntelliJ IDEA推奨
- **JDK**: OpenJDK 17
- **Build Tool**: Gradle 8.x
- **Git**: バージョン管理

### 開発サーバーのセットアップ
```bash
# 開発用Paperサーバーのダウンロード
mkdir dev-server
cd dev-server
wget https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/497/downloads/paper-1.20.4-497.jar

# サーバー起動
java -Xmx2G -Xms2G -jar paper-1.20.4-497.jar nogui
# 初回起動後、eula.txtでeula=trueに変更

# プラグインの配置
ln -s ../build/libs/minecraft_legends-*.jar plugins/
```

### ビルドとテスト
```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# 開発サーバーでの動作確認
./gradlew build && cp build/libs/*.jar dev-server/plugins/ && cd dev-server && java -Xmx2G -jar paper-*.jar nogui
```

## 📚 ドキュメント

- **[CLAUDE.md](./CLAUDE.md)** - 詳細な仕様書とアーキテクチャ
- **[API Documentation](./docs/api.md)** - API仕様書（予定）
- **[Contributors Guide](./docs/contributing.md)** - 貢献者向けガイド（予定）

## 🧪 テスト

### ユニットテスト
```bash
# 全てのテストを実行
./gradlew test

# 特定のテストクラスを実行
./gradlew test --tests "GameManagerTest"

# テストレポートの確認
open build/reports/tests/test/index.html
```

### 統合テスト
```bash
# 統合テスト実行
./gradlew integrationTest

# 実際のMinecraftサーバーでのテスト
# テスト用ワールドを作成してプラグインをテスト
```

## 🐛 トラブルシューティング

### よくある問題

**Q: プラグインが読み込まれない**
```
A: 以下を確認してください
1. Paperサーバーを使用しているか
2. Java 17以上を使用しているか
3. WorldBorderプラグインがインストールされているか
4. ログでエラーメッセージを確認
```

**Q: ゲームが開始されない**
```
A: 以下を確認してください
1. 最小プレイヤー数に達しているか
2. WorldBorderプラグインが正常に動作しているか
3. /bradmin status コマンドで状態を確認
```

**Q: パフォーマンスが悪い**
```
A: 以下を試してください
1. サーバーのメモリ割り当てを増やす
2. config.ymlでマップサイズを小さくする
3. 同時接続プレイヤー数を制限する
```

### ログ確認
```bash
# プラグインのログを確認
tail -f logs/latest.log | grep minecraft_legends

# エラーレベルのログのみ確認
tail -f logs/latest.log | grep -E "(ERROR|WARN)" | grep minecraft_legends
```

## 🤝 貢献

プロジェクトへの貢献を歓迎します！

### 貢献の方法
1. このリポジトリをフォーク
2. 新しいブランチを作成 (`git checkout -b feature/amazing-feature`)
3. 変更をコミット (`git commit -m 'Add amazing feature'`)
4. ブランチにプッシュ (`git push origin feature/amazing-feature`)
5. プルリクエストを作成

### 開発ガイドライン
- Clean Architectureに従った設計
- 適切なテストの作成
- コードレビューの実施
- ドキュメントの更新

## 📄 ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は [LICENSE](LICENSE) ファイルを参照してください。

## 👨‍💻 作成者

- **HackLab Team** - *初期開発* - [GitHub](https://github.com/hacklab)

## 🙏 謝辞

- **Mojang Studios** - Minecraftの素晴らしいゲーム
- **PaperMC Team** - 優れたサーバーソフトウェア
- **Respawn Entertainment** - Battle Royaleジャンルのインスピレーション
- **Community Contributors** - バグ報告と機能提案

## 📞 サポート

- **Issues**: [GitHub Issues](https://github.com/hacklab/minecraft_legends/issues)
- **Discord**: [Discord Server](https://discord.gg/hacklab) (予定)
- **Wiki**: [GitHub Wiki](https://github.com/hacklab/minecraft_legends/wiki) (予定)

---

**⚡ Quick Start**: `./gradlew build` → プラグインをサーバーに配置 → `/bradmin start`